package com.shikkhasathi.common;

/** Error codes + HTTP status + default Bangla message (spec §14). Same set as the previous API. */
public enum ErrorCode {
  BAD_REQUEST(400, "অনুরোধটি ঠিক বোঝা যায়নি।"),
  VALIDATION_FAILED(422, "কিছু তথ্য ঠিক নেই, একবার দেখে নিন।"),
  UNAUTHORIZED(401, "অনুগ্রহ করে আবার লগইন করুন।"),
  FORBIDDEN(403, "এই কাজের অনুমতি আপনার নেই।"),
  NOT_FOUND(404, "যা খুঁজছেন তা পাওয়া যায়নি।"),
  CONFLICT(409, "এটি আগেই করা হয়েছে।"),
  RATE_LIMITED(429, "একটু বেশি চেষ্টা হয়ে গেছে। কিছুক্ষণ পর আবার চেষ্টা করুন।"),
  PAYMENT_FAILED(402, "পেমেন্ট সম্পন্ন হয়নি। চিন্তা নেই, আপনার টাকা কাটা হয়নি।"),
  INTEGRATION_DISABLED(503, "এই সার্ভিসটি এখন চালু নেই।"),
  CLASSROOM_REAUTH(409, "Google Classroom-এর সংযোগ বিচ্ছিন্ন হয়েছে। আবার সংযুক্ত করুন।"),
  CLASSROOM_WORKSPACE_BLOCKED(409, "আপনার বিশ্ববিদ্যালয়ের Google Workspace বাইরের অ্যাপ বন্ধ রেখেছে।"),
  CLASSROOM_QUOTA(429, "Google এখন ব্যস্ত। একটু পরে আবার সিঙ্ক হবে।"),
  INTERNAL(500, "দুঃখিত, আমাদের দিক থেকে সমস্যা হয়েছে। আবার চেষ্টা করুন।");

  public final int status;
  public final String message;

  ErrorCode(int status, String message) {
    this.status = status;
    this.message = message;
  }
}
