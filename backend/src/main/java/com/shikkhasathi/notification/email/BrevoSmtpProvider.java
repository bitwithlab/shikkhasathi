package com.shikkhasathi.notification.email;

import com.shikkhasathi.notification.email.EmailMessage.EmailSender;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

/** Fallback: Brevo SMTP relay (smtp-relay.brevo.com:587, STARTTLS), built at runtime from the integration values. */
public class BrevoSmtpProvider implements EmailProvider {
  /** SMTP settings from Admin → Integrations → Email (Brevo). */
  public record Config(String host, int port, String login, String key) {}

  private final JavaMailSenderImpl sender;

  public BrevoSmtpProvider(Config cfg) {
    sender = new JavaMailSenderImpl();
    sender.setHost(cfg.host());
    sender.setPort(cfg.port());
    sender.setUsername(cfg.login());
    sender.setPassword(cfg.key());
    sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
    Properties p = sender.getJavaMailProperties();
    p.put("mail.transport.protocol", "smtp");
    p.put("mail.smtp.auth", "true");
    p.put("mail.smtp.connectiontimeout", "10000");
    p.put("mail.smtp.timeout", "15000");
    p.put("mail.smtp.writetimeout", "15000");
    if (cfg.port() == 465) p.put("mail.smtp.ssl.enable", "true");
    if (cfg.port() == 587) {
      p.put("mail.smtp.starttls.enable", "true");
      p.put("mail.smtp.starttls.required", "true");
    }
  }

  @Override
  public String name() {
    return "brevo_smtp";
  }

  @Override
  public String send(EmailMessage msg, EmailSender from) {
    try {
      MimeMessage m = sender.createMimeMessage();
      var h = new MimeMessageHelper(m, true, StandardCharsets.UTF_8.name());
      h.setFrom(new InternetAddress(from.email(), from.name(), StandardCharsets.UTF_8.name()));
      h.setTo(msg.toName() != null && !msg.toName().isEmpty() ? new InternetAddress(msg.to(), msg.toName(), StandardCharsets.UTF_8.name()) : new InternetAddress(msg.to()));
      if (from.replyTo() != null && !from.replyTo().isEmpty()) h.setReplyTo(from.replyTo());
      h.setSubject(msg.subject());
      h.setText(msg.text(), msg.html());
      for (var a : msg.attachments()) h.addAttachment(a.name(), new ByteArrayResource(Base64.getDecoder().decode(a.contentBase64())));
      sender.send(m);
      return m.getMessageID();
    } catch (Exception e) {
      throw new IllegalStateException("SMTP: " + e.getMessage(), e);
    }
  }

  /** Connect + authenticate (test connection). */
  public void verify() {
    try {
      sender.testConnection();
    } catch (Exception e) {
      throw new IllegalStateException("SMTP: " + e.getMessage(), e);
    }
  }
}
