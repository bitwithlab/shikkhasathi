import type { LegalDoc } from './LegalPage';

const UPDATED = '৩ নভেম্বর ২০২৬ / 3 November 2026';
const s = (hbn: string, hen: string, bn: string[], en: string[]) => ({ h: { bn: hbn, en: hen }, p: { bn, en } });

export const PRIVACY: LegalDoc = {
  title: { bn: 'প্রাইভেসি নীতি', en: 'Privacy Policy' },
  updated: UPDATED,
  sections: [
    s('আমরা কারা', 'Who we are', ['শিক্ষাসাথী (ShikkhaSathi, shikkhasathi.com) বাংলাদেশের শিক্ষার্থীদের জন্য একটি একাডেমিক ড্যাশবোর্ড ও পরীক্ষা-প্রস্তুতির কোর্স মার্কেটপ্লেস। আমরা কোনো বিশ্ববিদ্যালয় বা Google-এর সঙ্গে অফিসিয়ালি যুক্ত নই। যোগাযোগ: privacy@shikkhasathi.com'], ['ShikkhaSathi (shikkhasathi.com) is an academic dashboard and exam-prep course marketplace for students in Bangladesh. We are not officially affiliated with any university or with Google. Contact: privacy@shikkhasathi.com']),
    s('যে তথ্য সংগ্রহ করি', 'Information we collect', ['অ্যাকাউন্ট: নাম, মোবাইল নম্বর, ইমেইল, পাসওয়ার্ডের হ্যাশ (আসল পাসওয়ার্ড কখনো সংরক্ষণ করি না)।', 'একাডেমিক তথ্য: বিশ্ববিদ্যালয়, বিভাগ, সেমিস্টার, ব্যাচ, সেকশন — শুধু প্রাসঙ্গিক কোর্স দেখাতে।', 'কেনাকাটা: অর্ডার, ইনভয়েস, পেমেন্টের রেফারেন্স। কার্ড বা মোবাইল ওয়ালেটের PIN আমাদের সার্ভারে আসে না — পেমেন্ট গেটওয়ে তা প্রক্রিয়া করে।', 'পড়াশোনার অগ্রগতি: কোন লেসন দেখেছেন, নোট, Mock Test-এর ফল।', 'ডিভাইস: লগইন সেশন, পুশ নোটিফিকেশনের টোকেন।'], ['Account: name, mobile number, email, a hash of your password (we never store the password itself).', 'Academic info: university, department, semester, batch, section — only to show relevant courses.', 'Purchases: orders, invoices, payment references. Card or wallet PINs never reach our servers — the payment gateway handles them.', 'Learning progress: lessons watched, notes, mock test results.', 'Devices: sign-in sessions and push notification tokens.']),
    s('Google Classroom ডেটা (Google API Services User Data Policy)', 'Google Classroom data (Google API Services User Data Policy)', [
      'আপনি নিজে "Google Classroom সংযুক্ত করুন" চাপলে আমরা শুধু পড়ার (read-only) অনুমতি চাই: আপনার ক্লাসের তালিকা, অ্যাসাইনমেন্ট ও আপনার নিজের জমার অবস্থা ও গ্রেড, শিক্ষকের দেওয়া ম্যাটেরিয়াল ও ঘোষণা, এবং পরিবর্তন জানতে push notification।',
      'এই তথ্য শুধু আপনাকে আপনার ড্যাশবোর্ড, ক্যালেন্ডার, ডেডলাইন রিমাইন্ডার ও নোটিফিকেশন দেখানোর জন্য ব্যবহার হয়। আমরা Classroom-এ কিছু পোস্ট, জমা বা পরিবর্তন করি না।',
      'ShikkhaSathi-এর Google API থেকে পাওয়া তথ্যের ব্যবহার ও অন্য কোনো অ্যাপে হস্তান্তর Google API Services User Data Policy, Limited Use শর্তসহ, মেনে চলে। আমরা এই তথ্য বিক্রি করি না, বিজ্ঞাপনে ব্যবহার করি না, কোর্স সাজেশনে বা অ্যানালিটিক্সে ব্যবহার করি না, এবং AI মডেল প্রশিক্ষণে ব্যবহার করি না। কোনো মানুষ তা পড়েন না — শুধু আপনার অনুমতিতে সাপোর্টের জন্য, নিরাপত্তার কারণে, বা আইনগত প্রয়োজনে ব্যতিক্রম হতে পারে।',
      'Google-এর refresh token এনক্রিপ্ট (AES-256-GCM) করে শুধু আমাদের সার্ভারে রাখা হয়। প্রোফাইল → Google Classroom থেকে "সংযোগ বিচ্ছিন্ন করুন" চাপলে আমরা Google-এ অনুমতি বাতিল করি এবং আমাদের কাছে থাকা আপনার সব Classroom তথ্য মুছে ফেলি।',
    ], [
      'When you tap "Connect Google Classroom" we request read-only access to: your class list, coursework and your own submission status and grades, course materials and announcements posted by teachers, and push notifications about changes.',
      'This data is used only to show you your dashboard, calendar, deadline reminders and notifications. We never post, submit or change anything in Classroom.',
      "ShikkhaSathi's use and transfer of information received from Google APIs to any other app will adhere to the Google API Services User Data Policy, including the Limited Use requirements. We do not sell this data, use it for advertising, course recommendations or analytics, or use it to train AI models. No human reads it, except with your consent for support, for security, or where required by law.",
      'Google refresh tokens are encrypted (AES-256-GCM) and stored only on our servers. Disconnecting in Profile → Google Classroom revokes access at Google and deletes all Classroom data we hold about you.',
    ]),
    s('তথ্য কোথায় থাকে ও কতদিন', 'Where data is stored and for how long', ['তথ্য এনক্রিপ্টেড সংযোগে Supabase (Postgres) সার্ভারে থাকে; শুধু আমাদের API সার্ভার তা পড়তে পারে। অ্যাকাউন্ট মুছতে চাইলে ৩০ দিনের মধ্যে স্থায়ীভাবে মুছে যায়; আইন অনুযায়ী প্রয়োজনীয় আর্থিক রেকর্ড (ইনভয়েস) নির্দিষ্ট সময় রাখা হয়।'], ['Data is stored on Supabase (Postgres) over encrypted connections; only our API server can read it. Account deletion is permanent after 30 days; financial records (invoices) required by law are retained for the legally required period.']),
    s('যাদের সঙ্গে শেয়ার করি', 'Who we share with', ['শুধু সেবা চালাতে দরকারি প্রসেসর: পেমেন্ট গেটওয়ে (SSLCommerz/bKash), ইমেইল (Brevo), SMS প্রোভাইডার, ভিডিও হোস্টিং, পুশ নোটিফিকেশন (Firebase)। কোর্স কিনলে শিক্ষক আপনার নাম ও অগ্রগতি দেখতে পান, ফোন নম্বর নয়।'], ['Only processors needed to run the service: payment gateway (SSLCommerz/bKash), email (Brevo), SMS provider, video hosting and push notifications (Firebase). When you buy a course the teacher sees your name and progress, not your phone number.']),
    s('আপনার অধিকার', 'Your rights', ['প্রোফাইল থেকে তথ্য দেখতে, বদলাতে, নোটিফিকেশন বন্ধ করতে, ডিভাইস থেকে সাইন আউট করতে, Classroom বিচ্ছিন্ন করতে ও অ্যাকাউন্ট মুছতে পারবেন। অন্য কোনো অনুরোধে privacy@shikkhasathi.com-এ লিখুন।'], ['From your profile you can view and edit your data, turn off notifications, sign out devices, disconnect Classroom and delete your account. For anything else write to privacy@shikkhasathi.com.']),
  ],
};

export const TERMS: LegalDoc = {
  title: { bn: 'ব্যবহারের শর্তাবলি', en: 'Terms of Service' },
  updated: UPDATED,
  sections: [
    s('সেবার বিবরণ', 'The service', ['শিক্ষাসাথী শিক্ষার্থীদের Google Classroom-এর তথ্য এক জায়গায় দেখায় এবং স্বাধীন শিক্ষকদের বানানো কোর্স কেনার সুযোগ দেয়। আমরা কোনো বিশ্ববিদ্যালয়ের সঙ্গে অফিসিয়ালি যুক্ত নই এবং কোনো ফলাফলের নিশ্চয়তা দিই না।'], ['ShikkhaSathi shows students their Google Classroom information in one place and lets them buy courses made by independent teachers. We are not officially affiliated with any university and guarantee no results.']),
    s('"সম্ভাব্য গুরুত্বপূর্ণ টপিক"', '"Likely important topics"', ['কোর্সে চিহ্নিত টপিক শিক্ষকের মতামত — পরীক্ষায় কী আসবে তার নিশ্চয়তা নয়। পুরো সিলেবাস পড়ার দায়িত্ব শিক্ষার্থীর।'], ["Topics marked in courses are the teacher's opinion — not a guarantee of what will be in the exam. Students remain responsible for the full syllabus."]),
    s('অ্যাকাউন্ট', 'Accounts', ['একটি অ্যাকাউন্ট একজন ব্যক্তির জন্য। কোর্স একসাথে সর্বোচ্চ ৩টি ডিভাইসে দেখা যায়; অ্যাকাউন্ট শেয়ার বা ভিডিও রেকর্ড/বিতরণ করলে অ্যাকাউন্ট বন্ধ হতে পারে।'], ['One account per person. Courses can be watched on up to 3 devices; sharing accounts or recording/distributing videos may lead to suspension.']),
    s('শিক্ষকদের দায়িত্ব', 'Teacher responsibilities', ['শিক্ষক নিশ্চিত করবেন কনটেন্ট তাঁর নিজের বা ব্যবহারের অনুমতি আছে, কোনো "নিশ্চিত প্রশ্ন" দাবি নেই, এবং তাঁর প্রতিষ্ঠানের নিয়ম মেনে চলছেন। প্ল্যাটফর্ম কমিশন ও পেআউটের শর্ত Teacher Studio-তে দেখানো হয়।'], ["Teachers confirm content is their own or licensed, makes no 'guaranteed question' claims, and complies with their institution's rules. Platform commission and payout terms are shown in Teacher Studio."]),
    s('পেমেন্ট ও রিফান্ড', 'Payments and refunds', ['দাম বাংলাদেশি টাকায়। পেমেন্ট সার্ভার থেকে যাচাই হওয়ার পর কোর্স খোলে। রিফান্ডের নিয়ম রিফান্ড নীতিতে আছে।'], ['Prices are in BDT. Courses open after the payment is verified by our server. See the Refund Policy for refunds.']),
    s('কপিরাইট ও অভিযোগ', 'Copyright and complaints', ['কোনো কনটেন্ট আপনার কপিরাইট লঙ্ঘন করলে copyright@shikkhasathi.com-এ লিখুন; আমরা যাচাই করে সরিয়ে ফেলব।'], ['If any content infringes your copyright, write to copyright@shikkhasathi.com; we will review and remove it.']),
  ],
};

export const REFUND: LegalDoc = {
  title: { bn: 'রিফান্ড নীতি', en: 'Refund Policy' },
  updated: UPDATED,
  sections: [
    s('কখন রিফান্ড পাবেন', 'When you can get a refund', ['কেনার ৪৮ ঘণ্টার মধ্যে এবং কোর্সের ২০%-এর কম দেখা হলে আমার কোর্স → অর্ডার থেকে রিফান্ডের অনুরোধ করতে পারবেন।'], ['Within 48 hours of purchase and if you have watched less than 20% of the course, request a refund from My Courses → Orders.']),
    s('কীভাবে ফেরত যায়', 'How the money is returned', ['অনুমোদনের পর যে মাধ্যমে পেমেন্ট করেছেন (bKash/Nagad/কার্ড) সেখানে ৩–৭ কার্যদিবসে ফেরত যায়। ইমেইলে আপডেট জানানো হয়।'], ['After approval the money returns to the original method (bKash/Nagad/card) in 3–7 business days. You get an email update.']),
    s('পেমেন্ট আটকে গেলে', 'If a payment gets stuck', ['টাকা কেটে কোর্স না খুললে চিন্তা করবেন না — আমাদের সিস্টেম প্রতি ঘণ্টায় গেটওয়ের সাথে মিলিয়ে দেখে। ২৪ ঘণ্টায় সমাধান না হলে support@shikkhasathi.com-এ লেনদেন নম্বরসহ লিখুন।'], ['If money was deducted but the course did not open, our system re-checks with the gateway every hour. If not resolved in 24 hours, email support@shikkhasathi.com with the transaction ID.']),
  ],
};

export const ABOUT: LegalDoc = {
  title: { bn: 'আমাদের সম্পর্কে', en: 'About us' },
  updated: UPDATED,
  sections: [
    s('কেন শিক্ষাসাথী', 'Why ShikkhaSathi', ['ছয়টা Classroom ক্লাস আলাদা করে খুলে দেখা, Facebook গ্রুপে নোট খোঁজা আর পরীক্ষার আগের রাতে ভালো শিক্ষক খুঁজে না পাওয়া — এই সমস্যাগুলো সমাধান করতেই শিক্ষাসাথী। সিলেটের Metropolitan University দিয়ে শুরু।'], ['Opening six Classroom classes one by one, hunting for notes in Facebook groups, and not finding a good teacher the night before the exam — ShikkhaSathi exists to fix that. We started with Metropolitan University, Sylhet.']),
  ],
};

export const CONTACT: LegalDoc = {
  title: { bn: 'যোগাযোগ', en: 'Contact' },
  updated: UPDATED,
  sections: [
    s('সাপোর্ট', 'Support', ['ইমেইল: support@shikkhasathi.com (সাধারণত ২৪ ঘণ্টার মধ্যে উত্তর)', 'প্রাইভেসি: privacy@shikkhasathi.com · কপিরাইট: copyright@shikkhasathi.com', 'শিক্ষক হিসেবে যোগ দিতে: teach@shikkhasathi.com'], ['Email: support@shikkhasathi.com (usually answered within 24 hours)', 'Privacy: privacy@shikkhasathi.com · Copyright: copyright@shikkhasathi.com', 'Teach on ShikkhaSathi: teach@shikkhasathi.com']),
  ],
};
