package com.shikkhasathi.admin.service;

import com.shikkhasathi.admin.dto.OpsDtos.*;
import com.shikkhasathi.admin.repository.PayoutRepository;
import com.shikkhasathi.admin.repository.PayoutRepository.PayoutRow;
import com.shikkhasathi.common.ApiException;
import com.shikkhasathi.common.Cursor;
import com.shikkhasathi.common.audit.service.AuditService;
import com.shikkhasathi.config.JsonConfig;
import com.shikkhasathi.notification.email.EmailTemplate;
import com.shikkhasathi.notification.service.NotificationService;
import com.shikkhasathi.notification.service.NotificationTypes;
import com.shikkhasathi.notification.service.NotifyInput;
import com.shikkhasathi.payment.repository.LedgerRepository.NewEntry;
import com.shikkhasathi.payment.service.LedgerService;
import com.shikkhasathi.payment.service.Money;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

/** Seller payout requests and the admin approve → paid flow (ledger debit on paid). */
@Service
public class PayoutService {
  private static final Map<String, List<String>> ALLOWED = Map.of("approve", List.of("requested"), "reject", List.of("requested", "approved"), "paid", List.of("requested", "approved"));

  private final PayoutRepository payouts;
  private final LedgerService ledger;
  private final AuditService audit;
  private final NotificationService notifications;

  public PayoutService(PayoutRepository payouts, LedgerService ledger, AuditService audit, NotificationService notifications) {
    this.payouts = payouts;
    this.ledger = ledger;
    this.audit = audit;
    this.notifications = notifications;
  }

  static Payout toPayout(PayoutRow p) {
    return new Payout(p.id(), p.amount(), p.method(), p.accountMasked(), p.status(), p.reference(), p.note(), JsonConfig.iso(p.createdAt()), JsonConfig.iso(p.processedAt()));
  }

  public Items<Payout> mine(UUID sellerId) {
    return new Items<>(payouts.mine(sellerId, 100).stream().map(PayoutService::toPayout).toList());
  }

  public Payout request(UUID sellerId, long amount) {
    var sp = payouts.seller(sellerId).orElse(null);
    if (sp == null || sp.payoutMethod() == null || sp.payoutMethod().isEmpty() || sp.payoutAccountMasked() == null || sp.payoutAccountMasked().isEmpty())
      throw ApiException.badRequest("আগে প্রোফাইলে পেআউট নম্বর/অ্যাকাউন্ট যোগ করুন।");
    if (payouts.hasOpen(sellerId)) throw ApiException.conflict("একটি পেআউট অনুরোধ আগে থেকেই প্রক্রিয়াধীন আছে।");
    var b = ledger.balance(sellerId);
    if (amount < b.minPayout()) throw ApiException.badRequest("সর্বনিম্ন পেআউট ৳" + Money.taka(b.minPayout()) + "।");
    if (amount > b.available()) throw ApiException.badRequest("উত্তোলনযোগ্য ব্যালান্স ৳" + Money.taka(b.available()) + "।");
    var p = payouts.insert(sellerId, amount, sp.payoutMethod(), sp.payoutAccountMasked());
    audit.log(AuditService.Entry.of(sellerId, "payout.request", "payouts", p.id()).after(Map.of("amount", amount)));
    return toPayout(p);
  }

  private List<AdminPayout> toAdmin(List<PayoutRow> rows) {
    var sellerIds = rows.stream().map(PayoutRow::sellerId).toList();
    var sellers = payouts.sellers(sellerIds);
    var balances = ledger.balances(sellerIds);
    return rows.stream().map(p -> {
      var sp = sellers.get(p.sellerId());
      return new AdminPayout(toPayout(p), p.sellerId(), sp != null ? sp.displayName() : "", sp != null ? sp.slug() : null, balances.get(p.sellerId()).available());
    }).toList();
  }

  public Page<AdminPayout> adminList(String status, String cursor, int limit) {
    var cur = Cursor.decode(cursor);
    var rows = payouts.byStatus(status, cur == null ? null : Instant.parse(cur.sortString()), limit + 1);
    var pg = Cursor.page(rows, limit, r -> new Cursor.Value(JsonConfig.iso(r.createdAt()), r.id().toString()));
    return new Page<>(toAdmin(pg.items()), pg.nextCursor());
  }

  public AdminPayout decide(UUID actorId, UUID id, DecidePayoutBody d) {
    var p = payouts.byId(id).orElseThrow(ApiException::notFound);
    if (!ALLOWED.get(d.decision()).contains(p.status())) throw ApiException.conflict("এই অবস্থায় এই সিদ্ধান্ত নেওয়া যায় না।");
    if ("paid".equals(d.decision()) && (d.reference() == null || d.reference().isEmpty())) throw ApiException.badRequest("পাঠানোর রেফারেন্স (bKash TrxID / ব্যাংক রেফারেন্স) দিন।");
    String status = "approve".equals(d.decision()) ? "approved" : "reject".equals(d.decision()) ? "rejected" : "paid";
    var u = payouts.transition(id, p.status(), status, d.reference() != null ? d.reference() : p.reference(), d.note() != null ? d.note() : p.note(), actorId)
        .orElseThrow(() -> ApiException.conflict("অন্য কেউ এটি আপডেট করেছেন — রিফ্রেশ করুন।"));
    if ("paid".equals(status)) {
      ledger.append(p.sellerId(), new NewEntry("payout", -p.amount(), null, null, p.id(), null, "Payout " + d.reference(), Instant.now(), "payout:" + p.id()));
      Map<String, Object> data = new LinkedHashMap<>();
      data.put("amount", p.amount());
      data.put("method", p.method());
      data.put("account", p.accountMasked() != null ? p.accountMasked() : "");
      data.put("reference", d.reference() != null ? d.reference() : "");
      notifications.notify(NotifyInput.of(p.sellerId(), NotificationTypes.SELLER_PAYOUT_PAID, p.id().toString(), "পেআউট পাঠানো হয়েছে",
              "৳" + Money.taka(p.amount()) + " · " + p.method() + " " + (p.accountMasked() != null ? p.accountMasked() : ""))
          .withLink("/teach/earnings").withEmail(EmailTemplate.PAYOUT_PAID, data));
    } else if ("rejected".equals(status)) {
      notifications.notify(NotifyInput.of(p.sellerId(), NotificationTypes.SELLER_PAYOUT_PAID, "rejected:" + p.id(), "পেআউট অনুরোধ বাতিল হয়েছে",
          d.note() != null ? d.note() : "বিস্তারিত জানতে সাপোর্টে যোগাযোগ করুন।").withLink("/teach/earnings"));
    }
    Map<String, Object> after = new LinkedHashMap<>();
    after.put("status", status);
    if (d.reference() != null) after.put("reference", d.reference());
    audit.log(AuditService.Entry.of(actorId, "payout." + d.decision(), "payouts", id).before(Map.of("status", p.status())).after(after));
    return toAdmin(List.of(u)).get(0);
  }
}
