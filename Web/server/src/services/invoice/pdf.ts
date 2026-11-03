import PDFDocument from 'pdfkit';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const fontDir = resolve(dirname(fileURLToPath(import.meta.url)), '../../../assets/fonts');
const FONTS = {
  latin: resolve(fontDir, 'inter-latin-400-normal.woff'),
  latinBold: resolve(fontDir, 'inter-latin-600-normal.woff'),
  bn: resolve(fontDir, 'hind-siliguri-bengali-400-normal.woff'),
  bnBold: resolve(fontDir, 'hind-siliguri-bengali-600-normal.woff'),
};

export interface InvoiceData {
  number: string;
  date: Date;
  billingName: string;
  billingPhone: string | null;
  billingEmail: string | null;
  orderId: string;
  items: { title: string; price: number; discount: number }[];
  subtotal: number;
  discount: number;
  vat: number;
  total: number;
  paymentMethod: string | null;
  gatewayRef: string | null;
}

const bdt = (paisa: number) => `BDT ${(paisa / 100).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
const hasBangla = (s: string) => /[ঀ-৿]/.test(s);

/** Branded invoice PDF. Bangla text (names, course titles) uses Hind Siliguri; numbers use Inter. */
export function renderInvoicePdf(d: InvoiceData): Promise<Buffer> {
  return new Promise((resolvePdf, reject) => {
    const doc = new PDFDocument({ size: 'A4', margin: 48, info: { Title: `Invoice ${d.number}`, Author: 'ShikkhaSathi', Subject: 'Course purchase invoice' } });
    const chunks: Buffer[] = [];
    doc.on('data', (c: Buffer) => chunks.push(c));
    doc.on('end', () => resolvePdf(Buffer.concat(chunks)));
    doc.on('error', reject);
    doc.registerFont('latin', FONTS.latin);
    doc.registerFont('latinBold', FONTS.latinBold);
    doc.registerFont('bn', FONTS.bn);
    doc.registerFont('bnBold', FONTS.bnBold);
    const text = (s: string, bold = false) => doc.font(hasBangla(s) ? (bold ? 'bnBold' : 'bn') : bold ? 'latinBold' : 'latin');
    /** Mixed Bangla/Latin: render script runs with the matching font (the Bengali subset has no Latin glyphs). */
    const mixed = (s: string, x: number, y: number, size: number, opts: PDFKit.Mixins.TextOptions = {}, bold = false) => {
      const runs = s.match(/[\u0980-\u09FF\u200C\u200D]+(?:\s+[\u0980-\u09FF\u200C\u200D]+)*|[^\u0980-\u09FF\u200C\u200D]+/g) ?? [s];
      doc.fontSize(size);
      runs.forEach((r, i) => {
        doc.font(hasBangla(r) ? (bold ? 'bnBold' : 'bn') : bold ? 'latinBold' : 'latin');
        if (i === 0) doc.text(r, x, y, { ...opts, continued: i < runs.length - 1 });
        else doc.text(r, { continued: i < runs.length - 1 });
      });
    };
    const W = doc.page.width - 96;

    doc.rect(48, 48, W, 4).fill('#4F46E5');
    doc.fillColor('#0F172A');
    text('ShikkhaSathi', true).fontSize(22).text('ShikkhaSathi', 48, 68);
    doc.fillColor('#64748B');
    mixed('শিক্ষাসাথী · shikkhasathi.com', 48, 96, 11);
    text('INVOICE', true).fontSize(18).fillColor('#0F172A').text('INVOICE', 48, 68, { width: W, align: 'right' });
    text('x').fontSize(10).fillColor('#64748B').text(`No. ${d.number}`, 48, 92, { width: W, align: 'right' }).text(d.date.toISOString().slice(0, 10), { width: W, align: 'right' });

    doc.moveDown(2);
    let y = 140;
    text('Billed to', true).fontSize(10).fillColor('#64748B').text('Billed to', 48, y);
    doc.fillColor('#0F172A');
    mixed(d.billingName, 48, y + 14, 12, {}, true);
    text('x').fontSize(10).fillColor('#334155').text([d.billingPhone, d.billingEmail].filter(Boolean).join(' · ') || '—', 48, y + 32);
    text('Order', true).fontSize(10).fillColor('#64748B').text('Order', 320, y);
    text('x').fontSize(10).fillColor('#334155').text(d.orderId, 320, y + 14, { width: W - 272 }).text(`Paid via ${d.paymentMethod ?? '—'}${d.gatewayRef ? ` · ${d.gatewayRef}` : ''}`, 320, y + 30, { width: W - 272 });

    y = 220;
    doc.rect(48, y, W, 24).fill('#F1F5F9');
    text('Item', true).fontSize(10).fillColor('#334155').text('Item', 58, y + 7).text('Amount', 48, y + 7, { width: W - 10, align: 'right' });
    y += 32;
    for (const it of d.items) {
      doc.fillColor('#0F172A');
      mixed(it.title, 58, y, 11, { width: W - 140 });
      text('x').fontSize(11).text(bdt(it.price), 48, y, { width: W - 10, align: 'right' });
      y = Math.max(doc.y, y + 16) + 8;
      doc.moveTo(48, y - 4).lineTo(48 + W, y - 4).strokeColor('#E2E8F0').lineWidth(1).stroke();
    }
    const row = (label: string, v: string, bold = false) => {
      text(label, bold).fontSize(bold ? 12 : 10).fillColor(bold ? '#0F172A' : '#334155').text(label, 300, y, { width: 150 });
      text(v, bold).fontSize(bold ? 12 : 10).text(v, 48, y, { width: W - 10, align: 'right' });
      y += bold ? 22 : 18;
    };
    y += 6;
    row('Subtotal', bdt(d.subtotal));
    if (d.discount) row('Discount', `− ${bdt(d.discount)}`);
    if (d.vat) row('VAT', bdt(d.vat));
    row('Total paid', bdt(d.total), true);

    text('x').fontSize(9).fillColor('#64748B').text('Refunds: within 48 hours of purchase if less than 20% of the course has been watched (shikkhasathi.com/refund). ShikkhaSathi is not officially affiliated with any university. Support: support@shikkhasathi.com', 48, 760, { width: W, align: 'center' });
    doc.end();
  });
}
