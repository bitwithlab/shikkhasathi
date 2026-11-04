package com.shikkhasathi.payment.invoice;

import de.rototor.pdfbox.graphics2d.PdfBoxGraphics2D;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Component;

/**
 * Branded A4 invoice PDF (same layout as the previous API). Text is shaped by Java2D (HarfBuzz in the JDK, so
 * Bangla conjuncts and vowel signs are correct) and drawn as vector outlines through PdfBoxGraphics2D into PDFBox.
 * Bangla runs use Hind Siliguri; Latin text and numbers use Inter (the Bengali font subset has no Latin glyphs).
 */
@Component
public class InvoicePdfRenderer {
  /** One purchased course: price/discount in paisa. */
  public record Item(String title, long price, long discount) {}

  /** Everything printed on the invoice; money in paisa; phone/email/paymentMethod/gatewayRef nullable. */
  public record InvoiceData(String number, Instant date, String billingName, String billingPhone, String billingEmail, String orderId, List<Item> items,
                            long subtotal, long discount, long vat, long total, String paymentMethod, String gatewayRef) {}

  private static final float PAGE_W = PDRectangle.A4.getWidth();
  private static final float PAGE_H = PDRectangle.A4.getHeight();
  private static final float M = 48;
  private static final float W = PAGE_W - 96;
  private static final FontRenderContext FRC = new FontRenderContext(new AffineTransform(), true, true);

  private final Font latin;
  private final Font latinBold;
  private final Font bn;
  private final Font bnBold;

  public InvoicePdfRenderer() {
    latin = load("/fonts/inter-latin-400.ttf");
    latinBold = load("/fonts/inter-latin-600.ttf");
    bn = load("/fonts/hind-siliguri-bengali-400.ttf");
    bnBold = load("/fonts/hind-siliguri-bengali-600.ttf");
  }

  private static Font load(String path) {
    try (InputStream in = InvoicePdfRenderer.class.getResourceAsStream(path)) {
      if (in == null) throw new IllegalStateException("missing font " + path);
      return Font.createFont(Font.TRUETYPE_FONT, in);
    } catch (FontFormatException | IOException e) {
      throw new IllegalStateException("cannot load font " + path, e);
    }
  }

  /** "BDT 1,23,456.00" — en-IN grouping, 2 decimals. */
  static String bdt(long paisa) {
    long abs = Math.abs(paisa);
    String s = com.shikkhasathi.notification.email.EmailTemplates.indianGroup(Long.toString(abs / 100)) + "." + String.format("%02d", abs % 100);
    return "BDT " + (paisa < 0 ? "-" : "") + s;
  }

  static boolean isBangla(int cp) {
    return (cp >= 0x0980 && cp <= 0x09FF) || cp == 0x200C || cp == 0x200D || cp == 0x0964 || cp == 0x0965 || cp == 0x25CC;
  }

  /** Render the invoice; returns the PDF bytes. */
  public byte[] render(InvoiceData d) {
    try (PDDocument doc = new PDDocument()) {
      PDDocumentInformation info = doc.getDocumentInformation();
      info.setTitle("Invoice " + d.number());
      info.setAuthor("ShikkhaSathi");
      info.setSubject("Course purchase invoice");
      PDPage page = new PDPage(PDRectangle.A4);
      doc.addPage(page);
      PdfBoxGraphics2D g = new PdfBoxGraphics2D(doc, PAGE_W, PAGE_H);
      try {
        draw(new Painter(g), d);
      } finally {
        g.dispose();
      }
      try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
        cs.drawForm(g.getXFormObject());
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("invoice render failed", e);
    }
  }

  private void draw(Painter p, InvoiceData d) {
    p.rect(M, 48, W, 4, "#4F46E5");
    p.text("ShikkhaSathi", M, 68, 22, true, "#0F172A", 0, Align.LEFT);
    p.text("শিক্ষাসাথী · shikkhasathi.com", M, 96, 11, false, "#64748B", 0, Align.LEFT);
    p.text("INVOICE", M, 68, 18, true, "#0F172A", W, Align.RIGHT);
    float ny = p.text("No. " + d.number(), M, 92, 10, false, "#64748B", W, Align.RIGHT);
    p.text(d.date().atZone(ZoneOffset.UTC).toLocalDate().toString(), M, ny, 10, false, "#64748B", W, Align.RIGHT);

    float y = 140;
    p.text("Billed to", M, y, 10, true, "#64748B", 0, Align.LEFT);
    p.text(d.billingName(), M, y + 14, 12, true, "#0F172A", 0, Align.LEFT);
    String contact = String.join(" · ", java.util.stream.Stream.of(d.billingPhone(), d.billingEmail()).filter(s -> s != null && !s.isEmpty()).toList());
    p.text(contact.isEmpty() ? "—" : contact, M, y + 32, 10, false, "#334155", 0, Align.LEFT);
    p.text("Order", 320, y, 10, true, "#64748B", 0, Align.LEFT);
    p.text(d.orderId(), 320, y + 14, 10, false, "#334155", W - 272, Align.LEFT);
    p.text("Paid via " + (d.paymentMethod() != null ? d.paymentMethod() : "—") + (d.gatewayRef() != null ? " · " + d.gatewayRef() : ""), 320, y + 30, 10, false, "#334155", W - 272, Align.LEFT);

    y = 220;
    p.rect(M, y, W, 24, "#F1F5F9");
    p.text("Item", 58, y + 7, 10, true, "#334155", 0, Align.LEFT);
    p.text("Amount", M, y + 7, 10, true, "#334155", W - 10, Align.RIGHT);
    y += 32;
    for (Item it : d.items()) {
      float bottom = p.text(it.title(), 58, y, 11, false, "#0F172A", W - 140, Align.LEFT);
      p.text(bdt(it.price()), M, y, 11, false, "#0F172A", W - 10, Align.RIGHT);
      y = Math.max(bottom, y + 16) + 8;
      p.line(M, y - 4, M + W, y - 4, "#E2E8F0");
    }
    y += 6;
    y = row(p, y, "Subtotal", bdt(d.subtotal()), false);
    if (d.discount() != 0) y = row(p, y, "Discount", "− " + bdt(d.discount()), false);
    if (d.vat() != 0) y = row(p, y, "VAT", bdt(d.vat()), false);
    row(p, y, "Total paid", bdt(d.total()), true);

    p.text("Refunds: within 48 hours of purchase if less than 20% of the course has been watched (shikkhasathi.com/refund). ShikkhaSathi is not officially affiliated with any university. Support: support@shikkhasathi.com",
        M, 760, 9, false, "#64748B", W, Align.CENTER);
  }

  private static float row(Painter p, float y, String label, String value, boolean bold) {
    String color = bold ? "#0F172A" : "#334155";
    float size = bold ? 12 : 10;
    p.text(label, 300, y, size, bold, color, 150, Align.LEFT);
    p.text(value, M, y, size, bold, color, W - 10, Align.RIGHT);
    return y + (bold ? 22 : 18);
  }

  private enum Align { LEFT, RIGHT, CENTER }

  /** Java2D drawing helpers with PDFKit-like semantics: (x, y) is the top-left of the first line box. */
  private final class Painter {
    private final Graphics2D g;

    Painter(Graphics2D g) {
      this.g = g;
      g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    void rect(float x, float y, float w, float h, String color) {
      g.setColor(Color.decode(color));
      g.fill(new Rectangle2D.Float(x, y, w, h));
    }

    void line(float x1, float y1, float x2, float y2, String color) {
      g.setColor(Color.decode(color));
      g.setStroke(new BasicStroke(1f));
      g.draw(new Line2D.Float(x1, y1, x2, y2));
    }

    /** Mixed-script text with per-run fonts; wraps when {@code width > 0}. Returns the y below the last line. */
    float text(String s, float x, float y, float size, boolean bold, String color, float width, Align align) {
      g.setColor(Color.decode(color));
      if (s == null || s.isEmpty()) return y + size * 1.2f;
      AttributedString as = attributed(s, size, bold);
      List<TextLayout> lines = new ArrayList<>();
      if (width > 0 && align != Align.RIGHT) {
        AttributedCharacterIterator it = as.getIterator();
        LineBreakMeasurer lbm = new LineBreakMeasurer(it, FRC);
        while (lbm.getPosition() < it.getEndIndex()) lines.add(lbm.nextLayout(width));
      } else {
        lines.add(new TextLayout(as.getIterator(), FRC));
      }
      Font ref = (bold ? latinBold : latin).deriveFont(size);
      var lm = ref.getLineMetrics("Ag", FRC);
      float lineH = lm.getAscent() + lm.getDescent() + lm.getLeading();
      float top = y;
      for (TextLayout tl : lines) {
        float adv = tl.getVisibleAdvance();
        float lx = switch (align) {
          case LEFT -> x;
          case RIGHT -> x + width - adv;
          case CENTER -> x + (width - adv) / 2;
        };
        float baseline = top + lm.getAscent();
        g.fill(tl.getOutline(AffineTransform.getTranslateInstance(lx, baseline)));
        top += lineH;
      }
      return top;
    }

    /** Per code point: Bangla (and spaces between Bangla words) → Hind Siliguri, everything else → Inter. */
    private AttributedString attributed(String s, float size, boolean bold) {
      Font b = (bold ? bnBold : bn).deriveFont(size);
      Font l = (bold ? latinBold : latin).deriveFont(size);
      int n = s.length();
      boolean[] isBn = new boolean[n];
      for (int i = 0; i < n; ) {
        int cp = s.codePointAt(i);
        boolean v = isBangla(cp) ? b.canDisplay(cp) || !l.canDisplay(cp) : !l.canDisplay(cp) && b.canDisplay(cp);
        for (int k = 0; k < Character.charCount(cp); k++) isBn[i + k] = v;
        i += Character.charCount(cp);
      }
      // whitespace between two Bangla runs stays in the Bangla run (keeps word shaping/spacing natural)
      for (int i = 0; i < n; i++) {
        if (!Character.isWhitespace(s.charAt(i))) continue;
        int a = i - 1, z = i;
        while (z < n && Character.isWhitespace(s.charAt(z))) z++;
        if (a >= 0 && z < n && isBn[a] && isBn[z]) for (int k = i; k < z; k++) isBn[k] = true;
        i = z - 1;
      }
      AttributedString as = new AttributedString(s);
      int start = 0;
      for (int i = 1; i <= n; i++) {
        if (i == n || isBn[i] != isBn[start]) {
          as.addAttribute(TextAttribute.FONT, isBn[start] ? b : l, start, i);
          start = i;
        }
      }
      return as;
    }
  }
}
