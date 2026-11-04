package com.shikkhasathi.notification.email;

import com.shikkhasathi.config.AppProperties;
import com.shikkhasathi.notification.email.EmailMessage.Rendered;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Branded ShikkhaSathi email templates in bn + en with a plain-text alternative — a line-for-line port of the
 * previous API's {@code templates.ts} (same subjects, HTML and text). User data is HTML-escaped.
 */
@Component
public class EmailTemplates {
  private final String appUrl;

  @Autowired
  public EmailTemplates(AppProperties props) {
    this(props.appUrl());
  }

  public EmailTemplates(String appUrl) {
    this.appUrl = appUrl;
  }

  private record Cta(String label, String url) {}

  private record Block(String heading, List<String> paragraphs, String code, Cta cta, String footnote) {}

  private record Built(String subject, Block block) {}

  /** Render {@code template} with {@code data} (values: strings, numbers or null) in {@code locale} ("bn" default, "en"). */
  public Rendered render(EmailTemplate template, Map<String, ?> data, String locale) {
    String l = "en".equals(locale) ? "en" : "bn";
    Map<String, ?> d = data == null ? Map.of() : data;
    Built b = build(template, d, l);
    String[] ht = layout(l, b.block());
    return new Rendered(b.subject(), ht[0], ht[1]);
  }

  /** Render in Bangla. */
  public Rendered render(EmailTemplate template, Map<String, ?> data) {
    return render(template, data, "bn");
  }

  // ---------------------------------------------------------------- helpers (JS semantics)

  /** JS {@code String(v ?? '')}: integral numbers without ".0". */
  static String str(Object v) {
    if (v == null) return "";
    if (v instanceof Double || v instanceof Float) {
      double x = ((Number) v).doubleValue();
      if (x == Math.rint(x) && !Double.isInfinite(x) && Math.abs(x) < 1e15) return Long.toString((long) x);
      return BigDecimal.valueOf(x).stripTrailingZeros().toPlainString();
    }
    if (v instanceof BigDecimal bd) return bd.stripTrailingZeros().toPlainString();
    return String.valueOf(v);
  }

  static String esc(Object v) {
    return str(v).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }

  /** JS truthiness for template data. */
  static boolean truthy(Object v) {
    if (v == null) return false;
    if (v instanceof String s) return !s.isEmpty();
    if (v instanceof Boolean b) return b;
    if (v instanceof Number n) return n.doubleValue() != 0 && !Double.isNaN(n.doubleValue());
    return true;
  }

  /** {@code ৳} + paisa/100 formatted like {@code toLocaleString('en-IN')} (Indian grouping, ≤ 3 decimals). */
  static String taka(Object paisa) {
    BigDecimal p;
    try {
      p = paisa == null ? BigDecimal.ZERO : paisa instanceof Number n ? new BigDecimal(n.toString()) : str(paisa).isBlank() ? BigDecimal.ZERO : new BigDecimal(str(paisa).trim());
    } catch (NumberFormatException e) {
      return "৳NaN";
    }
    BigDecimal v = p.divide(BigDecimal.valueOf(100)).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
    boolean neg = v.signum() < 0;
    String plain = v.abs().toPlainString();
    String intPart = plain.contains(".") ? plain.substring(0, plain.indexOf('.')) : plain;
    String frac = plain.contains(".") ? plain.substring(plain.indexOf('.')) : "";
    return "৳" + (neg ? "-" : "") + indianGroup(intPart) + frac;
  }

  /** 1234567 → 12,34,567 */
  public static String indianGroup(String digits) {
    if (digits.length() <= 3) return digits;
    String last3 = digits.substring(digits.length() - 3);
    String rest = digits.substring(0, digits.length() - 3);
    StringBuilder sb = new StringBuilder();
    int first = rest.length() % 2;
    if (first > 0) sb.append(rest, 0, first);
    for (int i = first; i < rest.length(); i += 2) {
      if (!sb.isEmpty()) sb.append(',');
      sb.append(rest, i, i + 2);
    }
    return sb.append(',').append(last3).toString();
  }

  private static String t(String l, String bn, String en) {
    return "bn".equals(l) ? bn : en;
  }

  private static boolean notEmpty(String s) {
    return s != null && !s.isEmpty();
  }

  private static String strip(String s) {
    return s.replaceAll("<[^>]+>", "");
  }

  // ---------------------------------------------------------------- layout

  private String[] layout(String locale, Block b) {
    boolean bn = "bn".equals(locale);
    String brand = bn ? "শিক্ষাসাথী" : "ShikkhaSathi";
    String tagline = bn ? "আপনার ক্লাস, আপনার কোর্স, আপনার পরীক্ষার প্রস্তুতি — এক জায়গায়।" : "Your classes. Your courses. Your exam prep. All in one place.";
    String disclaimer = bn ? "কোনো বিশ্ববিদ্যালয়ের সঙ্গে অফিসিয়ালি যুক্ত নয়।" : "Not officially affiliated with any university.";
    String settingsUrl = appUrl + "/profile#notifications";
    StringBuilder paras = new StringBuilder();
    for (String p : b.paragraphs()) paras.append("<p style=\"margin:0 0 12px;font-size:15px;color:#334155\">").append(p).append("</p>");
    String html = "<!doctype html><html lang=\"" + locale + "\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"><title>" + esc(b.heading()) + "</title></head>\n"
        + "<body style=\"margin:0;background:#F8FAFC;font-family:'Hind Siliguri',Inter,Arial,sans-serif;color:#0F172A;line-height:1.7\">\n"
        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#F8FAFC;padding:24px 12px\"><tr><td align=\"center\">\n"
        + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"max-width:560px;background:#FFFFFF;border:1px solid #E2E8F0;border-radius:16px\">\n"
        + "<tr><td style=\"padding:20px 28px;border-bottom:1px solid #E2E8F0\">\n"
        + "<a href=\"" + appUrl + "\" style=\"text-decoration:none;color:#4F46E5;font-weight:700;font-size:20px\">" + brand + "</a>\n"
        + "</td></tr>\n"
        + "<tr><td style=\"padding:28px\">\n"
        + "<h1 style=\"margin:0 0 12px;font-size:20px;line-height:1.5;color:#0F172A\">" + esc(b.heading()) + "</h1>\n"
        + paras + "\n"
        + (notEmpty(b.code()) ? "<p style=\"margin:20px 0;font-family:Inter,Arial,sans-serif;font-size:30px;letter-spacing:8px;font-weight:700;color:#4F46E5\">" + esc(b.code()) + "</p>" : "") + "\n"
        + (b.cta() != null ? "<p style=\"margin:24px 0 8px\"><a href=\"" + esc(b.cta().url()) + "\" style=\"display:inline-block;background:#4F46E5;color:#FFFFFF;text-decoration:none;padding:12px 22px;border-radius:12px;font-weight:600\">" + esc(b.cta().label()) + "</a></p>" : "") + "\n"
        + (notEmpty(b.footnote()) ? "<p style=\"margin:16px 0 0;font-size:13px;color:#64748B\">" + b.footnote() + "</p>" : "") + "\n"
        + "</td></tr>\n"
        + "<tr><td style=\"padding:16px 28px;border-top:1px solid #E2E8F0;font-size:12px;color:#64748B\">\n"
        + tagline + "<br>" + disclaimer + " · <a href=\"" + settingsUrl + "\" style=\"color:#64748B\">" + (bn ? "ইমেইল সেটিংস" : "Email settings") + "</a>\n"
        + "</td></tr></table></td></tr></table></body></html>";
    List<String> text = new ArrayList<>(List.of(brand, "", b.heading(), ""));
    b.paragraphs().stream().map(EmailTemplates::strip).forEach(text::add);
    if (notEmpty(b.code())) text.addAll(List.of("", b.code()));
    if (b.cta() != null) text.addAll(List.of("", b.cta().label() + ": " + b.cta().url()));
    if (notEmpty(b.footnote())) text.addAll(List.of("", strip(b.footnote())));
    text.addAll(List.of("", "—", tagline, disclaimer));
    return new String[] {html, String.join("\n", text)};
  }

  // ---------------------------------------------------------------- templates

  private Built build(EmailTemplate template, Map<String, ?> d, String l) {
    return switch (template) {
      case WELCOME -> {
        List<String> ps = new ArrayList<>();
        ps.add(t(l, "আপনার অ্যাকাউন্ট তৈরি হয়েছে। Google Classroom সংযুক্ত করলে সব অ্যাসাইনমেন্ট আর নোটিশ এক জায়গায় দেখতে পাবেন।", "Your account is ready. Connect Google Classroom to see all assignments and notices in one place."));
        if (truthy(d.get("code"))) ps.add(t(l, "ইমেইল যাচাই করতে নিচের কোডটি দিন (১০ মিনিট বৈধ):", "Use this code to verify your email (valid for 10 minutes):"));
        yield new Built(t(l, "শিক্ষাসাথীতে স্বাগতম!", "Welcome to ShikkhaSathi!"),
            new Block(t(l, "স্বাগতম, " + esc(d.get("name")) + "!", "Welcome, " + esc(d.get("name")) + "!"), ps,
                truthy(d.get("code")) ? str(d.get("code")) : null,
                new Cta(t(l, "ড্যাশবোর্ডে যান", "Open dashboard"), appUrl + "/home"), null));
      }
      case EMAIL_OTP -> new Built(t(l, "আপনার শিক্ষাসাথী যাচাই কোড", "Your ShikkhaSathi verification code"),
          new Block(t(l, "যাচাই কোড", "Verification code"),
              List.of(t(l, "নিচের কোডটি ১০ মিনিটের মধ্যে ব্যবহার করুন। কাউকে এই কোড জানাবেন না।", "Use this code within 10 minutes. Never share it with anyone.")),
              str(d.get("code")), null,
              t(l, "আপনি অনুরোধ না করে থাকলে এই ইমেইলটি উপেক্ষা করুন।", "If you didn't request this, you can ignore this email.")));
      case PASSWORD_RESET -> new Built(t(l, "পাসওয়ার্ড রিসেট কোড", "Password reset code"),
          new Block(t(l, "পাসওয়ার্ড রিসেট করুন", "Reset your password"),
              List.of(t(l, "পাসওয়ার্ড বদলাতে নিচের কোডটি দিন (১০ মিনিট বৈধ)।", "Enter this code to set a new password (valid for 10 minutes).")),
              str(d.get("code")), null,
              t(l, "অনুরোধ আপনি না করলে পাসওয়ার্ড অপরিবর্তিত থাকবে।", "If this wasn't you, your password stays unchanged.")));
      case ORDER_PAID -> new Built(t(l, "পেমেন্ট সফল — ইনভয়েস " + esc(d.get("invoiceNumber")), "Payment received — invoice " + esc(d.get("invoiceNumber"))),
          new Block(t(l, "কোর্স আনলক হয়েছে", "Your course is unlocked"),
              List.of(
                  t(l, "<strong>" + esc(d.get("items")) + "</strong> — মোট " + taka(d.get("total")) + " পরিশোধ হয়েছে।", "<strong>" + esc(d.get("items")) + "</strong> — " + taka(d.get("total")) + " paid."),
                  t(l, "ইনভয়েস নম্বর: " + esc(d.get("invoiceNumber")), "Invoice number: " + esc(d.get("invoiceNumber")))),
              null,
              new Cta(t(l, "শেখা শুরু করুন", "Start learning"), appUrl + "/my-courses"),
              truthy(d.get("invoiceUrl"))
                  ? t(l, "ইনভয়েস ডাউনলোড: <a href=\"" + esc(d.get("invoiceUrl")) + "\">PDF</a>", "Download invoice: <a href=\"" + esc(d.get("invoiceUrl")) + "\">PDF</a>")
                  : null));
      case REFUND -> {
        boolean rejected = "rejected".equals(d.get("status"));
        yield new Built(t(l, "রিফান্ড আপডেট", "Refund update"),
            new Block(rejected ? t(l, "রিফান্ড অনুরোধ গ্রহণ হয়নি", "Refund request declined") : t(l, "রিফান্ড অনুমোদিত হয়েছে", "Refund approved"),
                List.of(rejected
                    ? t(l, "দুঃখিত, অর্ডার " + esc(d.get("orderId")) + "-এর রিফান্ড দেওয়া সম্ভব হয়নি। " + esc(d.get("note")), "Sorry, the refund for order " + esc(d.get("orderId")) + " was declined. " + esc(d.get("note")))
                    : t(l, taka(d.get("amount")) + " আপনার পেমেন্ট মাধ্যমে ফেরত যাবে (৩–৭ কার্যদিবস)।", taka(d.get("amount")) + " will be returned to your payment method (3–7 business days).")),
                null, null, null));
      }
      case SELLER_APPLICATION_RESULT -> {
        boolean verified = "verified".equals(d.get("status"));
        yield new Built(t(l, "শিক্ষক আবেদনের ফলাফল", "Your teacher application"),
            new Block(verified ? t(l, "অভিনন্দন! আপনি এখন ভেরিফায়েড শিক্ষক", "Congratulations — you're verified") : t(l, "আবেদনটি আবার দেখুন", "Please review your application"),
                List.of(verified ? t(l, "এখন কোর্স বানিয়ে রিভিউয়ের জন্য পাঠাতে পারবেন।", "You can now create courses and submit them for review.") : t(l, "কারণ: " + esc(d.get("note")), "Reason: " + esc(d.get("note")))),
                null, new Cta("Teacher Studio", appUrl + "/teach"), null));
      }
      case COURSE_REVIEW_DECISION -> {
        Object decision = d.get("decision");
        String heading = "published".equals(decision) ? t(l, "আপনার কোর্স প্রকাশিত হয়েছে", "Your course is live")
            : "changes_requested".equals(decision) ? t(l, "কিছু পরিবর্তন দরকার", "Changes requested")
            : t(l, "কোর্সটি প্রকাশ করা যায়নি", "Course not approved");
        List<String> ps = new ArrayList<>();
        ps.add(esc(d.get("courseTitle")));
        if (truthy(d.get("note"))) ps.add(t(l, "রিভিউয়ারের নোট: " + esc(d.get("note")), "Reviewer note: " + esc(d.get("note"))));
        yield new Built(t(l, "কোর্স রিভিউ: " + esc(d.get("courseTitle")), "Course review: " + esc(d.get("courseTitle"))),
            new Block(heading, ps, null, new Cta(t(l, "কোর্স দেখুন", "Open course"), appUrl + "/teach/courses"), null));
      }
      case PAYOUT_PAID -> new Built(t(l, "পেআউট পাঠানো হয়েছে — " + taka(d.get("amount")), "Payout sent — " + taka(d.get("amount"))),
          new Block(t(l, "আপনার টাকা পাঠানো হয়েছে", "Your payout has been sent"),
              List.of(t(l, taka(d.get("amount")) + " — " + esc(d.get("method")) + " (" + esc(d.get("account")) + ")। রেফারেন্স: " + esc(d.get("reference")),
                  taka(d.get("amount")) + " — " + esc(d.get("method")) + " (" + esc(d.get("account")) + "). Reference: " + esc(d.get("reference")))),
              null, new Cta(t(l, "আয় দেখুন", "View earnings"), appUrl + "/teach/earnings"), null));
      case CLASSROOM_DEADLINE -> new Built(t(l, "ডেডলাইন: " + esc(d.get("title")) + " — " + esc(d.get("when")), "Due: " + esc(d.get("title")) + " — " + esc(d.get("when"))),
          new Block(t(l, "অ্যাসাইনমেন্ট জমার সময় ঘনিয়ে এসেছে", "An assignment is due soon"),
              List.of("<strong>" + esc(d.get("title")) + "</strong> · " + esc(d.get("courseName")), t(l, "জমার সময়: " + esc(d.get("when")), "Due: " + esc(d.get("when")))),
              null,
              truthy(d.get("link")) ? new Cta(t(l, "Classroom-এ খুলুন", "Open in Classroom"), str(d.get("link"))) : null,
              t(l, "Google-এর সঙ্গে যুক্ত নয়।", "Not affiliated with Google.")));
      case ADMIN_BROADCAST -> new Built(d.get("subject") == null ? "ShikkhaSathi" : str(d.get("subject")),
          new Block(str(d.get("subject")), Arrays.stream(str(d.get("body")).split("\n{2,}", -1)).map(EmailTemplates::esc).toList(), null, null, null));
      case TEST -> new Built(t(l, "শিক্ষাসাথী টেস্ট মেইল", "ShikkhaSathi test email"),
          new Block(t(l, "ইমেইল সংযোগ ঠিক আছে ✓", "Email is working ✓"),
              List.of(t(l, "এই মেইলটি অ্যাডমিন প্যানেল থেকে পাঠানো হয়েছে (" + esc(d.get("provider")) + ")।", "Sent from the admin panel via " + esc(d.get("provider")) + ".")),
              null, null, null));
    };
  }
}
