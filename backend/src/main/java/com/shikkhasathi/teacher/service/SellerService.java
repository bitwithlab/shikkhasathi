package com.shikkhasathi.teacher.service;

import com.shikkhasathi.catalog.service.AcademicLookup;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Crypto;
import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.common.ErrorCode;
import com.shikkhasathi.common.audit.service.AuditService;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.course.repository.CourseAuthoringRepository;
import com.shikkhasathi.file.service.Bucket;
import com.shikkhasathi.file.service.StorageService;
import com.shikkhasathi.integration.service.IntegrationService;
import com.shikkhasathi.notification.email.EmailService;
import com.shikkhasathi.notification.email.EmailTemplate;
import com.shikkhasathi.notification.service.NotificationService;
import com.shikkhasathi.notification.service.NotificationTypes;
import com.shikkhasathi.notification.service.NotifyInput;
import com.shikkhasathi.security.RateLimiter;
import com.shikkhasathi.teacher.dto.SellerDtos.*;
import com.shikkhasathi.teacher.entity.SellerProfile;
import com.shikkhasathi.teacher.repository.SellerCoreQueries;
import com.shikkhasathi.teacher.repository.SellerProfileRepository;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Teacher application, university-email verification, storefront and the admin seller verification queue. */
@Service
public class SellerService {
  private static final Pattern PERSONAL_MAIL = Pattern.compile("^(gmail|yahoo|outlook|hotmail|icloud|proton)\\.", Pattern.CASE_INSENSITIVE);
  private static final String SLUG_TAKEN = "এই লিংক (slug) অন্য কেউ ব্যবহার করছেন।";

  private final SellerProfileRepository profiles;
  private final SellerCoreQueries core;
  private final CourseAuthoringRepository content;
  private final AcademicLookup academic;
  private final Crypto crypto;
  private final AuditService audit;
  private final NotificationService notifications;
  private final EmailService email;
  private final StorageService storage;
  private final RateLimiter limiter;
  private final IntegrationService integrations;
  private final JsonMapper json;

  public SellerService(SellerProfileRepository profiles, SellerCoreQueries core, CourseAuthoringRepository content, AcademicLookup academic, Crypto crypto, AuditService audit,
                       NotificationService notifications, EmailService email, StorageService storage, RateLimiter limiter, IntegrationService integrations, JsonMapper json) {
    this.profiles = profiles;
    this.core = core;
    this.content = content;
    this.academic = academic;
    this.crypto = crypto;
    this.audit = audit;
    this.notifications = notifications;
    this.email = email;
    this.storage = storage;
    this.limiter = limiter;
    this.integrations = integrations;
    this.json = json;
  }

  static String maskAccount(String v) {
    String d = v.replaceAll("\\s", "");
    if (d.length() <= 4) return "••••";
    return "•".repeat(Math.min(6, d.length() - 4)) + d.substring(d.length() - 4);
  }

  public Optional<SellerProfile> profileOf(UUID userId) {
    return profiles.findById(userId);
  }

  private static SellerProfileDto toProfile(SellerProfile p, AcademicLookup.Names names) {
    JsonNode ev = p.getVerificationData();
    var inst = p.getInstitutionId() == null ? null : names.institution().get(p.getInstitutionId());
    return new SellerProfileDto(p.getUserId(), p.getSlug(), p.getDisplayName(), p.getHeadline(), p.getBio(), p.getCoverUrl(), p.getInstitutionId(),
        inst == null ? null : inst.name(), p.getVerificationStatus(), p.getVerificationMethod(), p.getVerificationNote(),
        ev != null && ev.isObject() && ev.path("universityEmailVerified").isBoolean() && ev.get("universityEmailVerified").asBoolean(),
        p.getPayoutMethod(), p.getPayoutAccountMasked(), p.getCommissionRate(), JsonConfig.iso(p.getVerifiedAt()));
  }

  private SellerProfileDto toProfile(SellerProfile p) {
    var names = academic.lookup(p.getInstitutionId() == null ? List.of() : List.of(p.getInstitutionId()), List.of(), List.of());
    return toProfile(p, names);
  }

  private SellerProfile reload(UUID userId) {
    return profiles.findById(userId).orElseThrow(ApiException::notFound);
  }

  // ---------------------------------------------------------------- application

  public ProfileEnvelope getProfile(UUID userId) {
    return new ProfileEnvelope(profileOf(userId).map(this::toProfile).orElse(null));
  }

  private static boolean empty(String s) {
    return s == null || s.isEmpty();
  }

  private static String text(JsonNode n, String k) {
    JsonNode v = n == null ? null : n.get(k);
    return v == null || v.isNull() ? null : v.asString();
  }

  public SellerProfileDto apply(UUID userId, ApplyBody b) {
    var clash = profiles.findBySlug(b.slug());
    if (clash.isPresent() && !clash.get().getUserId().equals(userId)) throw ApiException.conflict(SLUG_TAKEN);
    Evidence e = b.evidence();
    if (b.method().equals("university_email") && empty(e.universityEmail())) throw ApiException.badRequest("বিশ্ববিদ্যালয়ের ইমেইল দিন।");
    if (b.method().equals("id_card") && empty(e.idCardKey())) throw ApiException.badRequest("আইডি কার্ডের ছবি আপলোড করুন।");
    if (b.method().equals("linkedin") && empty(e.linkedinUrl())) throw ApiException.badRequest("LinkedIn প্রোফাইল লিংক দিন।");
    if (b.method().equals("reference") && (empty(e.referenceName()) || empty(e.referenceContact())))
      throw ApiException.badRequest("রেফারেন্সের নাম ও যোগাযোগ দিন।");
    if (b.institutionId() != null && !content.institutionExists(b.institutionId())) throw ApiException.badRequest("প্রতিষ্ঠানটি তালিকায় নেই।");

    var existing = profileOf(userId);
    JsonNode prevEv = existing.map(SellerProfile::getVerificationData).orElse(null);
    boolean prevVerifiedEmail = prevEv != null && prevEv.path("universityEmailVerified").isBoolean() && prevEv.get("universityEmailVerified").asBoolean();
    ObjectNode evidence = json.createObjectNode();
    if (e.universityEmail() != null) evidence.put("universityEmail", e.universityEmail());
    if (e.idCardKey() != null) evidence.put("idCardKey", e.idCardKey());
    if (e.linkedinUrl() != null) evidence.put("linkedinUrl", e.linkedinUrl());
    if (e.referenceName() != null) evidence.put("referenceName", e.referenceName());
    if (e.referenceContact() != null) evidence.put("referenceContact", e.referenceContact());
    evidence.put("universityEmailVerified", prevVerifiedEmail && Objects.equals(text(prevEv, "universityEmail"), e.universityEmail()));
    String status = existing.map(p -> "verified".equals(p.getVerificationStatus()) ? "verified" : "pending").orElse("pending");

    SellerProfile p = existing.orElseGet(() -> new SellerProfile().setUserId(userId));
    p.setSlug(b.slug())
        .setDisplayName(b.displayName())
        .setHeadline(b.headline())
        .setBio(b.bio())
        .setInstitutionId(b.institutionId())
        .setVerificationStatus(status)
        .setVerificationMethod(b.method())
        .setVerificationData(evidence)
        .setPayoutMethod(b.payoutMethod())
        .setPayoutAccountEnc(crypto.encrypt(b.payoutAccount()))
        .setPayoutAccountMasked(maskAccount(b.payoutAccount()));
    if (existing.isPresent()) p.setUpdatedAt(Instant.now());
    profiles.saveAndFlush(p);
    core.addSellerRole(userId);
    audit.log(AuditService.Entry.of(userId, existing.isPresent() ? "seller.application_update" : "seller.apply", "seller_profiles", userId)
        .after(Map.of("method", b.method(), "status", status)));
    return toProfile(reload(userId));
  }

  public void requestUniversityEmailCode(UUID userId, String address) {
    int at = address.indexOf('@');
    String domain = at >= 0 ? address.substring(at + 1).split("@", -1)[0] : "";
    if (PERSONAL_MAIL.matcher(domain).find()) throw ApiException.badRequest("বিশ্ববিদ্যালয়ের অফিসিয়াল ইমেইল দিন (ব্যক্তিগত Gmail নয়)।");
    var lim = limiter.hit("seller-email:" + userId, 5, 3600);
    if (!lim.allowed()) throw new ApiException(ErrorCode.RATE_LIMITED, null, Map.of("retryAfterSec", lim.retryAfterSec()));
    String code = Crypto.randomDigits(6);
    String target = "seller:" + userId + ":" + address;
    core.insertOtp(target, crypto.hmac(target + ":" + code), "seller_email", Instant.now().plusSeconds(600));
    email.send(address, null, EmailTemplate.EMAIL_OTP, Map.of("code", code), "bn", "seller:" + userId, null);
  }

  public SellerProfileDto confirmUniversityEmail(UUID userId, String address, String code) {
    String target = "seller:" + userId + ":" + address;
    var otp = core.latestOpenOtp(target, "seller_email").orElse(null);
    if (otp == null || otp.expiresAt().isBefore(Instant.now()) || otp.attempts() >= 5) throw ApiException.badRequest("কোডের মেয়াদ শেষ। নতুন কোড নিন।");
    if (!Crypto.safeEqual(otp.codeHash(), crypto.hmac(target + ":" + code))) {
      core.setOtpAttempts(otp.id(), otp.attempts() + 1);
      throw ApiException.badRequest("কোডটি সঠিক নয়।");
    }
    core.consumeOtp(otp.id());
    SellerProfile p = profileOf(userId).orElseThrow(() -> ApiException.badRequest("আগে আবেদন ফর্ম পূরণ করুন।"));
    ObjectNode ev = p.getVerificationData() != null && p.getVerificationData().isObject() ? ((ObjectNode) p.getVerificationData()).deepCopy() : json.createObjectNode();
    ev.put("universityEmail", address);
    ev.put("universityEmailVerified", true);
    p.setVerificationData(ev).setVerificationMethod("university_email");
    profiles.saveAndFlush(p);
    return toProfile(reload(userId));
  }

  public SellerProfileDto updateStorefront(UUID userId, StorefrontPatch b, JsonNode raw) {
    SellerProfile p = profileOf(userId).orElseThrow(ApiException::notFound);
    if (b.slug() != null && !b.slug().isEmpty() && !b.slug().equals(p.getSlug()) && profiles.findBySlug(b.slug()).isPresent()) throw ApiException.conflict(SLUG_TAKEN);
    if (raw.has("slug")) p.setSlug(b.slug());
    if (raw.has("displayName")) p.setDisplayName(b.displayName());
    if (raw.has("headline")) p.setHeadline(b.headline());
    if (raw.has("bio")) p.setBio(b.bio());
    if (raw.has("coverUrl")) p.setCoverUrl(b.coverUrl());
    p.setUpdatedAt(Instant.now());
    profiles.saveAndFlush(p);
    if (b.displayName() != null && !b.displayName().isEmpty()) content.updateSellerNameOnCourses(userId, b.displayName());
    return toProfile(reload(userId));
  }

  // ---------------------------------------------------------------- admin verification

  /** Batched: one users query, one course-count query and one academic lookup for the whole page. */
  private List<AdminSellerItem> adminItems(List<SellerProfile> ps) {
    var ids = ps.stream().map(SellerProfile::getUserId).toList();
    var contacts = core.contacts(ids);
    var counts = content.countBySellers(ids);
    var names = academic.lookup(ps.stream().map(SellerProfile::getInstitutionId).filter(Objects::nonNull).toList(), List.of(), List.of());
    List<AdminSellerItem> out = new ArrayList<>();
    for (SellerProfile p : ps) {
      var u = contacts.get(p.getUserId());
      JsonNode ev = p.getVerificationData() == null || p.getVerificationData().isNull() ? null : p.getVerificationData();
      String idKey = ev != null && ev.path("idCardKey").isString() ? ev.get("idCardKey").asString() : null;
      out.add(AdminSellerItem.of(toProfile(p, names), u == null ? "" : u.name(), u == null ? null : u.email(), u == null ? null : u.phone(), ev,
          idKey != null ? storage.signedDownloadUrl(Bucket.VERIFICATION_DOCS, idKey, 600, null) : null,
          JsonConfig.iso(p.getCreatedAt()), counts.getOrDefault(p.getUserId(), 0)));
    }
    return out;
  }

  public AdminSellerPage adminListSellers(String status, String cursor, int limit) {
    var cur = Cursor.decode(cursor);
    Instant afterTs = null;
    UUID afterId = null;
    if (cur != null) {
      try {
        afterTs = Instant.parse(cur.sortString());
        afterId = UUID.fromString(cur.id());
      } catch (RuntimeException e) {
        throw ApiException.badRequest("অবৈধ cursor");
      }
    }
    List<UUID> ids = core.profileIdsByStatus(status, afterTs, afterId, limit + 1);
    Map<UUID, SellerProfile> byId = profiles.findAllById(ids).stream().collect(Collectors.toMap(SellerProfile::getUserId, Function.identity()));
    List<SellerProfile> rows = ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    var pg = Cursor.page(rows, limit, r -> new Cursor.Value(JsonConfig.iso(r.getUpdatedAt()), r.getUserId().toString()));
    return new AdminSellerPage(adminItems(pg.items()), pg.nextCursor());
  }

  public AdminSellerItem adminDecideSeller(UUID actorId, UUID userId, SellerDecision d) {
    SellerProfile p = profileOf(userId).orElseThrow(ApiException::notFound);
    String before = p.getVerificationStatus();
    boolean verified = d.decision().equals("verified");
    p.setVerificationStatus(d.decision()).setVerificationNote(d.note()).setVerifiedAt(verified ? Instant.now() : null);
    if (d.commissionRate() != null) p.setCommissionRate(d.commissionRate());
    p.setUpdatedAt(Instant.now());
    profiles.saveAndFlush(p);
    content.updateSellerVerifiedOnCourses(userId, verified);
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("status", d.decision());
    if (d.note() != null) after.put("note", d.note());
    audit.log(AuditService.Entry.of(actorId, "seller." + d.decision(), "seller_profiles", userId).before(Map.of("status", before)).after(after));
    notifications.notify(NotifyInput.of(userId, NotificationTypes.SELLER_APPLICATION_RESULT, d.decision() + ":" + System.currentTimeMillis(),
            verified ? "আপনি এখন ভেরিফায়েড শিক্ষক ✓" : "শিক্ষক আবেদন আবার দেখুন",
            verified ? "কোর্স বানিয়ে রিভিউয়ের জন্য পাঠাতে পারবেন।" : (d.note() != null ? d.note() : "আবেদনে কিছু তথ্য ঠিক নেই।"))
        .withLink(verified ? "/teach" : "/teach/verification")
        .withEmail(EmailTemplate.SELLER_APPLICATION_RESULT, Map.of("status", d.decision(), "note", d.note() != null ? d.note() : "")));
    return adminItems(List.of(reload(userId))).get(0);
  }

  // ---------------------------------------------------------------- used by payouts / orders

  /** Decrypted payout account of a seller (null when none). */
  public String payoutAccount(UUID sellerId) {
    return profileOf(sellerId).map(SellerProfile::getPayoutAccountEnc).map(crypto::decrypt).orElse(null);
  }

  /** The seller's commission rate, else the business default ({@code commission_percent}, 20). */
  public int defaultCommission(UUID sellerId) {
    var rate = profileOf(sellerId).map(SellerProfile::getCommissionRate).orElse(null);
    return rate != null ? rate : (int) integrations.businessNumber("commission_percent", 20);
  }
}
