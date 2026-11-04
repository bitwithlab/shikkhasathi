package com.shikkhasathi.notification.email;

import com.shikkhasathi.common.Ids;
import com.shikkhasathi.common.http.HttpTransport;
import com.shikkhasathi.common.jobs.EnqueueOptions;
import com.shikkhasathi.common.jobs.JobNames;
import com.shikkhasathi.common.jobs.JobQueue;
import com.shikkhasathi.config.AppProperties;
import com.shikkhasathi.integration.service.IntegrationService;
import com.shikkhasathi.notification.email.EmailMessage.EmailAttachment;
import com.shikkhasathi.notification.email.EmailMessage.EmailSender;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Transactional email (CLAUDE.md §0B): every email is logged in CORE {@code email_logs} and delivered by the
 * {@code email.send} job through Brevo API → Brevo SMTP → (outside production) the fake provider.
 * Template data is never stored (it may contain OTPs).
 */
@Service
public class EmailService {
  /** Payload of the {@code email.send} job. */
  public record EmailJob(UUID logId, String to, String toName, String template, String locale, Map<String, Object> data, List<EmailAttachment> attachments) {}

  /** Resolved sender identity + provider chain (first that succeeds wins). */
  public record Providers(EmailSender sender, List<EmailProvider> chain) {}

  private final NamedParameterJdbcTemplate core;
  private final JobQueue jobs;
  private final IntegrationService integrations;
  private final EmailTemplates templates;
  private final FakeEmailProvider fake;
  private final HttpTransport http;
  private final JsonMapper json;
  private final AppProperties props;

  public EmailService(@Qualifier("coreJdbc") NamedParameterJdbcTemplate core, JobQueue jobs, IntegrationService integrations, EmailTemplates templates, FakeEmailProvider fake, HttpTransport http, JsonMapper json, AppProperties props) {
    this.core = core;
    this.jobs = jobs;
    this.integrations = integrations;
    this.templates = templates;
    this.fake = fake;
    this.http = http;
    this.json = json;
    this.props = props;
  }

  @PostConstruct
  void registerTester() {
    integrations.registerTester("brevo", (cfg, opts) -> testSend(cfg, opts.to()));
  }

  /** Queue an email in Bangla with no attachments. Returns the email_logs id. */
  public UUID send(String to, EmailTemplate template, Map<String, ?> data) {
    return send(to, null, template, data, "bn", null, List.of());
  }

  /**
   * Queue an email: inserts {@code email_logs} (status {@code queued}) and enqueues {@code email.send}.
   *
   * @param toName        nullable
   * @param data          template values (strings/numbers/null); never persisted
   * @param locale        "bn" (default when null) or "en"
   * @param relatedEntity nullable, e.g. {@code notification:<id>} or {@code order:<id>}
   * @param attachments   nullable
   * @return the email_logs id
   */
  public UUID send(String to, String toName, EmailTemplate template, Map<String, ?> data, String locale, String relatedEntity, List<EmailAttachment> attachments) {
    String l = locale == null ? "bn" : locale;
    Map<String, Object> d = data == null ? Map.of() : new java.util.LinkedHashMap<>(data);
    String subject = templates.render(template, d, l).subject();
    UUID id = Ids.uuidv7();
    core.update("insert into email_logs (id, to_email, template, locale, subject, status, related_entity) values (:id, :to, :t, :l, :s, 'queued', :r)",
        new MapSqlParameterSource().addValue("id", id).addValue("to", to).addValue("t", template.id()).addValue("l", l).addValue("s", subject).addValue("r", relatedEntity));
    var job = new EmailJob(id, to, toName, template.id(), l, d, attachments == null ? List.of() : attachments);
    jobs.enqueue(JobNames.EMAIL_SEND, job, template.sensitive() ? EnqueueOptions.DEFAULT.asSensitive() : EnqueueOptions.DEFAULT);
    return id;
  }

  /** Sender + provider chain from the given integration values (null = current {@code brevo} settings). */
  public Providers resolveProviders(Map<String, String> cfg) {
    Map<String, String> c = cfg != null ? cfg : integrations.get("brevo");
    EmailSender sender = new EmailSender(or(c.get("sender_name"), "ShikkhaSathi"), or(c.get("sender_email"), "no-reply@shikkhasathi.com"), blank(c.get("reply_to")) ? null : c.get("reply_to"));
    List<EmailProvider> chain = new ArrayList<>();
    if (!blank(c.get("api_key"))) chain.add(new BrevoApiProvider(c.get("api_key"), http, json));
    if (!blank(c.get("smtp_login")) && !blank(c.get("smtp_key"))) {
      int port;
      try {
        port = (int) Double.parseDouble(or(c.get("smtp_port"), "587"));
      } catch (NumberFormatException e) {
        port = 587;
      }
      chain.add(new BrevoSmtpProvider(new BrevoSmtpProvider.Config(or(c.get("smtp_host"), "smtp-relay.brevo.com"), port, c.get("smtp_login"), c.get("smtp_key"))));
    }
    if (chain.isEmpty() && !props.isProd()) chain.add(fake);
    return new Providers(sender, chain);
  }

  /** Deliver one queued email (the {@code email.send} job): first provider that succeeds wins; updates email_logs. */
  public void deliver(EmailJob job) {
    EmailTemplate template = EmailTemplate.of(job.template()).orElseThrow(() -> new IllegalArgumentException("unknown template " + job.template()));
    // idempotent: a retried job never re-sends a delivered email
    var status = core.queryForList("select status from email_logs where id = :id", Map.of("id", job.logId()), String.class);
    if (!status.isEmpty() && "sent".equals(status.get(0))) return;
    var rendered = templates.render(template, job.data(), job.locale());
    var p = resolveProviders(null);
    if (p.chain().isEmpty()) {
      core.update("update email_logs set status = 'failed', error = 'Email provider not configured', updated_at = now() where id = :id", Map.of("id", job.logId()));
      throw new IllegalStateException("Email provider not configured");
    }
    RuntimeException last = null;
    for (EmailProvider provider : p.chain()) {
      try {
        String messageId = provider.send(new EmailMessage(job.to(), job.toName(), rendered.subject(), rendered.html(), rendered.text(), job.attachments(), List.of(job.template())), p.sender());
        core.update("update email_logs set status = 'sent', provider = :p, provider_message_id = :m, error = null, sent_at = now(), updated_at = now() where id = :id",
            new MapSqlParameterSource().addValue("p", provider.name()).addValue("m", messageId).addValue("id", job.logId()));
        return;
      } catch (RuntimeException e) {
        last = e;
        org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("email provider {} failed for template {}: {}", provider.name(), job.template(), e.getMessage());
      }
    }
    String err = last.getMessage() == null ? "unknown" : last.getMessage();
    core.update("update email_logs set status = 'failed', error = :e, attempts = attempts + 1, updated_at = now() where id = :id",
        new MapSqlParameterSource().addValue("e", err.length() > 500 ? err.substring(0, 500) : err).addValue("id", job.logId()));
    throw last;
  }

  /** "টেস্ট মেইল পাঠান": sends the {@code test} template synchronously through the first provider. */
  IntegrationService.TestResult testSend(Map<String, String> cfg, String to) {
    if (blank(to)) return new IntegrationService.TestResult(false, "টেস্ট মেইলের জন্য প্রাপকের ইমেইল দরকার");
    var p = resolveProviders(cfg);
    if (p.chain().isEmpty()) return new IntegrationService.TestResult(false, "Brevo API key বা SMTP তথ্য দেওয়া নেই");
    EmailProvider provider = p.chain().get(0);
    var rendered = templates.render(EmailTemplate.TEST, Map.of("provider", provider.name()), "bn");
    UUID id = Ids.uuidv7();
    core.update("insert into email_logs (id, to_email, template, subject, status, provider) values (:id, :to, 'test', :s, 'queued', :p)",
        new MapSqlParameterSource().addValue("id", id).addValue("to", to).addValue("s", rendered.subject()).addValue("p", provider.name()));
    try {
      String messageId = provider.send(new EmailMessage(to, null, rendered.subject(), rendered.html(), rendered.text(), List.of(), List.of("test")), p.sender());
      core.update("update email_logs set status = 'sent', provider_message_id = :m, sent_at = now(), updated_at = now() where id = :id", new MapSqlParameterSource().addValue("m", messageId).addValue("id", id));
      return new IntegrationService.TestResult(true, "টেস্ট মেইল পাঠানো হয়েছে (" + provider.name() + ") → " + to);
    } catch (RuntimeException e) {
      String m = e.getMessage() == null ? "unknown" : e.getMessage();
      core.update("update email_logs set status = 'failed', error = :e, updated_at = now() where id = :id", new MapSqlParameterSource().addValue("e", m.length() > 500 ? m.substring(0, 500) : m).addValue("id", id));
      return new IntegrationService.TestResult(false, m.length() > 300 ? m.substring(0, 300) : m);
    }
  }

  private static boolean blank(String s) {
    return s == null || s.isEmpty();
  }

  private static String or(String v, String d) {
    return blank(v) ? d : v;
  }
}
