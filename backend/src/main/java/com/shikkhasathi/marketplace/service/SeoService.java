package com.shikkhasathi.marketplace.service;

import com.shikkhasathi.common.testing.ResettableFake;
import com.shikkhasathi.marketplace.repository.MarketplaceRepository;
import com.shikkhasathi.marketplace.repository.MarketplaceRepository.SlugAt;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Request-time SEO for dynamic public pages (spec §5.4): course, university, subject, teacher storefront.
 * Injects title/description/canonical/Open Graph + JSON-LD and a small crawlable body into the website shell.
 * React replaces the body on mount (createRoot), so users never see it twice. Heads are cached 5 minutes.
 */
@Service
public class SeoService implements ResettableFake {
  public static final String SITE = "https://shikkhasathi.com";
  public static final Pattern DYNAMIC = Pattern.compile("^/(c|u|s|t)/([a-z0-9-]{1,120})/?$");
  private static final long TTL_MS = 5 * 60_000;
  private static final int MAX_CACHE = 2000;
  private static final List<String> STATIC = List.of("/", "/explore", "/universities", "/subjects", "/requests", "/about", "/contact", "/privacy", "/terms", "/refund");
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

  /** A rendered head: title, description, canonical path, OG image, JSON-LD blocks and a crawlable body. */
  public record Head(String title, String desc, String path, String image, List<Map<String, Object>> ld, String body) {}

  private record Cached(long at, Head head) {}

  private final MarketplaceRepository repo;
  private final JsonMapper json;
  private final Map<String, Cached> cache = new LinkedHashMap<>();

  public SeoService(MarketplaceRepository repo, JsonMapper json) {
    this.repo = repo;
    this.json = json;
  }

  @Override
  public synchronized void reset() {
    cache.clear();
  }

  // ---------------------------------------------------------------- helpers

  static String esc(String s) {
    return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
  }

  static String clip(String s, int n) {
    return s.length() > n ? s.substring(0, n - 1).stripTrailing() + "…" : s;
  }

  /** paisa → "499.00" */
  static String taka(long p) {
    return BigDecimal.valueOf(p).movePointLeft(2).setScale(2).toPlainString();
  }

  /** paisa → JS number text ("499", "125.5") */
  static String takaShort(long p) {
    return BigDecimal.valueOf(p).movePointLeft(2).stripTrailingZeros().toPlainString();
  }

  /** a JS-like number: integral doubles without ".0" */
  static Object num(double d) {
    return d == Math.rint(d) && Math.abs(d) < 1e15 ? (Object) (long) d : (Object) d;
  }

  private static boolean has(String s) {
    return s != null && !s.isEmpty();
  }

  private static Map<String, Object> obj(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) if (kv[i + 1] != null) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

  private static Map<String, Object> crumbs(List<String[]> items) {
    List<Object> list = new ArrayList<>();
    for (int i = 0; i < items.size(); i++) list.add(obj("@type", "ListItem", "position", i + 1, "name", items.get(i)[0], "item", SITE + items.get(i)[1]));
    return obj("@context", "https://schema.org", "@type", "BreadcrumbList", "itemListElement", list);
  }

  private static Map<String, Object> org() {
    return obj("@type", "Organization", "name", "ShikkhaSathi", "url", SITE);
  }

  // ---------------------------------------------------------------- pages

  Head coursePage(String slug) {
    var c = repo.publishedCourseBySlug(slug);
    if (c == null) return null;
    var t = repo.firstTarget(c.id());
    var sp = repo.sellerProfile(c.sellerId());
    var inst = t != null && t.institutionId() != null ? repo.institution(t.institutionId()) : null;
    String desc = clip(has(c.shortDescription()) ? c.shortDescription() : has(c.description()) ? c.description() : c.title(), 155);
    Map<String, Object> course = new LinkedHashMap<>();
    course.put("@context", "https://schema.org");
    course.put("@type", "Course");
    course.put("name", c.title());
    course.put("description", desc);
    course.put("url", SITE + "/c/" + c.slug());
    course.put("inLanguage", "en".equals(c.language()) ? "en" : "bn");
    if (t != null && has(t.code())) course.put("courseCode", t.code());
    course.put("provider", org());
    if (sp != null) course.put("instructor", obj("@type", "Person", "name", sp.displayName(), "url", SITE + "/t/" + sp.slug()));
    if (c.ratingCount() > 0) course.put("aggregateRating", obj("@type", "AggregateRating", "ratingValue", num(c.ratingAvg()), "reviewCount", c.ratingCount(), "bestRating", 5));
    course.put("offers", obj("@type", "Offer", "price", taka(c.price()), "priceCurrency", "BDT", "availability", "https://schema.org/InStock", "category", c.price() == 0 ? "Free" : "Paid", "url", SITE + "/c/" + c.slug()));
    course.put("hasCourseInstance", obj("@type", "CourseInstance", "courseMode", "online", "courseWorkload", c.totalDurationSec() != 0 ? "PT" + Math.max(1, Math.round(c.totalDurationSec() / 60.0)) + "M" : null));
    List<String[]> cr = new ArrayList<>();
    cr.add(new String[] {"শিক্ষাসাথী", "/"});
    if (inst != null) cr.add(new String[] {inst.shortName(), "/u/" + inst.slug()});
    cr.add(new String[] {c.title(), "/c/" + c.slug()});
    String price = c.price() == 0 ? "ফ্রি" : "৳" + takaShort(c.price());
    StringBuilder body = new StringBuilder("<main><h1>").append(esc(c.title())).append("</h1>");
    if (t != null && has(t.code())) body.append("<p>").append(esc(t.code())).append(inst != null ? " · " + esc(inst.name()) : "").append("</p>");
    body.append("<p>").append(esc(desc)).append("</p>");
    if (sp != null) body.append("<p>শিক্ষক: <a href=\"/t/").append(esc(sp.slug())).append("\">").append(esc(sp.displayName())).append("</a></p>");
    body.append("<p>").append(esc(price)).append("</p><p>সম্ভাব্য গুরুত্বপূর্ণ টপিক — কোনো বিশ্ববিদ্যালয়ের সঙ্গে অফিসিয়ালি যুক্ত নয়।</p></main>");
    return new Head(c.title() + " — " + price + " | শিক্ষাসাথী", desc, "/c/" + c.slug(), c.coverUrl(), List.of(course, crumbs(cr)), body.toString());
  }

  Head universityPage(String slug) {
    var u = repo.approvedInstitutionBySlug(slug);
    if (u == null) return null;
    String desc = u.name() + (has(u.nameBn()) ? " (" + u.nameBn() + ")" : "") + "-এর শিক্ষার্থীদের জন্য Midterm ও Final ক্র্যাশ কোর্স, কোর্স কোড অনুযায়ী।";
    List<String> alt = new ArrayList<>(List.of(u.shortName()));
    if (has(u.nameBn())) alt.add(u.nameBn());
    Map<String, Object> ld = obj("@context", "https://schema.org", "@type", "CollegeOrUniversity", "name", u.name(), "alternateName", alt, "sameAs", has(u.website()) ? u.website() : null,
        "address", has(u.city()) ? obj("@type", "PostalAddress", "addressLocality", u.city(), "addressCountry", "BD") : null);
    return new Head(u.shortName() + " — " + u.name() + " কোর্স | শিক্ষাসাথী", clip(desc, 155), "/u/" + u.slug(), u.logoUrl(),
        List.of(ld, crumbs(List.of(new String[] {"শিক্ষাসাথী", "/"}, new String[] {"ইউনিভার্সিটি", "/universities"}, new String[] {u.shortName(), "/u/" + u.slug()}))),
        "<main><h1>" + esc(u.name()) + "</h1><p>" + esc(desc) + "</p><p>কোনো বিশ্ববিদ্যালয়ের সঙ্গে অফিসিয়ালি যুক্ত নয়।</p></main>");
  }

  Head subjectPage(String slug) {
    var s = repo.approvedSubjectBySlug(slug);
    if (s == null) return null;
    String desc = s.name() + (has(s.nameBn()) ? " (" + s.nameBn() + ")" : "") + " — বিভিন্ন ইউনিভার্সিটির কোর্স কোড অনুযায়ী পরীক্ষার প্রস্তুতির কোর্স।";
    return new Head(s.name() + " কোর্স | শিক্ষাসাথী", clip(desc, 155), "/s/" + s.slug(), null,
        List.of(crumbs(List.of(new String[] {"শিক্ষাসাথী", "/"}, new String[] {"সাবজেক্ট", "/subjects"}, new String[] {s.name(), "/s/" + s.slug()}))),
        "<main><h1>" + esc(s.name()) + "</h1><p>" + esc(desc) + "</p></main>");
  }

  Head teacherPage(String slug) {
    var sp = repo.verifiedSellerBySlug(slug);
    if (sp == null) return null;
    String desc = clip(has(sp.headline()) ? sp.headline() + " — " + (sp.bio() == null ? "" : sp.bio()) : (sp.bio() != null ? sp.bio() : sp.displayName()), 155);
    Map<String, Object> ld = obj("@context", "https://schema.org", "@type", "Person", "name", sp.displayName(), "jobTitle", has(sp.headline()) ? sp.headline() : null, "url", SITE + "/t/" + sp.slug(), "worksFor", org());
    return new Head(sp.displayName() + " — শিক্ষক | শিক্ষাসাথী", desc, "/t/" + sp.slug(), null, List.of(ld), "<main><h1>" + esc(sp.displayName()) + "</h1><p>" + esc(desc) + "</p></main>");
  }

  /** Head for {@code /c|u|s|t/<slug>} (cached 5 min, also when missing); null for any other path or an unknown slug. */
  public Head dynamicHead(String url) {
    Matcher m = DYNAMIC.matcher(url);
    if (!m.matches()) return null;
    synchronized (this) {
      Cached hit = cache.get(url);
      if (hit != null && System.currentTimeMillis() - hit.at() < TTL_MS) return hit.head();
    }
    Function<String, Head> fn = switch (m.group(1)) {
      case "c" -> this::coursePage;
      case "u" -> this::universityPage;
      case "s" -> this::subjectPage;
      default -> this::teacherPage;
    };
    Head head;
    try {
      head = fn.apply(m.group(2));
    } catch (RuntimeException e) {
      head = null;
    }
    synchronized (this) {
      cache.put(url, new Cached(System.currentTimeMillis(), head));
      if (cache.size() > MAX_CACHE) cache.remove(cache.keySet().iterator().next());
    }
    return head;
  }

  private static String replaceFirst(String s, String regex, Function<String, String> repl) {
    Matcher m = Pattern.compile(regex).matcher(s);
    if (!m.find()) return s;
    return s.substring(0, m.start()) + repl.apply(m.group()) + s.substring(m.end());
  }

  private static String replaceLiteral(String s, String target, String repl) {
    int i = s.indexOf(target);
    return i < 0 ? s : s.substring(0, i) + repl + s.substring(i + target.length());
  }

  public String renderHead(String shell, Head h) {
    String canonical = SITE + h.path();
    String out = shell;
    out = replaceFirst(out, "<title>[^<]*</title>", t -> "<title>" + esc(h.title()) + "</title>");
    out = replaceFirst(out, "<meta name=\"description\" content=\"[^\"]*\"\\s*/?>", t -> "<meta name=\"description\" content=\"" + esc(h.desc()) + "\" />");
    out = replaceFirst(out, "<link rel=\"canonical\" href=\"[^\"]*\"\\s*/?>", t -> "<link rel=\"canonical\" href=\"" + canonical + "\" />");
    out = replaceFirst(out, "<meta property=\"og:title\" content=\"[^\"]*\"\\s*/?>", t -> "<meta property=\"og:title\" content=\"" + esc(h.title()) + "\" />");
    out = replaceFirst(out, "<meta property=\"og:description\" content=\"[^\"]*\"\\s*/?>", t -> "<meta property=\"og:description\" content=\"" + esc(h.desc()) + "\" />");
    out = replaceFirst(out, "<meta property=\"og:image\" content=\"[^\"]*\"\\s*/?>", t -> has(h.image()) ? "<meta property=\"og:image\" content=\"" + esc(h.image()) + "\" />" : t);
    List<String> scripts = new ArrayList<>();
    for (var j : h.ld()) scripts.add("<script type=\"application/ld+json\">" + json.writeValueAsString(j).replace("<", "\\u003c") + "</script>");
    out = replaceLiteral(out, "</head>", "<meta property=\"og:url\" content=\"" + canonical + "\" />\n" + String.join("\n", scripts) + "\n</head>");
    out = replaceLiteral(out, "<div id=\"root\"><!--ssr--></div>", "<div id=\"root\">" + h.body() + "</div>");
    return out;
  }

  // ---------------------------------------------------------------- sitemap

  private static String url(String path, Instant at, String pri) {
    return "  <url><loc>" + SITE + path + "</loc>" + (at != null ? "<lastmod>" + DAY.format(at) + "</lastmod>" : "") + "<priority>" + pri + "</priority></url>";
  }

  /** Live sitemap: static pages, published courses, approved universities and subjects, verified teachers. */
  public String sitemapXml() {
    List<String> rows = new ArrayList<>();
    for (String p : STATIC) rows.add(url(p, null, p.equals("/") ? "1.0" : "0.7"));
    for (SlugAt c : repo.sitemapCourses()) rows.add(url("/c/" + c.slug(), c.at(), "0.9"));
    for (SlugAt u : repo.sitemapInstitutions()) rows.add(url("/u/" + u.slug(), u.at(), "0.8"));
    for (SlugAt s : repo.sitemapSubjects()) rows.add(url("/s/" + s.slug(), s.at(), "0.6"));
    for (SlugAt t : repo.sitemapTeachers()) rows.add(url("/t/" + t.slug(), t.at(), "0.6"));
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n" + String.join("\n", rows) + "\n</urlset>\n";
  }
}
