import type { EmailTemplate, Locale } from '@shikkhasathi/shared';
import { env } from '../../config/env';

export interface Rendered {
  subject: string;
  html: string;
  text: string;
}

type Data = Record<string, string | number | undefined | null>;

const esc = (v: unknown) =>
  String(v ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');

const taka = (paisa: unknown) => `৳${(Number(paisa ?? 0) / 100).toLocaleString('en-IN')}`;

interface Block {
  heading: string;
  paragraphs: string[];
  code?: string;
  cta?: { label: string; url: string };
  footnote?: string;
}

function layout(locale: Locale, b: Block): { html: string; text: string } {
  const brand = locale === 'bn' ? 'শিক্ষাসাথী' : 'ShikkhaSathi';
  const tagline =
    locale === 'bn'
      ? 'আপনার ক্লাস, আপনার কোর্স, আপনার পরীক্ষার প্রস্তুতি — এক জায়গায়।'
      : 'Your classes. Your courses. Your exam prep. All in one place.';
  const disclaimer = locale === 'bn' ? 'কোনো বিশ্ববিদ্যালয়ের সঙ্গে অফিসিয়ালি যুক্ত নয়।' : 'Not officially affiliated with any university.';
  const settingsUrl = `${env.APP_URL}/profile#notifications`;
  const html = `<!doctype html><html lang="${locale}"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width"><title>${esc(b.heading)}</title></head>
<body style="margin:0;background:#F8FAFC;font-family:'Hind Siliguri',Inter,Arial,sans-serif;color:#0F172A;line-height:1.7">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#F8FAFC;padding:24px 12px"><tr><td align="center">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px;background:#FFFFFF;border:1px solid #E2E8F0;border-radius:16px">
<tr><td style="padding:20px 28px;border-bottom:1px solid #E2E8F0">
<a href="${env.APP_URL}" style="text-decoration:none;color:#4F46E5;font-weight:700;font-size:20px">${brand}</a>
</td></tr>
<tr><td style="padding:28px">
<h1 style="margin:0 0 12px;font-size:20px;line-height:1.5;color:#0F172A">${esc(b.heading)}</h1>
${b.paragraphs.map((p) => `<p style="margin:0 0 12px;font-size:15px;color:#334155">${p}</p>`).join('')}
${b.code ? `<p style="margin:20px 0;font-family:Inter,Arial,sans-serif;font-size:30px;letter-spacing:8px;font-weight:700;color:#4F46E5">${esc(b.code)}</p>` : ''}
${b.cta ? `<p style="margin:24px 0 8px"><a href="${esc(b.cta.url)}" style="display:inline-block;background:#4F46E5;color:#FFFFFF;text-decoration:none;padding:12px 22px;border-radius:12px;font-weight:600">${esc(b.cta.label)}</a></p>` : ''}
${b.footnote ? `<p style="margin:16px 0 0;font-size:13px;color:#64748B">${b.footnote}</p>` : ''}
</td></tr>
<tr><td style="padding:16px 28px;border-top:1px solid #E2E8F0;font-size:12px;color:#64748B">
${tagline}<br>${disclaimer} · <a href="${settingsUrl}" style="color:#64748B">${locale === 'bn' ? 'ইমেইল সেটিংস' : 'Email settings'}</a>
</td></tr></table></td></tr></table></body></html>`;
  const strip = (s: string) => s.replace(/<[^>]+>/g, '');
  const text = [
    brand,
    '',
    b.heading,
    '',
    ...b.paragraphs.map(strip),
    ...(b.code ? ['', b.code] : []),
    ...(b.cta ? ['', `${b.cta.label}: ${b.cta.url}`] : []),
    ...(b.footnote ? ['', strip(b.footnote)] : []),
    '',
    '—',
    tagline,
    disclaimer,
  ].join('\n');
  return { html, text };
}

type Builder = (d: Data, l: Locale) => { subject: string; block: Block };
const t = (l: Locale, bn: string, en: string) => (l === 'bn' ? bn : en);

const builders: Record<EmailTemplate, Builder> = {
  welcome: (d, l) => ({
    subject: t(l, 'শিক্ষাসাথীতে স্বাগতম!', 'Welcome to ShikkhaSathi!'),
    block: {
      heading: t(l, `স্বাগতম, ${esc(d.name)}!`, `Welcome, ${esc(d.name)}!`),
      paragraphs: [
        t(l, 'আপনার অ্যাকাউন্ট তৈরি হয়েছে। Google Classroom সংযুক্ত করলে সব অ্যাসাইনমেন্ট আর নোটিশ এক জায়গায় দেখতে পাবেন।', 'Your account is ready. Connect Google Classroom to see all assignments and notices in one place.'),
        ...(d.code ? [t(l, 'ইমেইল যাচাই করতে নিচের কোডটি দিন (১০ মিনিট বৈধ):', 'Use this code to verify your email (valid for 10 minutes):')] : []),
      ],
      ...(d.code ? { code: String(d.code) } : {}),
      cta: { label: t(l, 'ড্যাশবোর্ডে যান', 'Open dashboard'), url: `${env.APP_URL}/home` },
    },
  }),
  email_otp: (d, l) => ({
    subject: t(l, 'আপনার শিক্ষাসাথী যাচাই কোড', 'Your ShikkhaSathi verification code'),
    block: {
      heading: t(l, 'যাচাই কোড', 'Verification code'),
      paragraphs: [t(l, 'নিচের কোডটি ১০ মিনিটের মধ্যে ব্যবহার করুন। কাউকে এই কোড জানাবেন না।', 'Use this code within 10 minutes. Never share it with anyone.')],
      code: String(d.code ?? ''),
      footnote: t(l, 'আপনি অনুরোধ না করে থাকলে এই ইমেইলটি উপেক্ষা করুন।', "If you didn't request this, you can ignore this email."),
    },
  }),
  password_reset: (d, l) => ({
    subject: t(l, 'পাসওয়ার্ড রিসেট কোড', 'Password reset code'),
    block: {
      heading: t(l, 'পাসওয়ার্ড রিসেট করুন', 'Reset your password'),
      paragraphs: [t(l, 'পাসওয়ার্ড বদলাতে নিচের কোডটি দিন (১০ মিনিট বৈধ)।', 'Enter this code to set a new password (valid for 10 minutes).')],
      code: String(d.code ?? ''),
      footnote: t(l, 'অনুরোধ আপনি না করলে পাসওয়ার্ড অপরিবর্তিত থাকবে।', "If this wasn't you, your password stays unchanged."),
    },
  }),
  order_paid: (d, l) => ({
    subject: t(l, `পেমেন্ট সফল — ইনভয়েস ${esc(d.invoiceNumber)}`, `Payment received — invoice ${esc(d.invoiceNumber)}`),
    block: {
      heading: t(l, 'কোর্স আনলক হয়েছে', 'Your course is unlocked'),
      paragraphs: [
        t(l, `<strong>${esc(d.items)}</strong> — মোট ${taka(d.total)} পরিশোধ হয়েছে।`, `<strong>${esc(d.items)}</strong> — ${taka(d.total)} paid.`),
        t(l, `ইনভয়েস নম্বর: ${esc(d.invoiceNumber)}`, `Invoice number: ${esc(d.invoiceNumber)}`),
      ],
      cta: { label: t(l, 'শেখা শুরু করুন', 'Start learning'), url: `${env.APP_URL}/my-courses` },
      ...(d.invoiceUrl ? { footnote: t(l, `ইনভয়েস ডাউনলোড: <a href="${esc(d.invoiceUrl)}">PDF</a>`, `Download invoice: <a href="${esc(d.invoiceUrl)}">PDF</a>`) } : {}),
    },
  }),
  refund: (d, l) => ({
    subject: t(l, 'রিফান্ড আপডেট', 'Refund update'),
    block: {
      heading: d.status === 'rejected' ? t(l, 'রিফান্ড অনুরোধ গ্রহণ হয়নি', 'Refund request declined') : t(l, 'রিফান্ড অনুমোদিত হয়েছে', 'Refund approved'),
      paragraphs: [
        d.status === 'rejected'
          ? t(l, `দুঃখিত, অর্ডার ${esc(d.orderId)}-এর রিফান্ড দেওয়া সম্ভব হয়নি। ${esc(d.note)}`, `Sorry, the refund for order ${esc(d.orderId)} was declined. ${esc(d.note)}`)
          : t(l, `${taka(d.amount)} আপনার পেমেন্ট মাধ্যমে ফেরত যাবে (৩–৭ কার্যদিবস)।`, `${taka(d.amount)} will be returned to your payment method (3–7 business days).`),
      ],
    },
  }),
  seller_application_result: (d, l) => ({
    subject: t(l, 'শিক্ষক আবেদনের ফলাফল', 'Your teacher application'),
    block: {
      heading: d.status === 'verified' ? t(l, 'অভিনন্দন! আপনি এখন ভেরিফায়েড শিক্ষক', "Congratulations — you're verified") : t(l, 'আবেদনটি আবার দেখুন', 'Please review your application'),
      paragraphs: [
        d.status === 'verified'
          ? t(l, 'এখন কোর্স বানিয়ে রিভিউয়ের জন্য পাঠাতে পারবেন।', 'You can now create courses and submit them for review.')
          : t(l, `কারণ: ${esc(d.note)}`, `Reason: ${esc(d.note)}`),
      ],
      cta: { label: 'Teacher Studio', url: `${env.APP_URL}/teach` },
    },
  }),
  course_review_decision: (d, l) => ({
    subject: t(l, `কোর্স রিভিউ: ${esc(d.courseTitle)}`, `Course review: ${esc(d.courseTitle)}`),
    block: {
      heading:
        d.decision === 'published'
          ? t(l, 'আপনার কোর্স প্রকাশিত হয়েছে', 'Your course is live')
          : d.decision === 'changes_requested'
            ? t(l, 'কিছু পরিবর্তন দরকার', 'Changes requested')
            : t(l, 'কোর্সটি প্রকাশ করা যায়নি', 'Course not approved'),
      paragraphs: [esc(d.courseTitle), ...(d.note ? [t(l, `রিভিউয়ারের নোট: ${esc(d.note)}`, `Reviewer note: ${esc(d.note)}`)] : [])],
      cta: { label: t(l, 'কোর্স দেখুন', 'Open course'), url: `${env.APP_URL}/teach/courses` },
    },
  }),
  payout_paid: (d, l) => ({
    subject: t(l, `পেআউট পাঠানো হয়েছে — ${taka(d.amount)}`, `Payout sent — ${taka(d.amount)}`),
    block: {
      heading: t(l, 'আপনার টাকা পাঠানো হয়েছে', 'Your payout has been sent'),
      paragraphs: [t(l, `${taka(d.amount)} — ${esc(d.method)} (${esc(d.account)})। রেফারেন্স: ${esc(d.reference)}`, `${taka(d.amount)} — ${esc(d.method)} (${esc(d.account)}). Reference: ${esc(d.reference)}`)],
      cta: { label: t(l, 'আয় দেখুন', 'View earnings'), url: `${env.APP_URL}/teach/earnings` },
    },
  }),
  classroom_deadline: (d, l) => ({
    subject: t(l, `ডেডলাইন: ${esc(d.title)} — ${esc(d.when)}`, `Due: ${esc(d.title)} — ${esc(d.when)}`),
    block: {
      heading: t(l, 'অ্যাসাইনমেন্ট জমার সময় ঘনিয়ে এসেছে', 'An assignment is due soon'),
      paragraphs: [`<strong>${esc(d.title)}</strong> · ${esc(d.courseName)}`, t(l, `জমার সময়: ${esc(d.when)}`, `Due: ${esc(d.when)}`)],
      ...(d.link ? { cta: { label: t(l, 'Classroom-এ খুলুন', 'Open in Classroom'), url: String(d.link) } } : {}),
      footnote: t(l, 'Google-এর সঙ্গে যুক্ত নয়।', 'Not affiliated with Google.'),
    },
  }),
  admin_broadcast: (d) => ({
    subject: String(d.subject ?? 'ShikkhaSathi'),
    block: { heading: String(d.subject ?? ''), paragraphs: String(d.body ?? '').split(/\n{2,}/).map(esc) },
  }),
  test: (d, l) => ({
    subject: t(l, 'শিক্ষাসাথী টেস্ট মেইল', 'ShikkhaSathi test email'),
    block: {
      heading: t(l, 'ইমেইল সংযোগ ঠিক আছে ✓', 'Email is working ✓'),
      paragraphs: [t(l, `এই মেইলটি অ্যাডমিন প্যানেল থেকে পাঠানো হয়েছে (${esc(d.provider)})।`, `Sent from the admin panel via ${esc(d.provider)}.`)],
    },
  }),
};

export function renderEmail(template: EmailTemplate, data: Data, locale: Locale = 'bn'): Rendered {
  const { subject, block } = builders[template](data, locale);
  return { subject, ...layout(locale, block) };
}
