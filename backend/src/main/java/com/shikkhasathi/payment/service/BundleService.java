package com.shikkhasathi.payment.service;

import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.audit.service.AuditService;
import com.shikkhasathi.payment.dto.CommerceDtos.*;
import com.shikkhasathi.payment.repository.CheckoutContentRepository;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Course bundles (Midterm + Final …) built by sellers from their own published courses. */
@Service
public class BundleService {
  private static final SecureRandom RND = new SecureRandom();
  private final CheckoutContentRepository catalog;
  private final AuditService audit;
  private final BundleWriter writer;

  public BundleService(CheckoutContentRepository catalog, AuditService audit, BundleWriter writer) {
    this.catalog = catalog;
    this.audit = audit;
    this.writer = writer;
  }

  static String slugify(String s) {
    String v = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    return v.length() > 50 ? v.substring(0, 50) : v;
  }

  /** 5 random base-36 characters (like {@code Math.random().toString(36).slice(2, 7)}). */
  static String suffix() {
    var sb = new StringBuilder();
    for (int i = 0; i < 5; i++) sb.append(Character.forDigit(RND.nextInt(36), 36));
    return sb.toString();
  }

  public Created create(UUID sellerId, CreateBundleBody d) {
    var ids = new ArrayList<>(new LinkedHashSet<>(d.courseIds()));
    if (ids.size() < 2) throw ApiException.badRequest("বান্ডেলে অন্তত ২টি কোর্স দিন।");
    var cs = catalog.liveCourses(ids);
    if (cs.size() != ids.size() || cs.stream().anyMatch(c -> !c.sellerId().equals(sellerId))) throw ApiException.forbidden("শুধু নিজের কোর্স দিয়ে বান্ডেল বানানো যায়।");
    if (cs.stream().anyMatch(c -> !"published".equals(c.status()))) throw ApiException.badRequest("বান্ডেলের সব কোর্স প্রকাশিত হতে হবে।");
    long compare = cs.stream().mapToLong(CheckoutContentRepository.CourseRow::price).sum();
    if (d.price() >= compare) throw ApiException.badRequest("বান্ডেলের দাম আলাদা কোর্সের মোট দামের চেয়ে কম হতে হবে।");
    String base = slugify(d.title());
    String slug = (base.isEmpty() ? "bundle" : base) + "-" + suffix();
    UUID id = writer.insert(sellerId, d.title(), slug, d.description(), d.price(), compare, ids);
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("courseIds", ids);
    after.put("price", d.price());
    audit.log(AuditService.Entry.of(sellerId, "bundle.create", "bundles", id).after(after));
    return new Created(id, slug);
  }

  public SellerBundles list(UUID sellerId) {
    var bs = catalog.sellerBundles(sellerId);
    var counts = catalog.bundleItemCounts(bs.stream().map(CheckoutContentRepository.BundleRow::id).toList());
    return new SellerBundles(bs.stream().map(b -> new SellerBundle(b.id(), b.slug(), b.title(), b.price(), b.compareAtPrice(), counts.getOrDefault(b.id(), 0))).toList());
  }

  public Bundle get(String slug) {
    var b = catalog.publishedBundleBySlug(slug).orElseThrow(() -> ApiException.notFound("বান্ডেলটি পাওয়া যায়নি।"));
    var items = catalog.bundleCourses(b.id()).stream().map(c -> new BundleCourse(c.id(), c.slug(), c.title(), c.price())).toList();
    return new Bundle(b.id(), b.slug(), b.title(), b.description(), b.price(), b.compareAtPrice(), items);
  }

  /** Bundle + items in one CONTENT transaction. */
  @org.springframework.stereotype.Component
  public static class BundleWriter {
    private final CheckoutContentRepository catalog;

    public BundleWriter(CheckoutContentRepository catalog) {
      this.catalog = catalog;
    }

    @Transactional(transactionManager = "contentTransactionManager")
    public UUID insert(UUID sellerId, String title, String slug, String description, long price, long compare, List<UUID> ids) {
      return catalog.insertBundle(sellerId, title, slug, description, price, compare, ids);
    }
  }
}
