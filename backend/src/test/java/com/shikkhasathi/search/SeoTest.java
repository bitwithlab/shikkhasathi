package com.shikkhasathi.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.marketplace.service.SeoService;
import com.shikkhasathi.marketplace.service.SeoService.Head;
import com.shikkhasathi.marketplace.service.WebsiteShell;
import com.shikkhasathi.support.IntegrationTest;
import com.shikkhasathi.support.Resp;
import java.util.HashMap;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/** Port of seo.test.ts plus the HTTP routes (/sitemap.xml, /c|u|s|t/{slug}). */
class SeoTest extends IntegrationTest {
  static final String SHELL = "<html><head><title>x</title><meta name=\"description\" content=\"x\" /><link rel=\"canonical\" href=\"https://shikkhasathi.com/\" /><meta property=\"og:title\" content=\"x\" /><meta property=\"og:description\" content=\"x\" /><meta property=\"og:image\" content=\"https://shikkhasathi.com/og-image.jpg\" /></head><body><div id=\"root\"><!--ssr--></div></body></html>";
  private static final Pattern LD = Pattern.compile("<script type=\"application/ld\\+json\">(.*?)</script>");


  @Autowired SeoService seo;
  @Autowired WebsiteShell shell;

  /** GET outside /api/v1 (the SEO routes). */
  private Resp page(String path) {
    try {
      var res = mvc.perform(MockMvcRequestBuilders.get(path)).andReturn().getResponse();
      res.setCharacterEncoding("UTF-8");
      Map<String, String> h = new HashMap<>();
      for (String n : res.getHeaderNames()) h.put(n.toLowerCase(), res.getHeader(n));
      return new Resp(res.getStatus(), res.getContentAsString(), h);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private List<JsonNode> ld(String html) {
    List<JsonNode> out = new ArrayList<>();
    Matcher m = LD.matcher(html);
    while (m.find()) out.add(json.readTree(m.group(1)));
    return out;
  }

  @Test
  void coursePageTitlePriceCanonicalCourseAndBreadcrumbJsonLdCrawlableBody() {
    seed();
    Head h = seo.dynamicHead("/c/data-structures-midterm-crash-cse-2203");
    assertThat(h).isNotNull();
    String html = seo.renderHead(SHELL, h);
    assertThat(html).contains("<link rel=\"canonical\" href=\"https://shikkhasathi.com/c/data-structures-midterm-crash-cse-2203\" />");
    assertThat(html).containsPattern("<title>Data Structures[^<]*৳\\d+[^<]*শিক্ষাসাথী</title>");
    var blocks = ld(html);
    JsonNode course = blocks.get(0), crumbs = blocks.get(1);
    assertThat(course.get("@type").asString()).isEqualTo("Course");
    assertThat(course.get("courseCode").asString()).isEqualTo("CSE-2203");
    assertThat(course.at("/offers/priceCurrency").asString()).isEqualTo("BDT");
    assertThat(course.at("/aggregateRating/reviewCount").asInt()).isGreaterThan(0);
    assertThat(crumbs.get("@type").asString()).isEqualTo("BreadcrumbList");
    assertThat(html).contains("<h1>Data Structures").contains("অফিসিয়ালি যুক্ত নয়").doesNotContain("<!--ssr-->");
  }

  @Test
  void universitySubjectTeacherPagesUnknownDraftAndPrivateRoutesGetNoHead() {
    seed();
    assertThat(ld(seo.renderHead(SHELL, seo.dynamicHead("/u/metropolitan-university"))).get(0).get("@type").asString()).isEqualTo("CollegeOrUniversity");
    assertThat(seo.dynamicHead("/s/data-structures").title()).contains("Data Structures");
    assertThat(ld(seo.renderHead(SHELL, seo.dynamicHead("/t/rakibul-hasan"))).get(0).get("@type").asString()).isEqualTo("Person");
    assertThat(seo.dynamicHead("/t/maruf-rashid")).isNull(); // pending teacher
    assertThat(seo.dynamicHead("/c/does-not-exist")).isNull();
    assertThat(seo.dynamicHead("/home")).isNull();
    assertThat(seo.dynamicHead("/c/<script>")).isNull();
  }

  @Test
  void jsonLdCannotBreakOutOfTheScriptTag() {
    String html = seo.renderHead(SHELL, new Head("a", "b", "/c/x", null, List.of(Map.of("name", "</script><script>alert(1)</script>")), ""));
    assertThat(html).doesNotContain("</script><script>alert(1)");
  }

  @Test
  void sitemapListsPublishedCoursesUniversitiesSubjectsAndVerifiedTeachersOnly() {
    seed();
    var r = page("/sitemap.xml");
    r.expect(200);
    assertThat(r.header("content-type")).startsWith("application/xml");
    assertThat(r.header("cache-control")).isEqualTo("public, max-age=3600");
    String xml = r.body();
    assertThat(xml).contains("<loc>https://shikkhasathi.com/c/data-structures-midterm-crash-cse-2203</loc>");
    assertThat(xml).contains("/u/metropolitan-university").contains("/t/rakibul-hasan").doesNotContain("/t/maruf-rashid").doesNotContain("/home");
  }

  @Test
  void seoRoutesWrapTheWebsiteShellOr404WithoutIt() {
    seed();
    // no WEBSITE_INDEX_URL → 404 so the proxy falls back to the website
    assertThat(page("/c/data-structures-midterm-crash-cse-2203").status()).isEqualTo(404);
    List<String> fetched = new ArrayList<>();
    shell.override("http://website/index.html", req -> {
      fetched.add(req.url());
      return HttpTransport.Response.of(200, SHELL);
    });
    var page = page("/c/data-structures-midterm-crash-cse-2203");
    page.expect(200);
    assertThat(page.header("content-type")).startsWith("text/html");
    assertThat(page.header("cache-control")).isEqualTo("public, max-age=60");
    assertThat(page.body()).contains("<link rel=\"canonical\" href=\"https://shikkhasathi.com/c/data-structures-midterm-crash-cse-2203\" />").contains("application/ld+json");
    assertThat(page("/u/metropolitan-university/").body()).contains("CollegeOrUniversity");
    var unknown = page("/c/does-not-exist");
    unknown.expect(200);
    assertThat(unknown.header("cache-control")).isEqualTo("no-cache");
    assertThat(unknown.body()).isEqualTo(SHELL);
    assertThat(fetched).hasSize(1); // shell cached
    shell.override("http://website/index.html", req -> {
      throw new HttpTransport.HttpException("down", null);
    });
    assertThat(page("/t/rakibul-hasan").status()).isEqualTo(404);
  }
}
