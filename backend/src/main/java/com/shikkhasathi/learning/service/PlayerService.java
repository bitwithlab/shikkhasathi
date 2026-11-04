package com.shikkhasathi.learning.service;

import com.shikkhasathi.catalog.service.AcademicLookup;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Crypto;
import com.shikkhasathi.course.adapter.VideoService;
import com.shikkhasathi.file.service.Bucket;
import com.shikkhasathi.file.service.StorageService;
import com.shikkhasathi.learning.dto.LearningDtos.*;
import com.shikkhasathi.learning.repository.LearningContentRepository;
import com.shikkhasathi.learning.repository.LearningContentRepository.DocumentRow;
import com.shikkhasathi.learning.repository.LearningContentRepository.LessonRow;
import com.shikkhasathi.learning.repository.LearningContentRepository.TargetRow;
import com.shikkhasathi.learning.repository.LearningCoreRepository;
import com.shikkhasathi.learning.repository.LearningCoreRepository.ProgressRow;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** My Courses, the player outline, lesson access, signed playback (device limit, single stream, watermark) and progress. */
@Service
public class PlayerService {
  static final long PLAYBACK_TTL_SEC = 2 * 3600;
  static final long STREAM_TTL_SEC = 90;
  static final int MAX_DEVICES = 3;
  static final long DEVICE_WINDOW_MS = 30L * 86_400_000;
  static final int REVIEW_MIN_PROGRESS = 20;
  private static final long DAY_MS = 86_400_000L;
  private static final Pattern YOUTUBE = Pattern.compile("(?:youtu\\.be/|youtube\\.com/(?:watch\\?v=|embed/|shorts/))([\\w-]{11})");
  private static final Pattern HLS = Pattern.compile("\\.m3u8(\\?|$)");

  private final LearningAccess acc;
  private final LearningContentRepository content;
  private final LearningCoreRepository core;
  private final AcademicLookup academic;
  private final StorageService storage;
  private final VideoService video;
  private final StringRedisTemplate redis;

  public PlayerService(LearningAccess acc, LearningContentRepository content, LearningCoreRepository core, AcademicLookup academic, StorageService storage,
                       VideoService video, StringRedisTemplate redis) {
    this.acc = acc;
    this.content = content;
    this.core = core;
    this.academic = academic;
    this.storage = storage;
    this.video = video;
    this.redis = redis;
  }

  // ---------------------------------------------------------------- progress

  @Transactional
  public ProgressResponse heartbeat(UUID userId, UUID lessonId, ProgressBody b) {
    LessonRow l = acc.lessonOr404(lessonId);
    var a = acc.access(userId, l.courseId());
    acc.requireLessonAccess(a, l);
    boolean streamActive = true;
    if (b.streamToken() != null) {
      String cur = redis.opsForValue().get(streamKey(userId));
      streamActive = b.streamToken().equals(cur);
      if (streamActive) redis.expire(streamKey(userId), Duration.ofSeconds(STREAM_TTL_SEC));
    }
    if (!a.enrolled()) return new ProgressResponse(false, 0, streamActive);
    // a stream that was taken over by another device doesn't earn watch time
    int delta = streamActive ? Math.min(b.deltaSec(), 30) : 0;
    int cap = Math.max(l.durationSec(), 60) * 2;
    ProgressRow row = core.upsertHeartbeat(userId, l.courseId(), lessonId, delta, b.positionSec(), cap);
    boolean completed = row.completedAt() != null;
    if (!completed && l.durationSec() > 0 && row.watchedSeconds() >= l.durationSec() * 0.9) {
      core.markProgressCompleted(row.id());
      completed = true;
    }
    int pct = acc.recomputeProgress(userId, l.courseId(), lessonId);
    return new ProgressResponse(completed, pct, streamActive);
  }

  @Transactional
  public ProgressResponse setComplete(UUID userId, UUID lessonId, boolean completed) {
    LessonRow l = acc.lessonOr404(lessonId);
    var a = acc.access(userId, l.courseId());
    if (!a.enrolled()) throw ApiException.forbidden("এনরোল করা শিক্ষার্থীরাই প্রগ্রেস রাখতে পারেন।");
    core.upsertCompleted(userId, l.courseId(), lessonId, completed);
    return new ProgressResponse(completed, acc.recomputeProgress(userId, l.courseId(), lessonId), true);
  }

  // ---------------------------------------------------------------- My Courses

  public MyCourses myCourses(UUID userId, String filter) {
    var enr = core.myEnrollments(userId);
    if (enr.isEmpty()) return new MyCourses(List.of());
    var ids = enr.stream().map(LearningCoreRepository.EnrollmentRow::courseId).distinct().toList();
    var cs = content.courses(ids).stream().collect(Collectors.toMap(LearningContentRepository.CourseRow::id, c -> c));
    var ts = content.targets(ids);
    var firstTarget = new HashMap<UUID, TargetRow>();
    for (var t : ts) firstTarget.putIfAbsent(t.courseId(), t);
    var counts = content.countableLessons(ids);
    var done = core.completedLessons(userId, ids);
    var sellers = core.sellerNames(cs.values().stream().map(LearningContentRepository.CourseRow::sellerId).distinct().toList());
    var names = academic.lookup(ts.stream().map(TargetRow::institutionId).filter(Objects::nonNull).distinct().toList(), List.of(), List.of());
    var lastIds = enr.stream().map(LearningCoreRepository.EnrollmentRow::lastLessonId).filter(Objects::nonNull).distinct().toList();
    var lastLessons = content.lessonTitles(lastIds).stream().collect(Collectors.toMap(LearningContentRepository.LessonTitle::id, x -> x, (x, y) -> x));
    var firsts = content.firstLessons(ids);
    long now = System.currentTimeMillis();
    var items = new ArrayList<MyCourse>();
    for (var e : enr) {
      var c = cs.get(e.courseId());
      if (c == null) continue;
      var t = firstTarget.get(c.id());
      var first = firsts.get(c.id());
      var last = e.lastLessonId() == null ? null : lastLessons.get(e.lastLessonId());
      Long until = e.accessUntil() == null ? null : e.accessUntil().toEpochMilli();
      boolean expired = "expired".equals(e.status()) || (until != null && until < now);
      String instShort = null;
      if (t != null && t.institutionId() != null) {
        var inst = names.institution().get(t.institutionId());
        if (inst != null && inst.shortName() != null && !inst.shortName().isEmpty()) instShort = inst.shortName();
      }
      var seller = sellers.get(c.sellerId());
      items.add(new MyCourse(c.id(), c.slug(), c.title(), t == null ? null : t.code(), instShort, c.examType(), seller == null || seller.name() == null ? "" : seller.name(),
          c.coverColor(), e.progressPercent(), done.getOrDefault(c.id(), 0), counts.getOrDefault(c.id(), 0),
          last != null ? last.id() : first != null ? first.id() : null, last != null ? last.title() : first != null ? first.title() : null,
          e.lastAccessedAt(), e.completedAt(), e.accessUntil(), !expired && until != null && until - now < 7 * DAY_MS, expired));
    }
    var f = items.stream().filter(i -> switch (filter) {
      case "in_progress" -> i.completedAt() == null && !i.expired();
      case "completed" -> i.completedAt() != null;
      case "expiring" -> i.expiringSoon();
      default -> true;
    }).toList();
    return new MyCourses(f);
  }

  /** Dashboard "continue learning": the most recently accessed in-progress course, or null. */
  public ContinueWidget continueLearning(UUID userId) {
    var items = myCourses(userId, "in_progress").items();
    if (items.isEmpty()) return null;
    var c = items.get(0);
    return new ContinueWidget(c.courseId(), c.slug(), c.title(), c.courseCode(), c.sellerName(), c.progressPercent(), c.completedLessons(), c.totalLessons(),
        c.resumeLessonId(), c.resumeLessonTitle(), c.coverColor());
  }

  // ---------------------------------------------------------------- player

  public PlayerOutline outline(UUID userId, UUID courseId, List<String> roles) {
    var a = acc.access(userId, courseId, roles);
    var c = a.course();
    if (!"published".equals(c.status()) && !a.isOwner() && !a.isAdmin() && a.enrollment() == null) throw ApiException.notFound("কোর্সটি পাওয়া যায়নি।");
    var cur = acc.curriculum(courseId);
    var prog = core.progressFor(userId, courseId).stream().collect(Collectors.toMap(ProgressRow::lessonId, p -> p, (x, y) -> x));
    var seller = core.sellerNames(List.of(c.sellerId())).get(c.sellerId());
    var docs = content.documentsOf(courseId);
    var rev = core.reviewBy(courseId, userId).orElse(null);
    String code = content.firstTargetCode(courseId).orElse(null);
    boolean all = a.canSeeAll();
    var countable = cur.flat().stream().filter(LearningAccess::countable).toList();
    int completedLessons = (int) countable.stream().filter(l -> prog.containsKey(l.id()) && prog.get(l.id()).completedAt() != null).count();
    int pct = a.enrollment() == null ? 0 : a.enrollment().progressPercent();
    UUID last = a.enrollment() == null ? null : a.enrollment().lastLessonId();
    UUID resume = last != null && cur.flat().stream().anyMatch(l -> l.id().equals(last)) ? last
        : cur.flat().stream().filter(l -> all || l.isPreview()).map(LessonRow::id).findFirst().orElse(null);
    var sections = cur.tree().stream().map(s -> new PlayerSection(s.section().id(), s.section().title(), s.chapters().stream()
        .map(ch -> new PlayerChapter(ch.chapter().id(), ch.chapter().title(), ch.lessons().stream().map(l -> {
          var p = prog.get(l.id());
          return new PlayerLesson(l.id(), l.title(), l.type(), l.durationSec(), l.isPreview(), l.isImportant(), p != null && p.completedAt() != null,
              p == null ? 0 : p.lastPositionSec(), !all && !l.isPreview());
        }).toList())).toList())).toList();
    return new PlayerOutline(
        new OutlineCourse(c.id(), c.slug(), c.title(), code, c.sellerId(), seller == null || seller.name() == null ? "" : seller.name(), seller == null ? null : seller.slug()),
        a.enrolled(), a.isOwner(), a.enrollment() == null ? null : a.enrollment().accessUntil(), pct, completedLessons, countable.size(), resume, sections,
        all ? docs.stream().map(d -> new Resource(d.id(), d.kind(), d.title(), d.year())).toList() : List.of(),
        rev == null ? null : new MyReview(rev.id(), rev.rating(), rev.body()), a.enrolled() && pct >= REVIEW_MIN_PROGRESS);
  }

  static String youtubeId(String u) {
    var m = YOUTUBE.matcher(u);
    return m.find() ? m.group(1) : null;
  }

  static String externalKind(String u) {
    return youtubeId(u) != null ? "youtube" : HLS.matcher(u).find() ? "hls" : "external";
  }

  private String documentLink(DocumentRow d) {
    if (d.storageKey() != null && !d.storageKey().isEmpty() && d.storageBucket() != null && !d.storageBucket().isEmpty())
      return storage.signedDownloadUrl(Bucket.of(d.storageBucket()).orElseThrow(() -> new IllegalStateException("unknown bucket " + d.storageBucket())), d.storageKey(), 900, null);
    return d.externalUrl() == null ? "" : d.externalUrl();
  }

  @Transactional
  public LessonDetail lessonDetail(UUID userId, UUID lessonId, List<String> roles) {
    LessonRow l = acc.lessonOr404(lessonId);
    var a = acc.access(userId, l.courseId(), roles);
    acc.requireLessonAccess(a, l);
    boolean all = a.canSeeAll();
    var flat = acc.curriculum(l.courseId()).flat();
    var p = core.progress(userId, lessonId).orElse(null);
    var d = l.documentId() == null ? null : content.document(l.documentId()).orElse(null);
    var v = l.videoAssetId() == null ? null : content.video(l.videoAssetId()).orElse(null);
    var visible = flat.stream().filter(x -> all || x.isPreview()).toList();
    int i = -1;
    for (int k = 0; k < visible.size(); k++) if (visible.get(k).id().equals(l.id())) i = k;
    LessonDocument document = d == null ? null : new LessonDocument(d.id(), d.title(), documentLink(d), d.pages());
    // record "last opened" for non-video lessons so My Courses resumes here
    if (a.enrolled() && !"video".equals(l.type())) acc.recomputeProgress(userId, l.courseId(), l.id());
    LessonVideo vid = v == null ? null
        : new LessonVideo("external_link".equals(v.provider()) && v.externalUrl() != null && !v.externalUrl().isEmpty() ? externalKind(v.externalUrl()) : "hls", v.status());
    return new LessonDetail(l.id(), l.courseId(), l.title(), l.type(), l.durationSec(), l.isImportant(), l.body(), document, vid, p != null && p.completedAt() != null,
        p == null ? 0 : p.lastPositionSec(), i > 0 ? visible.get(i - 1).id() : null, i >= 0 && i < visible.size() - 1 ? visible.get(i + 1).id() : null);
  }

  static String streamKey(UUID userId) {
    return "ps:" + userId;
  }

  /** Max 3 playback devices per 30 days; a new playback takes over the single active stream. */
  private void claimDevice(UUID userId, String deviceId) {
    String k = "pd:" + userId;
    long now = System.currentTimeMillis();
    var z = redis.opsForZSet();
    z.removeRangeByScore(k, 0, now - DEVICE_WINDOW_MS);
    Double known = z.score(k, deviceId);
    Long n = z.zCard(k);
    if (known == null && n != null && n >= MAX_DEVICES)
      throw ApiException.forbidden("সর্বোচ্চ " + MAX_DEVICES + "টি ডিভাইসে কোর্স দেখা যায়। পুরোনো ডিভাইস ৩০ দিন ব্যবহার না হলে নিজে থেকেই সরে যায় — জরুরি হলে সাপোর্টে যোগাযোগ করুন।");
    z.add(k, deviceId, now);
    redis.expire(k, Duration.ofMillis(DEVICE_WINDOW_MS));
  }

  public Playback playback(UUID userId, UUID lessonId, PlaybackBody b, List<String> roles) {
    LessonRow l = acc.lessonOr404(lessonId);
    var a = acc.access(userId, l.courseId(), roles);
    acc.requireLessonAccess(a, l);
    if (!"video".equals(l.type()) || l.videoAssetId() == null) throw ApiException.badRequest("এই লেসনে ভিডিও নেই।");
    var v = content.video(l.videoAssetId()).orElseThrow(() -> ApiException.notFound("ভিডিওটি পাওয়া যায়নি।"));
    if (!"external_link".equals(v.provider()) && !"ready".equals(v.status())) throw ApiException.conflict("ভিডিওটি এখনো প্রসেস হচ্ছে। কিছুক্ষণ পর আবার চেষ্টা করুন।");
    if (!a.isOwner() && !a.isAdmin()) claimDevice(userId, b.deviceId());
    String streamToken = Crypto.randomToken(12);
    redis.opsForValue().set(streamKey(userId), streamToken, Duration.ofSeconds(STREAM_TTL_SEC));
    var u = core.contact(userId).orElse(null);
    var p = core.progress(userId, lessonId).orElse(null);
    String who;
    if (u != null && u.phone() != null && !u.phone().isEmpty()) who = "••" + u.phone().substring(Math.max(0, u.phone().length() - 4));
    else if (u != null && u.email() != null) {
      String local = u.email().split("@", -1)[0];
      who = local.substring(0, Math.min(6, local.length()));
    } else who = "";
    String uid = userId.toString();
    String watermark = "ShikkhaSathi · " + who + " · " + uid.substring(uid.length() - 6);
    String kind = "hls";
    String url;
    if ("external_link".equals(v.provider())) {
      String ext = v.externalUrl() == null ? "" : v.externalUrl();
      kind = externalKind(ext);
      url = "youtube".equals(kind) ? "https://www.youtube-nocookie.com/embed/" + youtubeId(ext) + "?rel=0&modestbranding=1" : ext;
    } else {
      url = video.provider().playbackUrl(v.providerVideoId() == null ? "" : v.providerVideoId(), PLAYBACK_TTL_SEC);
    }
    int pos = p == null ? 0 : p.lastPositionSec();
    return new Playback(kind, url, Instant.now().plusSeconds(PLAYBACK_TTL_SEC), streamToken, watermark, l.durationSec() != 0 && pos > l.durationSec() - 10 ? 0 : pos);
  }

  public DocumentUrl documentUrl(UUID userId, UUID id, List<String> roles) {
    var d = content.document(id).orElse(null);
    if (d == null || d.courseId() == null) throw ApiException.notFound();
    var a = acc.access(userId, d.courseId(), roles);
    if (!a.canSeeAll()) throw ApiException.forbidden("এই রিসোর্স দেখতে কোর্সটি কিনুন।");
    String url = documentLink(d);
    if (url.isEmpty()) throw ApiException.notFound();
    return new DocumentUrl(url, d.title());
  }
}
