package com.shikkhasathi.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.shikkhasathi.payment.invoice.InvoicePdfRenderer;
import com.shikkhasathi.payment.invoice.InvoicePdfRenderer.InvoiceData;
import com.shikkhasathi.payment.invoice.InvoicePdfRenderer.Item;
import java.io.File;
import java.time.Instant;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

/** Renders a sample invoice; the PNG in target/ is for visual checks of Bangla shaping. */
class InvoicePdfRendererTest {
  @Test
  void rendersAnA4InvoiceWithBanglaText() throws Exception {
    var data = new InvoiceData("SS-2026-000123", Instant.parse("2026-11-04T10:00:00Z"), "মোঃ রাকিব হাসান", "+8801712345678", "rakib@example.com",
        "0192f0c0-0000-7000-8000-000000000001",
        List.of(new Item("ডাটা স্ট্রাকচার (CSE-2203) — সম্পূর্ণ প্রস্তুতি: স্ট্যাক, কিউ, লিংকড লিস্ট, ট্রি ও গ্রাফ", 129900, 0), new Item("Discrete Mathematics ফাইনাল সাজেশন", 49900, 10000)),
        179800, 10000, 0, 169800, "bkash", "FAKE-00000001");
    byte[] pdf = new InvoicePdfRenderer().render(data);
    assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    try (var doc = Loader.loadPDF(pdf)) {
      assertThat(doc.getNumberOfPages()).isEqualTo(1);
      assertThat(doc.getDocumentInformation().getTitle()).isEqualTo("Invoice SS-2026-000123");
      var img = new PDFRenderer(doc).renderImageWithDPI(0, 110);
      File out = new File("target/invoice-sample.png");
      out.getParentFile().mkdirs();
      ImageIO.write(img, "png", out);
      // something was drawn in the title area and the items area
      assertThat(dark(img, 60, 100, 200, 140)).isGreaterThan(50);
      assertThat(dark(img, 80, 380, 500, 420)).isGreaterThan(50);
    }
  }

  private static int dark(java.awt.image.BufferedImage img, int x0, int y0, int x1, int y1) {
    int n = 0;
    for (int x = x0; x < x1; x++) for (int y = y0; y < y1; y++) if ((img.getRGB(x, y) & 0xFF) < 128) n++;
    return n;
  }
}
