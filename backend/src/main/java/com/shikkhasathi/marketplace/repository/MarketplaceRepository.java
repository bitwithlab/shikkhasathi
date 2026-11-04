package com.shikkhasathi.marketplace.repository;

import com.shikkhasathi.common.Ids;
import com.shikkhasathi.search.repository.CourseRows;
import com.shikkhasathi.search.repository.CourseRows.CourseRow;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Marketplace reads (course details, storefronts, landing) and the wishlist. CORE and CONTENT are queried separately. */
@Repository
public class MarketplaceRepository {
  private final NamedParameterJdbcTemplate content;
  private final NamedParameterJdbcTemplate core;

  public MarketplaceRepository(@Qualifier("contentJdbc") NamedParameterJdbcTemplate content, @Qualifier("coreJdbc") NamedParameterJdbcTemplate core) {
    this.content = content;
    this.core = core;
  }

  private static List<UUID> uniq(Collection<UUID> ids) {
    return ids.stream().filter(Objects::nonNull).distinct().toList();
  }

  // ---------------------------------------------------------------- CONTENT: courses

  public CourseRow courseById(UUID id) {
    var r = content.query("select " + CourseRows.COURSE_COLS + " from courses c where c.id = :id and c.deleted_at is null", Map.of("id", id), CourseRows.COURSE);
    return r.isEmpty() ? null : r.get(0);
  }

  public CourseRow courseBySlug(String slug) {
    var r = content.query("select " + CourseRows.COURSE_COLS + " from courses c where c.slug = :s and c.deleted_at is null", Map.of("s", slug), CourseRows.COURSE);
    return r.isEmpty() ? null : r.get(0);
  }

  public record Section(UUID id, String title) {}

  public record Chapter(UUID id, UUID sectionId, String title) {}

  public record LessonRow(UUID id, UUID chapterId, String title, String type, int durationSec, boolean isPreview, boolean isImportant) {}

  public record Doc(String kind, Integer year) {}

  public List<Section> sections(UUID courseId) {
    return content.query("select id, title from course_sections where course_id = :c order by position asc", Map.of("c", courseId), (rs, i) -> new Section(rs.getObject("id", UUID.class), rs.getString("title")));
  }

  public List<Chapter> chapters(UUID courseId) {
    return content.query("select id, section_id, title from course_chapters where course_id = :c order by position asc", Map.of("c", courseId),
        (rs, i) -> new Chapter(rs.getObject("id", UUID.class), rs.getObject("section_id", UUID.class), rs.getString("title")));
  }

  public List<LessonRow> lessons(UUID courseId) {
    return content.query("select id, chapter_id, title, type, duration_sec, is_preview, is_important from lessons where course_id = :c order by position asc", Map.of("c", courseId),
        (rs, i) -> new LessonRow(rs.getObject("id", UUID.class), rs.getObject("chapter_id", UUID.class), rs.getString("title"), rs.getString("type"), rs.getInt("duration_sec"), rs.getBoolean("is_preview"), rs.getBoolean("is_important")));
  }

  public List<Doc> documents(UUID courseId) {
    return content.query("select kind, year from documents where course_id = :c", Map.of("c", courseId), (rs, i) -> new Doc(rs.getString("kind"), (Integer) rs.getObject("year", Integer.class)));
  }

  public int mockTestCount(UUID courseId) {
    return content.queryForObject("select count(*)::int from mock_tests where course_id = :c", Map.of("c", courseId), Integer.class);
  }

  public int flashcardCount(UUID courseId) {
    return content.queryForObject("select count(*)::int from flashcards where course_id = :c", Map.of("c", courseId), Integer.class);
  }

  public record SellerAgg(int students, int courses, double rating) {}

  /** Published, non-deleted courses of a seller: total students, count, rating weighted by rating count. */
  public SellerAgg sellerAgg(UUID sellerId) {
    return content.queryForObject(
        "select coalesce(sum(enrollment_count),0)::int as students, count(*)::int as n, coalesce(sum(rating_avg * rating_count) / nullif(sum(rating_count),0), 0)::float as rating from courses where seller_id = :s and status = 'published' and deleted_at is null",
        Map.of("s", sellerId), (rs, i) -> new SellerAgg(rs.getInt("students"), rs.getInt("n"), rs.getDouble("rating")));
  }

  public record SubjectRow(UUID id, String name, String nameBn, String slug) {}

  public List<SubjectRow> subjects(Collection<UUID> ids) {
    var u = uniq(ids);
    if (u.isEmpty()) return List.of();
    return content.query("select id, name, name_bn, slug from subject_masters where id in (:ids)", Map.of("ids", u),
        (rs, i) -> new SubjectRow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("name_bn"), rs.getString("slug")));
  }

  public List<CourseRow> publishedBySeller(UUID sellerId) {
    return content.query("select " + CourseRows.COURSE_COLS + " from courses c where c.seller_id = :s and c.status = 'published' and c.deleted_at is null order by c.enrollment_count desc", Map.of("s", sellerId), CourseRows.COURSE);
  }

  public List<UUID> targetInstitutionIds(Collection<UUID> courseIds) {
    var u = uniq(courseIds);
    if (u.isEmpty()) return List.of();
    return content.query("select institution_id from course_targets where course_id in (:ids)", Map.of("ids", u), (rs, i) -> rs.getObject("institution_id", UUID.class));
  }

  public record SellerTotals(UUID sellerId, int students, int courses, double rating) {}

  /** Top 4 verified sellers by students (published, non-deleted courses). */
  public List<SellerTotals> topVerifiedSellers() {
    return content.query(
        "select seller_id, sum(enrollment_count)::int as students, count(*)::int as n, coalesce(sum(rating_avg * rating_count) / nullif(sum(rating_count),0), 0)::float as rating from courses where status = 'published' and seller_verified = true and deleted_at is null group by seller_id order by sum(enrollment_count) desc limit 4",
        Map.of(), (rs, i) -> new SellerTotals(rs.getObject("seller_id", UUID.class), rs.getInt("students"), rs.getInt("n"), rs.getDouble("rating")));
  }

  public record Totals(int students, int courses) {}

  public Totals publishedTotals() {
    return content.queryForObject("select coalesce(sum(enrollment_count),0)::int as students, count(*)::int as n from courses where status = 'published'", Map.of(),
        (rs, i) -> new Totals(rs.getInt("students"), rs.getInt("n")));
  }

  public int targetedInstitutionCount() {
    return content.queryForObject("select count(distinct institution_id)::int from course_targets", Map.of(), Integer.class);
  }

  public boolean publishedCourseExists(UUID id) {
    return !content.queryForList("select id from courses where id = :id and status = 'published'", Map.of("id", id), UUID.class).isEmpty();
  }

  // ---------------------------------------------------------------- CORE: sellers, reviews, enrollments

  public record SellerProfileRow(UUID userId, String slug, String displayName, String headline, String bio, String coverUrl, UUID institutionId, String verificationStatus, Instant updatedAt) {}

  private static final String SELLER_COLS = "user_id, slug, display_name, headline, bio, cover_url, institution_id, verification_status, updated_at";
  private static final RowMapper<SellerProfileRow> SELLER = (rs, i) -> new SellerProfileRow(rs.getObject("user_id", UUID.class), rs.getString("slug"), rs.getString("display_name"),
      rs.getString("headline"), rs.getString("bio"), rs.getString("cover_url"), rs.getObject("institution_id", UUID.class), rs.getString("verification_status"), CourseRows.instant(rs, "updated_at"));

  public SellerProfileRow sellerProfile(UUID userId) {
    var r = core.query("select " + SELLER_COLS + " from seller_profiles where user_id = :u", Map.of("u", userId), SELLER);
    return r.isEmpty() ? null : r.get(0);
  }

  public SellerProfileRow sellerProfileBySlug(String slug) {
    var r = core.query("select " + SELLER_COLS + " from seller_profiles where slug = :s", Map.of("s", slug), SELLER);
    return r.isEmpty() ? null : r.get(0);
  }

  public List<SellerProfileRow> sellerProfiles(Collection<UUID> userIds) {
    var u = uniq(userIds);
    if (u.isEmpty()) return List.of();
    return core.query("select " + SELLER_COLS + " from seller_profiles where user_id in (:ids)", Map.of("ids", u), SELLER);
  }

  /** Visible review counts per rating (1–5). */
  public Map<Integer, Integer> ratingDistribution(UUID courseId) {
    Map<Integer, Integer> out = new java.util.HashMap<>();
    core.query("select rating, count(*)::int as n from reviews where course_id = :c and status = 'visible' group by rating", Map.of("c", courseId), rs -> {
      out.put(rs.getInt("rating"), rs.getInt("n"));
    });
    return out;
  }

  public record ActiveEnrollment(UUID lastLessonId) {}

  public ActiveEnrollment activeEnrollment(UUID studentId, UUID courseId) {
    var r = core.query("select last_lesson_id from enrollments where student_id = :s and course_id = :c and status = 'active'", Map.of("s", studentId, "c", courseId),
        (rs, i) -> new ActiveEnrollment(rs.getObject("last_lesson_id", UUID.class)));
    return r.isEmpty() ? null : r.get(0);
  }

  public List<UUID> enrolledCourseIds(UUID studentId) {
    return core.queryForList("select course_id from enrollments where student_id = :s", Map.of("s", studentId), UUID.class);
  }

  // ---------------------------------------------------------------- CORE: wishlist

  public boolean wishlisted(UUID userId, UUID courseId) {
    return !core.queryForList("select id from wishlists where user_id = :u and course_id = :c", Map.of("u", userId, "c", courseId), UUID.class).isEmpty();
  }

  public void addWishlist(UUID userId, UUID courseId) {
    core.update("insert into wishlists (id, user_id, course_id) values (:id, :u, :c) on conflict do nothing", new MapSqlParameterSource("id", Ids.uuidv7()).addValue("u", userId).addValue("c", courseId));
  }

  public void removeWishlist(UUID userId, UUID courseId) {
    core.update("delete from wishlists where user_id = :u and course_id = :c", Map.of("u", userId, "c", courseId));
  }

  public List<UUID> wishlistCourseIds(UUID userId) {
    return core.queryForList("select course_id from wishlists where user_id = :u order by created_at desc", Map.of("u", userId), UUID.class);
  }

  // ---------------------------------------------------------------- SEO / sitemap

  public record SlugAt(String slug, Instant at) {}

  private static final RowMapper<SlugAt> SLUG_AT = (rs, i) -> new SlugAt(rs.getString("slug"), CourseRows.instant(rs, "updated_at"));

  public List<SlugAt> sitemapCourses() {
    return content.query("select slug, updated_at from courses where status = 'published' and deleted_at is null order by updated_at desc limit 20000", Map.of(), SLUG_AT);
  }

  public List<SlugAt> sitemapInstitutions() {
    return content.query("select slug, updated_at from institutions where status = 'approved'", Map.of(), SLUG_AT);
  }

  public List<SlugAt> sitemapSubjects() {
    return content.query("select slug, updated_at from subject_masters where status = 'approved'", Map.of(), SLUG_AT);
  }

  public List<SlugAt> sitemapTeachers() {
    return core.query("select slug, updated_at from seller_profiles where verification_status in ('verified')", Map.of(), SLUG_AT);
  }

  public CourseRow publishedCourseBySlug(String slug) {
    var r = content.query("select " + CourseRows.COURSE_COLS + " from courses c where c.slug = :s and c.status = 'published' and c.deleted_at is null", Map.of("s", slug), CourseRows.COURSE);
    return r.isEmpty() ? null : r.get(0);
  }

  public record FirstTarget(String code, UUID institutionId) {}

  public FirstTarget firstTarget(UUID courseId) {
    var r = content.query("select course_code, institution_id from course_targets where course_id = :c limit 1", Map.of("c", courseId),
        (rs, i) -> new FirstTarget(rs.getString("course_code"), rs.getObject("institution_id", UUID.class)));
    return r.isEmpty() ? null : r.get(0);
  }

  public record InstitutionRow(String name, String nameBn, String shortName, String slug, String city, String logoUrl, String website) {}

  private static final RowMapper<InstitutionRow> INST = (rs, i) -> new InstitutionRow(rs.getString("name"), rs.getString("name_bn"), rs.getString("short_name"), rs.getString("slug"),
      rs.getString("city"), rs.getString("logo_url"), rs.getString("website"));

  public InstitutionRow institution(UUID id) {
    var r = content.query("select name, name_bn, short_name, slug, city, logo_url, website from institutions where id = :id", Map.of("id", id), INST);
    return r.isEmpty() ? null : r.get(0);
  }

  public InstitutionRow approvedInstitutionBySlug(String slug) {
    var r = content.query("select name, name_bn, short_name, slug, city, logo_url, website from institutions where slug = :s and status = 'approved'", Map.of("s", slug), INST);
    return r.isEmpty() ? null : r.get(0);
  }

  public SubjectRow approvedSubjectBySlug(String slug) {
    var r = content.query("select id, name, name_bn, slug from subject_masters where slug = :s and status = 'approved'", Map.of("s", slug),
        (rs, i) -> new SubjectRow(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("name_bn"), rs.getString("slug")));
    return r.isEmpty() ? null : r.get(0);
  }

  public SellerProfileRow verifiedSellerBySlug(String slug) {
    var r = core.query("select " + SELLER_COLS + " from seller_profiles where slug = :s and verification_status = 'verified'", Map.of("s", slug), SELLER);
    return r.isEmpty() ? null : r.get(0);
  }
}
