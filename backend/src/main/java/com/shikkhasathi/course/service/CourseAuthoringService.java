package com.shikkhasathi.course.service;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.ErrorCode;
import com.shikkhasathi.common.audit.service.AuditService;
import com.shikkhasathi.common.jobs.EnqueueOptions;
import com.shikkhasathi.common.jobs.JobNames;
import com.shikkhasathi.common.jobs.JobQueue;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.course.adapter.VideoService;
import com.shikkhasathi.course.dto.CourseDtos.*;
import com.shikkhasathi.course.repository.CourseAuthoringRepository;
import com.shikkhasathi.course.repository.CourseAuthoringRepository.*;
import com.shikkhasathi.file.service.Bucket;
import com.shikkhasathi.file.service.StorageKeys;
import com.shikkhasathi.file.service.StorageService;
import com.shikkhasathi.teacher.entity.SellerProfile;
import com.shikkhasathi.teacher.repository.SellerProfileRepository;
import java.net.URI;
import java.text.Normalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Teacher Studio course wizard: drafts, basics/pricing autosave, targets, curriculum, checklist, submit, visibility, uploads. */
@Service
public class CourseAuthoringService {
  static final List<String> COVER_COLORS = List.of("#4F46E5", "#0EA5E9", "#10B981", "#F59E0B", "#EC4899", "#8B5CF6", "#14B8A6", "#F97316");
  /** Fields that, once published, go through review again (spec §6.12). */
  static final List<String> REVIEWED_FIELDS = List.of("title", "price", "compareAtPrice");
  static final List<String> PATCH_FIELDS = List.of("title", "shortDescription", "description", "learnings", "notFor", "faq", "coverUrl", "coverColor", "introVideoId",
      "language", "examType", "courseType", "price", "compareAtPrice", "accessType", "accessDays", "accessUntil");
  private static final Pattern GUARANTEE = Pattern.compile("(পরীক্ষায় আসবেই|১০০% কমন|নিশ্চিত প্রশ্ন|guaranteed question|100% common)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
  private static final String NOT_A_SELLER = "আগে শিক্ষক হিসেবে আবেদন করুন।";

  private final CourseAuthoringRepository repo;
  private final CourseReadModel read;
  private final SellerProfileRepository profiles;
  private final AuditService audit;
  private final JobQueue jobs;
  private final VideoService video;
  private final StorageService storage;
  private final JsonMapper json;
  private final TransactionTemplate contentTx;

  public CourseAuthoringService(CourseAuthoringRepository repo, CourseReadModel read, SellerProfileRepository profiles, AuditService audit, JobQueue jobs, VideoService video,
                                StorageService storage, JsonMapper json, @Qualifier("contentTransactionManager") PlatformTransactionManager contentTm) {
    this.repo = repo;
    this.read = read;
    this.profiles = profiles;
    this.audit = audit;
    this.jobs = jobs;
    this.video = video;
    this.storage = storage;
    this.json = json;
    this.contentTx = new TransactionTemplate(contentTm);
  }

  static String slugify(String s) {
    String v = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    return v.length() > 60 ? v.substring(0, 60) : v;
  }

  /** like Math.random().toString(36).slice(2, 7) */
  static String randomBase36(int n) {
    StringBuilder b = new StringBuilder();
    String digits = "0123456789abcdefghijklmnopqrstuvwxyz";
    for (int i = 0; i < n; i++) b.append(digits.charAt(ThreadLocalRandom.current().nextInt(36)));
    return b.toString();
  }

  /** a field of a loaded SellerCourse map (a revision may have replaced it with a JSON value) */
  static String str(Object v) {
    if (v instanceof JsonNode n) return n.isNull() ? null : n.asString();
    return v == null ? null : String.valueOf(v);
  }

  static long num(Object v) {
    if (v instanceof JsonNode n) return n.asLong();
    return v == null ? 0 : ((Number) v).longValue();
  }

  static int size(Object v) {
    if (v instanceof JsonNode n) return n.size();
    return v == null ? 0 : ((Collection<?>) v).size();
  }

  CourseRow ownCourse(UUID sellerId, UUID id) {
    CourseRow c = repo.liveCourse(id).orElseThrow(() -> ApiException.notFound("কোর্সটি পাওয়া যায়নি।"));
    if (!c.sellerId().equals(sellerId)) throw ApiException.forbidden();
    return c;
  }

  private Map<String, Object> reloadCourse(UUID id) {
    return read.load(repo.course(id).orElseThrow(ApiException::notFound));
  }

  // ---------------------------------------------------------------- list / create / get

  public CourseList listMyCourses(UUID sellerId) {
    List<CourseRow> rows = repo.coursesOfSeller(sellerId);
    List<TargetRow> codes = repo.targetsOf(rows.stream().map(CourseRow::id).toList());
    Map<UUID, String> codeOf = new HashMap<>();
    for (TargetRow t : codes) if (t.courseCode() != null && !t.courseCode().isEmpty()) codeOf.putIfAbsent(t.courseId(), t.courseCode());
    return new CourseList(rows.stream().map(c -> new SellerCourseListItem(c.id(), c.slug(), c.title(), c.status(), c.price(), c.lessonCount(), c.enrollmentCount(),
        c.ratingAvg().doubleValue(), c.ratingCount(), JsonConfig.iso(c.updatedAt()), c.coverColor(), codeOf.get(c.id()))).toList());
  }

  public Map<String, Object> createCourse(UUID sellerId, String title) {
    SellerProfile p = profiles.findById(sellerId).orElseThrow(() -> ApiException.forbidden(NOT_A_SELLER));
    String base = slugify(title);
    if (base.isEmpty()) base = "course";
    String slug = base + "-" + randomBase36(5);
    int n = repo.countBySeller(sellerId);
    UUID id = repo.insertCourse(sellerId, title, slug, p.getDisplayName(), "verified".equals(p.getVerificationStatus()), COVER_COLORS.get(n % COVER_COLORS.size()));
    audit.log(AuditService.Entry.of(sellerId, "course.create", "courses", id));
    return reloadCourse(id);
  }

  public Map<String, Object> getMyCourse(UUID sellerId, UUID id) {
    return read.load(ownCourse(sellerId, id));
  }

  // ---------------------------------------------------------------- basics / pricing

  private static Object currentValue(CourseRow c, String k) {
    return switch (k) {
      case "title" -> c.title();
      case "price" -> c.price();
      case "compareAtPrice" -> c.compareAtPrice();
      default -> null;
    };
  }

  private static Object patchValue(CoursePatch b, String k) {
    return switch (k) {
      case "title" -> b.title();
      case "shortDescription" -> b.shortDescription();
      case "description" -> b.description();
      case "learnings" -> b.learnings();
      case "notFor" -> b.notFor();
      case "faq" -> b.faq();
      case "coverUrl" -> b.coverUrl();
      case "coverColor" -> b.coverColor();
      case "introVideoId" -> b.introVideoId();
      case "language" -> b.language();
      case "examType" -> b.examType();
      case "courseType" -> b.courseType();
      case "price" -> b.price();
      case "compareAtPrice" -> b.compareAtPrice();
      case "accessType" -> b.accessType();
      case "accessDays" -> b.accessDays();
      case "accessUntil" -> b.accessUntil();
      default -> throw new IllegalArgumentException(k);
    };
  }

  public Map<String, Object> updateCourse(UUID sellerId, UUID id, CoursePatch b, JsonNode raw) {
    Instant accessUntil = null;
    if (b.accessUntil() != null) {
      try {
        accessUntil = OffsetDateTime.parse(b.accessUntil()).toInstant();
      } catch (RuntimeException e) {
        throw ApiException.validation("Invalid ISO datetime", List.of(Map.of("path", "body.accessUntil", "message", "Invalid ISO datetime")));
      }
    }
    CourseRow c = ownCourse(sellerId, id);
    if (b.compareAtPrice() != null && b.price() != null && b.compareAtPrice() > 0 && b.compareAtPrice() <= b.price())
      throw ApiException.badRequest("আগের দাম বর্তমান দামের চেয়ে বেশি হতে হবে।");
    boolean published = c.status().equals("published");
    Map<String, Object> set = new LinkedHashMap<>();
    ObjectNode revision = json.createObjectNode();
    for (var e : raw.properties()) {
      String k = e.getKey();
      if (!PATCH_FIELDS.contains(k)) continue;
      Object v = patchValue(b, k);
      Object val = k.equals("accessUntil") ? accessUntil : v;
      if (published && REVIEWED_FIELDS.contains(k) && !Objects.equals(currentValue(c, k), v)) revision.set(k, json.valueToTree(v));
      else set.put(k, val);
    }
    if (!set.isEmpty()) {
      if (published) set.put("needsSpotReview", true);
      if (c.status().equals("changes_requested") || c.status().equals("rejected")) set.put("status", "draft");
      set.put("contentUpdatedAt", Instant.now());
      repo.updateCourse(id, set);
    }
    if (!revision.isEmpty()) upsertRevision(id, revision);
    return reloadCourse(id);
  }

  void upsertRevision(UUID courseId, ObjectNode patch) {
    var existing = repo.pendingRevisions(courseId);
    if (!existing.isEmpty()) {
      RevisionRow r = existing.get(0);
      ObjectNode merged = r.payload() != null && r.payload().isObject() ? ((ObjectNode) r.payload()).deepCopy() : json.createObjectNode();
      merged.setAll(patch);
      repo.updateRevisionPayload(r.id(), merged);
    } else repo.insertRevision(courseId, patch);
  }

  // ---------------------------------------------------------------- targets

  private void validateTargets(List<TargetInput> ts) {
    Map<UUID, UUID> depts = repo.departmentInstitutions(ts.stream().map(TargetInput::departmentId).filter(Objects::nonNull).distinct().toList());
    for (TargetInput t : ts) {
      if (!t.isGeneral() && t.institutionId() == null) throw ApiException.badRequest("প্রতিটি টার্গেটে ইউনিভার্সিটি দিন, অথবা \"General / Cross-University\" বেছে নিন।");
      if (t.departmentId() != null) {
        UUID inst = depts.get(t.departmentId());
        if (inst == null || (t.institutionId() != null && !inst.equals(t.institutionId()))) throw ApiException.badRequest("বিভাগটি নির্বাচিত ইউনিভার্সিটির নয়।");
      }
      if (t.subjectMasterId() == null && (t.courseCode() == null || t.courseCode().isEmpty())) throw ApiException.badRequest("প্রতিটি টার্গেটে সাবজেক্ট বা কোর্স কোড দিন।");
    }
  }

  /** The targets as stored in a revision payload (parsed values; absent optional keys left out). */
  private JsonNode targetsJson(List<TargetInput> ts) {
    var arr = json.createArrayNode();
    for (TargetInput t : ts) {
      ObjectNode o = json.valueToTree(t);
      for (String k : List.of("institutionId", "departmentId", "programId", "academicLevel", "semester", "subjectMasterId", "courseCode"))
        if (o.has(k) && o.get(k).isNull()) o.remove(k);
      arr.add(o);
    }
    return arr;
  }

  public Map<String, Object> setTargets(UUID sellerId, UUID id, List<TargetInput> ts) {
    CourseRow c = ownCourse(sellerId, id);
    validateTargets(ts);
    if (c.status().equals("published")) {
      ObjectNode patch = json.createObjectNode();
      patch.set("targets", targetsJson(ts));
      upsertRevision(id, patch);
      return read.load(c);
    }
    replaceTargets(id, ts);
    return read.load(c);
  }

  void replaceTargets(UUID courseId, List<TargetInput> ts) {
    contentTx.executeWithoutResult(s -> repo.replaceTargets(courseId, ts));
  }

  List<TargetInput> targetsFromJson(JsonNode n) {
    List<TargetInput> out = new ArrayList<>();
    if (n != null && n.isArray()) for (JsonNode t : n) out.add(json.treeToValue(t, TargetInput.class));
    return out;
  }

  // ---------------------------------------------------------------- curriculum

  /** Replace the curriculum tree; existing ids are kept (progress/notes stay attached), removed ones deleted. */
  public Map<String, Object> setCurriculum(UUID sellerId, UUID id, CurriculumBody body) {
    CourseRow c = ownCourse(sellerId, id);
    var allLessons = body.sections().stream().flatMap(s -> s.chapters().stream()).flatMap(ch -> ch.lessons().stream()).toList();
    List<UUID> vidIds = allLessons.stream().map(LessonInput::videoAssetId).filter(Objects::nonNull).distinct().toList();
    List<UUID> docIds = allLessons.stream().map(LessonInput::documentId).filter(Objects::nonNull).distinct().toList();
    Map<UUID, VideoRow> vids = repo.videos(vidIds).stream().collect(Collectors.toMap(VideoRow::id, Function.identity()));
    List<DocumentRow> docs = repo.documents(docIds);
    if (vids.values().stream().anyMatch(v -> !v.ownerId().equals(sellerId)) || docs.stream().anyMatch(d -> !d.ownerId().equals(sellerId))) throw ApiException.forbidden();
    contentTx.executeWithoutResult(status -> {
      int total = 0;
      int lessonCount = 0;
      List<UUID> keepSec = new ArrayList<>();
      List<UUID> keepCh = new ArrayList<>();
      List<UUID> keepL = new ArrayList<>();
      for (int si = 0; si < body.sections().size(); si++) {
        SectionInput s = body.sections().get(si);
        UUID secId;
        if (s.id() != null) {
          if (!repo.updateSection(s.id(), id, s.title(), si)) throw ApiException.badRequest("অজানা সেকশন");
          secId = s.id();
        } else secId = repo.insertSection(id, s.title(), si);
        keepSec.add(secId);
        for (int ci = 0; ci < s.chapters().size(); ci++) {
          ChapterInput ch = s.chapters().get(ci);
          UUID chId;
          if (ch.id() != null) {
            if (!repo.updateChapter(ch.id(), id, secId, ch.title(), ci)) throw ApiException.badRequest("অজানা চ্যাপ্টার");
            chId = ch.id();
          } else chId = repo.insertChapter(id, secId, ch.title(), ci);
          keepCh.add(chId);
          for (int li = 0; li < ch.lessons().size(); li++) {
            LessonInput l = ch.lessons().get(li);
            VideoRow vid = l.videoAssetId() == null ? null : vids.get(l.videoAssetId());
            int duration = l.durationSec() != 0 ? l.durationSec() : vid != null && vid.durationSec() != 0 ? vid.durationSec() : 0;
            var values = new LessonValues(l.title(), l.type(), li, duration, l.videoAssetId(), l.documentId(), l.body(), l.isPreview(), l.isImportant(), chId);
            UUID lId;
            if (l.id() != null) {
              if (!repo.updateLesson(l.id(), id, values)) throw ApiException.badRequest("অজানা লেসন");
              lId = l.id();
            } else lId = repo.insertLesson(id, values);
            keepL.add(lId);
            total += duration;
            lessonCount++;
          }
        }
      }
      repo.deleteExcept("lessons", id, keepL);
      repo.deleteExcept("course_chapters", id, keepCh);
      repo.deleteExcept("course_sections", id, keepSec);
      Map<String, Object> set = new LinkedHashMap<>();
      set.put("totalDurationSec", total);
      set.put("lessonCount", lessonCount);
      set.put("contentUpdatedAt", Instant.now());
      if (c.status().equals("published")) set.put("needsSpotReview", true);
      repo.updateCourse(id, set);
    });
    return reloadCourse(id);
  }

  // ---------------------------------------------------------------- checklist / submit / visibility / delete

  public Checklist checklist(UUID sellerId, UUID id) {
    Map<String, Object> c = getMyCourse(sellerId, id);
    var p = profiles.findById(sellerId).orElse(null);
    List<LessonDto> lessons = CourseReadModel.lessonsOf(c);
    String title = Objects.toString(str(c.get("title")), "");
    String shortDescription = Objects.toString(str(c.get("shortDescription")), "");
    String description = Objects.toString(str(c.get("description")), "");
    long price = num(c.get("price"));
    List<ChecklistItem> items = List.of(
        new ChecklistItem("verified", p != null && "verified".equals(p.getVerificationStatus()), "শিক্ষক ভেরিফিকেশন সম্পন্ন"),
        new ChecklistItem("title", title.length() >= 10, "শিরোনাম অন্তত ১০ অক্ষর"),
        new ChecklistItem("description", shortDescription.length() >= 30, "ছোট বিবরণ (অন্তত ৩০ অক্ষর)"),
        new ChecklistItem("learnings", size(c.get("learnings")) >= 3, "\"যা শিখবেন\" — অন্তত ৩টি পয়েন্ট"),
        new ChecklistItem("targets", size(c.get("targets")) >= 1, "অন্তত একটি টার্গেট (ইউনিভার্সিটি/সাবজেক্ট)"),
        new ChecklistItem("lessons", lessons.size() >= 3, "অন্তত ৩টি লেসন"),
        new ChecklistItem("preview", lessons.stream().anyMatch(LessonDto::isPreview), "অন্তত একটি প্রিভিউ লেসন"),
        new ChecklistItem("media", lessons.stream().filter(l -> l.type().equals("video")).allMatch(l -> l.video() != null && !"failed".equals(l.video().status()))
            && lessons.stream().filter(l -> l.type().equals("pdf")).allMatch(l -> l.document() != null), "সব ভিডিও/PDF লেসনে ফাইল যুক্ত"),
        new ChecklistItem("price", price == 0 || price >= 5000, "দাম ফ্রি অথবা অন্তত ৳৫০"),
        new ChecklistItem("noGuarantee", !GUARANTEE.matcher(title + " " + shortDescription + " " + description).find(), "কোনো \"নিশ্চিত প্রশ্ন/১০০% কমন\" দাবি নেই"));
    boolean can = items.stream().allMatch(ChecklistItem::ok) && List.of("draft", "changes_requested", "rejected").contains(str(c.get("status")));
    return new Checklist(items, can);
  }

  public Map<String, Object> submit(UUID sellerId, UUID id) {
    Checklist cl = checklist(sellerId, id);
    if (!cl.canSubmit()) throw new ApiException(ErrorCode.VALIDATION_FAILED, "রিভিউয়ের আগে চেকলিস্টের সবগুলো পূরণ করুন।", cl.items().stream().filter(i -> !i.ok()).toList());
    Map<String, Object> set = new LinkedHashMap<>();
    set.put("status", "in_review");
    set.put("submittedAt", Instant.now());
    set.put("reviewNote", null);
    repo.updateCourse(id, set);
    audit.log(AuditService.Entry.of(sellerId, "course.submit", "courses", id).after(Map.of("status", "in_review")));
    return getMyCourse(sellerId, id);
  }

  public Map<String, Object> setVisibility(UUID sellerId, UUID id, String status) {
    CourseRow c = ownCourse(sellerId, id);
    if (!List.of("published", "unlisted", "archived").contains(c.status()) || (status.equals("published") && c.publishedAt() == null))
      throw ApiException.badRequest("প্রকাশিত কোর্সই শুধু লুকানো বা আর্কাইভ করা যায়।");
    repo.updateCourse(id, Map.of("status", status));
    audit.log(AuditService.Entry.of(sellerId, "course." + status, "courses", id).before(Map.of("status", c.status())).after(Map.of("status", status)));
    jobs.enqueue(JobNames.SEARCH_INDEX_COURSE, Map.of("courseId", id));
    return getMyCourse(sellerId, id);
  }

  public void removeDraft(UUID sellerId, UUID id) {
    CourseRow c = ownCourse(sellerId, id);
    if (c.publishedAt() != null) throw ApiException.badRequest("প্রকাশিত কোর্স মুছা যায় না — আর্কাইভ করুন।");
    repo.updateCourse(id, Map.of("deletedAt", Instant.now()));
    audit.log(AuditService.Entry.of(sellerId, "course.delete", "courses", id));
  }

  // ---------------------------------------------------------------- uploads

  public UploadInstructions createUpload(UUID userId, UploadBody b) {
    boolean isSeller = profiles.existsById(userId);
    if (!(b instanceof IdCardUpload) && !isSeller) throw ApiException.forbidden(NOT_A_SELLER);
    return switch (b) {
      case VideoUpload v -> {
        var vp = video.provider();
        var up = vp.createUpload(v.title());
        boolean fake = vp.name().equals("fake");
        UUID id = repo.insertVideo(userId, vp.name(), up.providerVideoId(), null, v.title(), fake ? "ready" : "uploading", fake ? 600 : 0);
        boolean hasUrl = up.uploadUrl() != null && !up.uploadUrl().isEmpty();
        yield new UploadInstructions(id, hasUrl ? up.uploadUrl() : null, hasUrl ? "TUS" : null, up.headers() == null ? Map.of() : up.headers(), null, null);
      }
      case VideoLinkUpload v -> {
        String scheme;
        try {
          scheme = URI.create(v.url()).getScheme();
        } catch (RuntimeException e) {
          scheme = null;
        }
        if (scheme == null || !scheme.equalsIgnoreCase("https")) throw ApiException.badRequest("https লিংক দিন।");
        UUID id = repo.insertVideo(userId, "external_link", null, v.url(), v.title(), "ready", v.durationSec());
        yield new UploadInstructions(id, null, null, Map.of(), null, null);
      }
      case DocumentUpload d -> {
        ownCourse(userId, d.courseId());
        String ext = StorageKeys.EXT_BY_MIME.get(d.mime());
        if (ext == null) throw ApiException.badRequest("শুধু PDF, PPTX বা ছবি আপলোড করা যায়।");
        String key = StorageKeys.objectKey("courses/" + d.courseId(), "f." + ext);
        var signed = storage.signedUploadUrl(Bucket.COURSE_DOCS, key, d.mime());
        UUID id = repo.insertDocument(d.courseId(), userId, d.docKind(), d.title(), d.year(), Bucket.COURSE_DOCS.id(), key, null, d.mime(), d.size().intValue());
        yield new UploadInstructions(id, signed.url(), "PUT", signed.headers(), key, null);
      }
      case DocumentLinkUpload d -> {
        ownCourse(userId, d.courseId());
        UUID id = repo.insertDocument(d.courseId(), userId, d.docKind(), d.title(), d.year(), null, null, d.url(), null, null);
        yield new UploadInstructions(id, null, null, Map.of(), null, null);
      }
      case CoverUpload cv -> {
        String key = StorageKeys.objectKey("covers/" + userId, "c." + StorageKeys.EXT_BY_MIME.get(cv.mime()));
        var signed = storage.signedUploadUrl(Bucket.COURSE_COVERS, key, cv.mime());
        yield new UploadInstructions(null, signed.url(), "PUT", signed.headers(), key, storage.publicUrl(Bucket.COURSE_COVERS, key));
      }
      case IdCardUpload id -> {
        String key = StorageKeys.objectKey("id-cards/" + userId, "id." + StorageKeys.EXT_BY_MIME.get(id.mime()));
        var signed = storage.signedUploadUrl(Bucket.VERIFICATION_DOCS, key, id.mime());
        yield new UploadInstructions(null, signed.url(), "PUT", signed.headers(), key, null);
      }
    };
  }

  public void completeUpload(UUID sellerId, UUID id) {
    var d = repo.document(id);
    if (d.isPresent()) {
      if (!d.get().ownerId().equals(sellerId)) throw ApiException.forbidden();
      String key = d.get().storageKey();
      if (key != null && !key.isEmpty() && !storage.exists(Bucket.COURSE_DOCS, key)) throw ApiException.badRequest("ফাইলটি এখনো আপলোড হয়নি।");
      return;
    }
    var v = repo.video(id).orElse(null);
    if (v == null || !v.ownerId().equals(sellerId)) throw ApiException.notFound();
    if (v.provider().equals("bunny") && v.providerVideoId() != null && !v.providerVideoId().isEmpty()) {
      repo.updateVideo(id, "processing", null);
      jobs.enqueue(JobNames.VIDEO_PROCESSED, new VideoProcessedJob.Payload(id, 0), EnqueueOptions.delay(30_000));
    }
  }

  public SubjectRef proposeSubject(UUID sellerId, String name, List<String> aliases) {
    String slug = slugify(name);
    if (slug.isEmpty()) slug = "subject-" + System.currentTimeMillis();
    var exists = repo.subjectBySlug(slug);
    if (exists.isPresent()) return new SubjectRef(exists.get().getKey(), exists.get().getValue());
    UUID id = repo.insertProposedSubject(name, slug, aliases, sellerId);
    return new SubjectRef(id, "proposed");
  }
}
