package com.shikkhasathi.marketplace.service;

import com.shikkhasathi.catalog.service.AcademicLookup;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.marketplace.dto.MarketplaceDtos.*;
import com.shikkhasathi.marketplace.repository.MarketplaceRepository;
import com.shikkhasathi.marketplace.repository.MarketplaceRepository.SellerProfileRow;
import com.shikkhasathi.search.dto.SearchDtos.CourseCard;
import com.shikkhasathi.search.dto.SearchDtos.SearchQuery;
import com.shikkhasathi.search.repository.CourseRows.CourseRow;
import com.shikkhasathi.search.repository.CourseRows.TargetRow;
import com.shikkhasathi.search.repository.SearchRepository;
import com.shikkhasathi.search.service.SearchService;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Course details, teacher storefront, landing "featured", recommendations and the wishlist. */
@Service
public class CourseService {
  private static final Pattern UUID_RE = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", Pattern.CASE_INSENSITIVE);
  private static final String[] BN = {"০", "১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯"};
  private static final Map<Integer, String> ORD = Map.of(1, "১ম", 2, "২য়", 3, "৩য়", 4, "৪র্থ", 5, "৫ম", 6, "৬ষ্ঠ", 7, "৭ম", 8, "৮ম", 9, "৯ম", 10, "১০ম");

  private final MarketplaceRepository repo;
  private final SearchRepository searchRepo;
  private final SearchService search;
  private final AcademicLookup academic;
  private final JsonMapper json;

  public CourseService(MarketplaceRepository repo, SearchRepository searchRepo, SearchService search, AcademicLookup academic, JsonMapper json) {
    this.repo = repo;
    this.searchRepo = searchRepo;
    this.search = search;
    this.academic = academic;
    this.json = json;
  }

  public static String bnDigits(Object n) {
    StringBuilder b = new StringBuilder();
    for (char ch : String.valueOf(n).toCharArray()) b.append(ch >= '0' && ch <= '9' ? BN[ch - '0'] : String.valueOf(ch));
    return b.toString();
  }

  public static String ordinalBn(int n) {
    return ORD.containsKey(n) ? ORD.get(n) : bnDigits(n) + "তম";
  }

  private static double round2(double v) {
    return Math.round(v * 100) / 100.0;
  }

  // ---------------------------------------------------------------- details

  public Map<String, Object> courseDetail(String slug, UUID userId, List<String> roles) {
    // checkout links by id; public pages by slug
    CourseRow c = UUID_RE.matcher(slug).matches() ? repo.courseById(UUID.fromString(slug)) : repo.courseBySlug(slug);
    boolean isOwner = c != null && c.sellerId().equals(userId);
    if (c == null || (!"published".equals(c.status()) && !isOwner && !roles.contains("admin"))) throw ApiException.notFound("কোর্সটি পাওয়া যায়নি।");
    var student = search.studentContext(userId);
    CourseCard card = search.toCards(List.of(c), student).get(0);
    var secs = repo.sections(c.id());
    var chs = repo.chapters(c.id());
    var ls = repo.lessons(c.id());
    var docs = repo.documents(c.id());
    int mockN = repo.mockTestCount(c.id());
    int flashN = repo.flashcardCount(c.id());
    SellerProfileRow sp = repo.sellerProfile(c.sellerId());
    var agg = repo.sellerAgg(c.sellerId());
    var dist = repo.ratingDistribution(c.id());
    var enr = userId != null ? repo.activeEnrollment(userId, c.id()) : null;
    boolean wish = userId != null && repo.wishlisted(userId, c.id());
    List<TargetRow> ts = searchRepo.targets(List.of(c.id()));

    var names = academic.lookup(ts.stream().map(TargetRow::institutionId).filter(Objects::nonNull).toList(), ts.stream().map(TargetRow::departmentId).filter(Objects::nonNull).toList(), List.of());
    var subs = repo.subjects(ts.stream().map(TargetRow::subjectMasterId).filter(Objects::nonNull).toList());

    // breadcrumb from the best target (student's own institution first)
    TargetRow primary = SearchService.primaryTarget(ts, student);
    List<Crumb> breadcrumb = new ArrayList<>();
    if (primary != null && primary.institutionId() != null) {
      var inst = names.institution().get(primary.institutionId());
      if (inst != null) {
        breadcrumb.add(new Crumb(inst.name(), "/u/" + inst.slug()));
        var dept = primary.departmentId() != null ? names.department().get(primary.departmentId()) : null;
        if (dept != null) breadcrumb.add(new Crumb(dept.shortName(), "/u/" + inst.slug() + "?dept=" + primary.departmentId()));
        if (primary.semester() != null && primary.semester() != 0)
          breadcrumb.add(new Crumb(ordinalBn(primary.semester()) + " সেমিস্টার", "/u/" + inst.slug() + "?dept=" + (primary.departmentId() == null ? "" : primary.departmentId()) + "&sem=" + primary.semester()));
      }
    }
    UUID primarySubject = primary == null ? null : primary.subjectMasterId();
    subs.stream().filter(s -> s.id().equals(primarySubject)).findFirst().ifPresent(s -> breadcrumb.add(new Crumb(s.name(), "/s/" + s.slug())));

    List<Integer> distribution = new ArrayList<>();
    for (int r = 1; r <= 5; r++) distribution.add(dist.getOrDefault(r, 0));
    UUID firstLesson = ls.isEmpty() ? null : ls.get(0).id();
    UUID resume = enr != null ? (enr.lastLessonId() != null ? enr.lastLessonId() : firstLesson) : null;

    List<TargetLabel> targets = ts.stream().map(t -> {
      String label;
      if (t.isGeneral()) label = "General / Cross-University";
      else {
        List<String> parts = new ArrayList<>();
        if (t.institutionId() != null && names.institution().containsKey(t.institutionId())) parts.add(names.institution().get(t.institutionId()).shortName());
        if (t.departmentId() != null && names.department().containsKey(t.departmentId())) parts.add(names.department().get(t.departmentId()).shortName());
        parts.add(t.courseCode());
        label = parts.stream().filter(s -> s != null && !s.isEmpty()).collect(Collectors.joining(" · "));
      }
      String instSlug = t.institutionId() != null && names.institution().containsKey(t.institutionId()) ? names.institution().get(t.institutionId()).slug() : null;
      return new TargetLabel(label, instSlug, t.courseCode(), t.semester(), t.batches());
    }).toList();

    List<SectionDto> sections = secs.stream().map(s -> new SectionDto(s.id(), s.title(), chs.stream().filter(ch -> ch.sectionId().equals(s.id()))
        .map(ch -> new ChapterDto(ch.id(), ch.title(), ls.stream().filter(l -> l.chapterId().equals(ch.id()))
            .map(l -> new PublicLesson(l.id(), l.title(), l.type(), l.durationSec(), l.isPreview(), l.isImportant())).toList()))
        .toList())).toList();

    int videos = (int) ls.stream().filter(l -> "video".equals(l.type())).count();
    int notes = (int) docs.stream().filter(d -> "notes".equals(d.kind()) || "pdf".equals(d.kind()) || "slides".equals(d.kind())).count() + (int) ls.stream().filter(l -> "pdf".equals(l.type())).count();
    List<Integer> years = docs.stream().filter(d -> "previous_questions".equals(d.kind()) && d.year() != null && d.year() != 0).map(MarketplaceRepository.Doc::year).distinct().sorted(Comparator.reverseOrder()).toList();

    Map<String, Object> out = json.convertValue(card, new TypeReference<LinkedHashMap<String, Object>>() {});
    out.put("description", c.description());
    out.put("learnings", c.learnings());
    out.put("notFor", c.notFor());
    out.put("faq", c.faqJson() == null ? List.of() : json.readValue(c.faqJson(), new TypeReference<List<Object>>() {}));
    out.put("language", c.language());
    out.put("accessType", c.accessType());
    out.put("accessDays", c.accessDays());
    out.put("publishedAt", JsonConfig.iso(c.publishedAt()));
    out.put("updatedAt", JsonConfig.iso(c.effectiveUpdatedAt()));
    out.put("breadcrumb", breadcrumb);
    out.put("targets", targets);
    out.put("seller", new SellerCard(c.sellerId(), sp == null ? "" : sp.slug(), c.sellerName(), sp == null ? null : sp.headline(), c.sellerVerified(), round2(agg.rating()), agg.students(), agg.courses()));
    out.put("sections", sections);
    out.put("resources", new Resources(videos, notes, years, mockN, flashN));
    out.put("ratingDistribution", distribution);
    out.put("viewer", new Viewer(enr != null, wish, firstLesson, resume, isOwner));
    out.put("status", c.status());
    return out;
  }

  // ---------------------------------------------------------------- storefront

  public Storefront storefront(String slug, UUID userId) {
    SellerProfileRow p = repo.sellerProfileBySlug(slug);
    if (p == null) throw ApiException.notFound("এই শিক্ষকের পেজ পাওয়া যায়নি।");
    var student = search.studentContext(userId);
    var rows = repo.publishedBySeller(p.userId());
    var instTargets = repo.targetInstitutionIds(rows.stream().map(CourseRow::id).toList());
    List<UUID> instIds = new ArrayList<>(instTargets.stream().filter(Objects::nonNull).toList());
    if (p.institutionId() != null) instIds.add(p.institutionId());
    var names = academic.lookup(instIds, List.of(), List.of());
    int ratingCount = rows.stream().mapToInt(CourseRow::ratingCount).sum();
    double weighted = rows.stream().mapToDouble(r -> r.ratingAvg() * r.ratingCount()).sum();
    var taught = new LinkedHashSet<String>();
    for (UUID i : instTargets) if (i != null && names.institution().containsKey(i) && names.institution().get(i).shortName() != null && !names.institution().get(i).shortName().isEmpty()) taught.add(names.institution().get(i).shortName());
    return new Storefront(p.userId(), p.slug(), p.displayName(), p.headline(), p.bio(), p.coverUrl(), "verified".equals(p.verificationStatus()),
        p.institutionId() != null && names.institution().containsKey(p.institutionId()) ? names.institution().get(p.institutionId()).name() : null,
        List.copyOf(taught),
        new StorefrontStats(rows.stream().mapToInt(CourseRow::enrollmentCount).sum(), rows.size(), ratingCount > 0 ? round2(weighted / ratingCount) : 0, ratingCount),
        search.toCards(rows, student));
  }

  // ---------------------------------------------------------------- landing

  public Featured featured(UUID institutionId) {
    var res = search.search(SearchQuery.of("popular", 8, institutionId), null);
    var sellers = repo.topVerifiedSellers();
    var profiles = repo.sellerProfiles(sellers.stream().map(MarketplaceRepository.SellerTotals::sellerId).toList());
    var names = academic.lookup(profiles.stream().map(SellerProfileRow::institutionId).filter(Objects::nonNull).toList(), List.of(), List.of());
    var tot = repo.publishedTotals();
    int inst = repo.targetedInstitutionCount();
    List<FeaturedTeacher> teachers = sellers.stream().map(s -> {
      SellerProfileRow p = profiles.stream().filter(x -> x.userId().equals(s.sellerId())).findFirst().orElse(null);
      String instName = p != null && p.institutionId() != null && names.institution().containsKey(p.institutionId()) ? names.institution().get(p.institutionId()).name() : null;
      return new FeaturedTeacher(p == null ? "" : p.slug(), p == null ? "" : p.displayName(), p == null ? null : p.headline(), instName, round2(s.rating()), s.students(), s.courses());
    }).toList();
    return new Featured(res.items(), teachers, new FeaturedStats(tot.students(), tot.courses(), inst));
  }

  /** "আপনার জন্য কোর্স" — ranked by the spec §6.5 score for this student, excluding enrolled courses. */
  public Items recommendations(UUID userId, int limit) {
    var enrolled = repo.enrolledCourseIds(userId);
    var res = search.search(SearchQuery.of("best", limit + enrolled.size(), null), userId);
    var set = new HashSet<>(enrolled);
    return new Items(res.items().stream().filter(c -> !set.contains(c.id())).limit(limit).toList());
  }

  // ---------------------------------------------------------------- wishlist

  public void addWishlist(UUID userId, UUID courseId) {
    if (!repo.publishedCourseExists(courseId)) throw ApiException.notFound();
    repo.addWishlist(userId, courseId);
  }

  public void removeWishlist(UUID userId, UUID courseId) {
    repo.removeWishlist(userId, courseId);
  }

  public Items listWishlist(UUID userId) {
    var ids = repo.wishlistCourseIds(userId);
    if (ids.isEmpty()) return new Items(List.of());
    var rows = searchRepo.coursesByIds(ids, true);
    var student = search.studentContext(userId);
    var cards = search.toCards(rows, student);
    Map<UUID, CourseCard> byId = new HashMap<>();
    cards.forEach(c -> byId.put(c.id(), c));
    return new Items(ids.stream().map(byId::get).filter(Objects::nonNull).toList());
  }
}
