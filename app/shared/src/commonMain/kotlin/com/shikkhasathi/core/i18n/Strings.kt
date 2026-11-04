package com.shikkhasathi.core.i18n

/**
 * All user-facing copy of the app, Bangla first (CLAUDE.md §5.1 "no hardcoded UI text" applies here too).
 *
 * Keys use the same dotted namespace as the web client's i18n catalogue (`website/src/lib/i18n`,
 * e.g. `auth.title`, `nav.home`, `home.greeting`), and placeholders use the i18next `{{name}}` syntax,
 * so one JSON catalogue can later generate both sides. A key that exists on web must mean the same thing here.
 */
enum class AppLocale { Bn, En }

@Suppress("MaxLineLength")
enum class Str(val key: String, val bn: String, val en: String) {
    AppName("app.name", "শিক্ষাসাথী", "ShikkhaSathi"),

    // --- common ---
    CommonRetry("common.retry", "আবার চেষ্টা করুন", "Try again"),
    CommonOr("common.or", "অথবা", "or"),
    CommonPleaseWait("common.pleaseWait", "অপেক্ষা করুন", "Please wait"),
    CommonBack("common.back", "ফিরে যান", "Back"),
    ErrorTitle("error.title", "দুঃখিত, লোড করা গেল না", "Sorry, this didn’t load"),
    ErrorNetwork(
        "error.network",
        "ইন্টারনেট সংযোগ পাওয়া যাচ্ছে না। সংযোগ দেখে আবার চেষ্টা করুন।",
        "No internet connection. Check your connection and try again."
    ),
    ErrorGeneric(
        "error.generic",
        "কিছু একটা ঠিকমতো হয়নি। একটু পরে আবার চেষ্টা করুন।",
        "Something went wrong. Please try again in a moment."
    ),
    ErrorRateLimited(
        "error.rateLimited",
        "অনেকবার চেষ্টা করা হয়েছে। কিছুক্ষণ পরে আবার চেষ্টা করুন।",
        "Too many attempts. Please wait a little and try again."
    ),
    ErrorSessionExpired("error.sessionExpired", "আপনার সেশন শেষ হয়েছে, আবার লগইন করুন।", "Your session has ended. Please sign in again."),

    // --- auth ---
    AuthTitle("auth.title", "শিক্ষাসাথীতে লগইন করুন", "Sign in to ShikkhaSathi"),
    AuthSubtitle(
        "auth.subtitle",
        "মোবাইল নম্বর দিন — আমরা একটি ৬ সংখ্যার কোড পাঠাব।",
        "Enter your mobile number — we’ll text you a 6-digit code."
    ),
    AuthPhoneLabel("auth.phone.label", "মোবাইল নম্বর", "Mobile number"),
    AuthPhonePlaceholder("auth.phone.placeholder", "01XXXXXXXXX", "01XXXXXXXXX"),
    AuthPhoneInvalid("auth.phone.invalid", "সঠিক মোবাইল নম্বর দিন (01XXXXXXXXX)", "Enter a valid mobile number (01XXXXXXXXX)"),
    AuthOtpSend("auth.otp.send", "কোড পাঠান", "Send code"),
    AuthOtpTitle("auth.otp.title", "কোডটি লিখুন", "Enter the code"),
    AuthOtpSentTo("auth.otp.sentTo", "{{phone}} নম্বরে ৬ সংখ্যার কোড পাঠানো হয়েছে", "We sent a 6-digit code to {{phone}}"),
    AuthOtpVerify("auth.otp.verify", "যাচাই করুন", "Verify"),
    AuthOtpResendIn("auth.otp.resendIn", "{{seconds}} সেকেন্ড পর আবার কোড পাঠাতে পারবেন", "You can resend in {{seconds}}s"),
    AuthOtpResend("auth.otp.resend", "আবার কোড পাঠান", "Resend code"),
    AuthOtpChangeNumber("auth.otp.changeNumber", "নম্বর বদলান", "Change number"),
    AuthOtpInvalid("auth.otp.invalid", "৬ সংখ্যার কোড দিন", "Enter the 6-digit code"),
    AuthOtpDigit("auth.otp.digit", "কোডের {{index}} নম্বর সংখ্যা", "Code digit {{index}}"),
    AuthOtpDevCode("auth.otp.devCode", "ডেভেলপমেন্ট কোড: {{code}}", "Development code: {{code}}"),
    AuthEmailCta("auth.email.cta", "ইমেইল দিয়ে লগইন করুন", "Sign in with email"),
    AuthEmailTitle("auth.email.title", "ইমেইল দিয়ে লগইন", "Sign in with email"),
    AuthEmailLabel("auth.email.label", "ইমেইল", "Email"),
    AuthEmailInvalid("auth.email.invalid", "সঠিক ইমেইল দিন", "Enter a valid email"),
    AuthPasswordLabel("auth.password.label", "পাসওয়ার্ড", "Password"),
    AuthPasswordRequired("auth.password.required", "পাসওয়ার্ড লিখুন", "Enter your password"),
    AuthEmailSubmit("auth.email.submit", "লগইন", "Sign in"),
    AuthPhoneCta("auth.phone.cta", "মোবাইল নম্বর দিয়ে লগইন করুন", "Sign in with mobile number"),
    AuthGoogle("auth.google", "Google দিয়ে চালিয়ে যান", "Continue with Google"),
    AuthGoogleUnavailable(
        "auth.google.unavailable",
        "এই ভার্সনে Google লগইন এখনো চালু হয়নি। মোবাইল নম্বর দিয়ে লগইন করুন।",
        "Google sign-in isn’t available in this version yet. Use your mobile number."
    ),
    AuthTerms(
        "auth.terms",
        "চালিয়ে গেলে আপনি শিক্ষাসাথীর শর্তাবলি ও গোপনীয়তা নীতিতে সম্মত হচ্ছেন।",
        "By continuing you agree to ShikkhaSathi’s Terms and Privacy Policy."
    ),

    // --- navigation (CLAUDE.md §0A / spec §5.3) ---
    NavHome("nav.home", "হোম", "Home"),
    NavClassroom("nav.classroom", "ক্লাসরুম", "Classroom"),
    NavCourses("nav.courses", "কোর্স", "Courses"),
    NavExam("nav.exam", "Exam", "Exam"),
    NavProfile("nav.profile", "প্রোফাইল", "Profile"),
    NavExplore("nav.explore", "কোর্স খুঁজুন", "Find courses"),
    NavMockTests("nav.mockTests", "Mock Test", "Mock tests"),
    NavNotes("nav.notes", "নোট", "Notes"),
    NavRequests("nav.requests", "কোর্স অনুরোধ", "Course requests"),
    NavNotifications("nav.notifications", "নোটিফিকেশন", "Notifications"),
    NavSettings("nav.settings", "সেটিংস", "Settings"),
    NavTeacherStudio("nav.teacherStudio", "Teacher Studio", "Teacher Studio"),
    NavOpenMenu("nav.openMenu", "মেনু খুলুন", "Open menu"),

    // --- home dashboard ---
    HomeGreeting("home.greeting", "আসসালামু আলাইকুম, {{name}}", "Assalamu alaikum, {{name}}"),
    HomeGreetingFallbackName("home.greeting.fallbackName", "শিক্ষার্থী", "student"),
    HomeClassroomConnectTitle("home.classroom.connect.title", "Google Classroom সংযুক্ত করুন", "Connect Google Classroom"),
    HomeClassroomConnectBody(
        "home.classroom.connect.body",
        "সব অ্যাসাইনমেন্ট আর নোটিশ এক জায়গায়",
        "All your assignments and announcements in one place"
    ),
    HomeClassroomConnectPrivacy("home.classroom.connect.privacy", "আমরা শুধু পড়ার অনুমতি চাই", "We only ask for read access"),
    HomeClassroomConnectCta("home.classroom.connect.cta", "সংযুক্ত করুন", "Connect"),
    HomeCoursesTitle("home.courses.title", "পড়া চালিয়ে যান", "Continue learning"),
    HomeCoursesEmptyTitle("home.courses.empty.title", "এখনো কোনো কোর্স শুরু করেননি", "You haven’t started a course yet"),
    HomeCoursesEmptyBody(
        "home.courses.empty.body",
        "আপনার ইউনিভার্সিটির সিলেবাসের সাথে মিলিয়ে Mid ও Final-এর কোর্স খুঁজে নিন।",
        "Find mid and final exam courses that match your university syllabus."
    ),

    CommonSeeAll("common.seeAll", "সব দেখুন", "See all"),
    CommonClose("common.close", "বন্ধ করুন", "Close"),
    CommonLoadMore("common.loadMore", "আরও দেখুন", "Show more"),
    BrandDisclaimerGoogle("brand.disclaimerGoogle", "Google-এর সঙ্গে যুক্ত নয়", "Not affiliated with Google"),
    NavAssignments("nav.assignments", "অ্যাসাইনমেন্ট", "Assignments"),
    NavCalendar("nav.calendar", "ক্যালেন্ডার", "Calendar"),

    // --- time (web: time.*, due.*) ---
    TimeJustNow("time.justNow", "এইমাত্র", "just now"),
    TimeMinutesAgo("time.minutesAgo", "{{n}} মিনিট আগে", "{{n}} min ago"),
    TimeHoursAgo("time.hoursAgo", "{{n}} ঘণ্টা আগে", "{{n}} h ago"),
    TimeDaysAgo("time.daysAgo", "{{n}} দিন আগে", "{{n}} days ago"),
    DueTodayAt("due.todayAt", "আজ {{time}}", "Today {{time}}"),
    DueTomorrowAt("due.tomorrowAt", "আগামীকাল {{time}}", "Tomorrow {{time}}"),
    DueYesterdayAt("due.yesterdayAt", "গতকাল {{time}}", "Yesterday {{time}}"),
    DueDateAt("due.dateAt", "{{day}}, {{date}}, {{time}}", "{{day}}, {{date}}, {{time}}"),
    DueMinutesLeft("due.minutesLeft", "আর {{n}} মিনিট", "{{n}} min left"),
    DueHoursLeft("due.hoursLeft", "আর {{n}} ঘণ্টা", "{{n}} h left"),
    DueDaysLeft("due.daysLeft", "{{n}} দিন বাকি", "{{n}} days left"),
    DueOverdue("due.overdue", "মেয়াদ পেরিয়েছে", "Overdue"),
    DueToday("due.today", "আজ", "Today"),
    DueTomorrow("due.tomorrow", "আগামীকাল", "Tomorrow"),
    DueThisWeek("due.thisWeek", "এই সপ্তাহ", "This week"),

    // --- Google Classroom (web: gc.*) ---
    GcSynced("gc.synced", "সিঙ্ক হয়েছে", "Synced"),
    GcSyncing("gc.syncing", "সিঙ্ক হচ্ছে…", "Syncing…"),
    GcSyncedAgo("gc.syncedAgo", "সিঙ্ক হয়েছে {{when}}", "Synced {{when}}"),
    GcNotSynced("gc.notSynced", "এখনো সিঙ্ক হয়নি", "Not synced yet"),
    GcRefresh("gc.refresh", "এখনই সিঙ্ক করুন", "Sync now"),
    GcSyncRateLimited(
        "gc.syncRateLimited",
        "এইমাত্র সিঙ্ক হয়েছে — এক মিনিট পর আবার চেষ্টা করুন",
        "Just synced — try again in a minute"
    ),
    GcQuota(
        "gc.quota",
        "Google এখন একটু অপেক্ষা করতে বলছে। শেষ সিঙ্ক: {{when}} — কিছুক্ষণ পর নিজে থেকেই আবার সিঙ্ক হবে।",
        "Google asked us to wait a little. Last synced {{when}} — we’ll sync again on our own shortly."
    ),
    GcQuotaTitle("gc.quotaTitle", "Google একটু বিরতি চাইছে", "Google needs a short break"),
    GcConnectBody(
        "gc.connectBody",
        "সব অ্যাসাইনমেন্ট আর নোটিশ এক জায়গায় — ডেডলাইনের আগে রিমাইন্ডারও পাবেন।",
        "All assignments and announcements in one place — with reminders before deadlines."
    ),
    GcReauthTitle("gc.reauthTitle", "Classroom-এর সংযোগ বিচ্ছিন্ন হয়েছে", "Classroom got disconnected"),
    GcReauthBody(
        "gc.reauthBody",
        "Google অনুমতি বাতিল বা মেয়াদ শেষ হয়েছে। আবার সংযুক্ত করলে সব ঠিক হয়ে যাবে।",
        "Google access was revoked or expired. Reconnect and everything will be back."
    ),
    GcReconnect("gc.reconnect", "আবার সংযুক্ত করুন", "Reconnect"),
    GcBlockedTitle(
        "gc.blockedTitle",
        "আপনার বিশ্ববিদ্যালয়ের Google অ্যাকাউন্ট বাইরের অ্যাপ বন্ধ রেখেছে",
        "Your university’s Google account blocks outside apps"
    ),
    GcBlockedBody(
        "gc.blockedBody",
        "এটা আপনার ভুল নয় — বিশ্ববিদ্যালয়ের Workspace অ্যাডমিন অনুমতি দিলে কাজ করবে।",
        "It isn’t your fault — it works once your university’s Workspace admin allows it."
    ),
    GcBlockedWhy("gc.blockedWhy", "কী করতে পারি?", "What can I do?"),
    GcBlockedHelp(
        "gc.blockedHelp",
        "আপনার বিভাগের IT অফিসকে ShikkhaSathi (shikkhasathi.com) অ্যাপটি অনুমোদন করতে বলুন। অথবা যদি Classroom-এ ব্যক্তিগত Gmail দিয়ে যুক্ত থাকেন, সেই অ্যাকাউন্ট দিয়ে সংযুক্ত করুন।",
        "Ask your department’s IT office to allow the ShikkhaSathi (shikkhasathi.com) app. Or, if you joined Classroom with a personal Gmail, connect that account."
    ),
    GcConnectError("gc.connectError", "সংযোগ হয়নি। একটু পরে আবার চেষ্টা করুন।", "Couldn’t connect. Please try again in a moment."),
    GcConnectedToast(
        "gc.connectedToast",
        "Google Classroom সংযুক্ত হয়েছে — ক্লাসগুলো সিঙ্ক হচ্ছে",
        "Google Classroom connected — syncing your classes"
    ),
    GcDeniedToast(
        "gc.deniedToast",
        "সংযোগ বাতিল করা হয়েছে। যখন খুশি আবার চেষ্টা করতে পারবেন।",
        "Connection cancelled. You can try again any time."
    ),
    GcConnectUnavailable(
        "gc.connectUnavailable",
        "এই ডিভাইসে Google সংযোগ চালু করা যায়নি। Google Play সার্ভিস আপডেট করে আবার চেষ্টা করুন।",
        "Google connection isn’t available on this device. Update Google Play services and try again."
    ),
    GcConnecting("gc.connecting", "Google-এর অনুমতি চাওয়া হচ্ছে…", "Asking Google for permission…"),
    GcOpenInClassroom("gc.openInClassroom", "Classroom-এ খুলুন", "Open in Classroom"),
    GcLate("gc.late", "দেরিতে জমা", "Turned in late"),
    GcGrade("gc.grade", "গ্রেড", "Grade"),
    GcGradeValue("gc.gradeValue", "{{grade}}/{{max}}", "{{grade}}/{{max}}"),
    GcDue("gc.due", "জমার সময়", "Due"),
    GcNoDue("gc.noDue", "কোনো ডেডলাইন নেই", "No due date"),
    GcPoints("gc.points", "নম্বর", "Points"),
    GcPosted("gc.posted", "পোস্ট", "Posted"),
    GcReadOnlyNote(
        "gc.readOnlyNote",
        "শিক্ষাসাথী শুধু দেখায় — জমা দিতে বা কিছু বদলাতে Classroom-এ খুলুন।",
        "ShikkhaSathi only shows your work — open Classroom to submit or change anything."
    ),
    GcUnread("gc.unread", "নতুন", "New"),
    GcKindAssignment("gc.kind.assignment", "অ্যাসাইনমেন্ট", "Assignment"),
    GcKindMaterial("gc.kind.material", "ম্যাটেরিয়াল", "Material"),
    GcKindAnnouncement("gc.kind.announcement", "ঘোষণা", "Announcement"),
    GcKindGraded("gc.kind.graded", "গ্রেড", "Grade returned"),
    GcMaterials("gc.materials", "সংযুক্ত ফাইল", "Attachments"),

    // --- home dashboard widgets (web: home.*) ---
    HomeSemester("home.semester", "সেমিস্টার {{n}}", "Semester {{n}}"),
    HomeAddAcademic("home.addAcademic", "ইউনিভার্সিটি ও সেমিস্টার যোগ করুন", "Add university and semester"),
    HomeBellUnread("home.bellUnread", "নোটিফিকেশন, {{n}}টি অপঠিত", "Notifications, {{n}} unread"),
    HomeDueSoon("home.dueSoon", "সামনের অ্যাসাইনমেন্ট", "Due soon"),
    HomeFeed("home.feed", "নতুন আপডেট", "New from your classes"),
    HomeNewCount("home.newCount", "{{n}}টি নতুন", "{{n}} new"),
    HomeMarkAllRead("home.markAllRead", "সব পড়া হয়েছে", "Mark all read"),
    HomeFeedEmpty(
        "home.feedEmpty",
        "এখনো কোনো আপডেট নেই। শিক্ষক কিছু পোস্ট করলে এখানে দেখাবে।",
        "No updates yet. Posts from your teachers will show up here."
    ),
    HomeAllDone("home.allDone", "সামনে কোনো অ্যাসাইনমেন্ট জমা বাকি নেই", "Nothing due coming up"),
    HomeAllDoneBody("home.allDoneBody", "দারুণ! নতুন কিছু এলে এখানেই দেখাবে।", "Nice! New work will show up right here."),
    HomeWidgetError("home.widgetError", "এই অংশটি লোড হয়নি", "This section didn’t load"),
    HomeOffline("home.offline", "অফলাইন — শেষ সংরক্ষিত তথ্য দেখাচ্ছি", "Offline — showing the last saved data"),
    HomeNoExam("home.noExam", "সামনের পরীক্ষার তারিখ দিন", "Add your next exam date"),
    HomeNoExamBody("home.noExamBody", "কাউন্টডাউন আর প্রতিদিনের প্ল্যান পাবেন", "Get a countdown and a daily plan"),
    HomeAddExam("home.addExam", "যোগ করুন", "Add"),
    HomeLastLesson("home.lastLesson", "শেষ দেখা: {{title}}", "Last watched: {{title}}"),
    HomeProgress("home.progress", "কোর্স {{p}}% শেষ", "{{p}}% complete"),
    HomeResume("home.resume", "চালিয়ে যান", "Resume"),
    HomeWeek("home.week", "এই সপ্তাহ", "This week"),
    HomeLegendDeadline("home.legendDeadline", "ডেডলাইন", "Deadline"),
    HomeLegendExam("home.legendExam", "পরীক্ষা", "Exam"),
    HomeForYou("home.forYou", "আপনার জন্য কোর্স", "Courses for you"),
    WeekSummary("week.summary", "{{d}}টি ডেডলাইন, {{e}}টি পরীক্ষা", "{{d}} deadlines, {{e}} exams"),
    ExamToday("exam.today", "আজ পরীক্ষা", "Exam today"),
    ExamDaysLeft("exam.daysLeft", "{{n}} দিন বাকি", "{{n}} days left"),
    ExamReadiness("exam.readiness", "প্রস্তুতি {{p}}%", "{{p}}% ready"),
    ExamOpen("exam.open", "Exam Mode খুলুন", "Open Exam Mode"),
    ExamTypeMidterm("examType.midterm", "Midterm", "Midterm"),
    ExamTypeFinal("examType.final", "Final", "Final"),
    ExamTypeQuiz("examType.quiz", "Quiz", "Quiz"),
    ExamTypeLab("examType.lab", "Lab", "Lab"),
    ExamTypeViva("examType.viva", "Viva", "Viva"),
    ExamTypeAdmission("examType.admission", "ভর্তি", "Admission"),
    ExamTypeGeneral("examType.general", "সাধারণ", "General"),
    CourseVerified("course.verified", "ভেরিফায়েড শিক্ষক", "Verified teacher"),
    CompatMatch("compat.match", "আপনার ইউনিভার্সিটির সিলেবাস", "Matches your university syllabus"),
    CompatMismatch("compat.mismatch", "ভিন্ন সিলেবাস হতে পারে", "Syllabus may differ"),
    CoursePrice("course.price", "৳{{price}}", "৳{{price}}"),

    // --- classroom screens ---
    ClassroomClassesEmptyTitle("classroom.classes.emptyTitle", "Classroom-এ কোনো ক্লাস পাওয়া যায়নি", "No classes found in Classroom"),
    ClassroomClassesEmptyBody(
        "classroom.classes.emptyBody",
        "যে Google অ্যাকাউন্ট দিয়ে ক্লাসে যুক্ত আছেন সেটিই সংযুক্ত কিনা দেখুন। নতুন ক্লাস যোগ হলে সিঙ্কের পর এখানে আসবে।",
        "Check that you connected the Google account you joined your classes with. New classes appear here after a sync."
    ),
    ClassroomPendingCount("classroom.pendingCount", "{{n}}টি বাকি", "{{n}} pending"),
    ClassroomNextDue("classroom.nextDue", "পরের ডেডলাইন: {{when}}", "Next due: {{when}}"),
    ClassroomTabStream("classroom.tab.stream", "স্ট্রিম", "Stream"),
    ClassroomTabAssignments("classroom.tab.assignments", "অ্যাসাইনমেন্ট", "Assignments"),
    ClassroomTabMaterials("classroom.tab.materials", "ম্যাটেরিয়াল", "Materials"),
    ClassroomStreamEmptyTitle("classroom.stream.emptyTitle", "এই ক্লাসে এখনো কিছু পোস্ট হয়নি", "Nothing posted in this class yet"),
    ClassroomStreamEmptyBody(
        "classroom.stream.emptyBody",
        "শিক্ষক ঘোষণা, অ্যাসাইনমেন্ট বা ম্যাটেরিয়াল দিলে এখানে দেখাবে।",
        "Announcements, assignments and materials from your teacher will show up here."
    ),
    ClassroomAssignmentsEmptyTitle("classroom.assignments.emptyTitle", "এই ক্লাসে কোনো অ্যাসাইনমেন্ট নেই", "No assignments in this class"),
    ClassroomMaterialsEmptyTitle("classroom.materials.emptyTitle", "এই ক্লাসে কোনো ম্যাটেরিয়াল নেই", "No materials in this class"),
    ClassroomMaterialsEmptyBody(
        "classroom.materials.emptyBody",
        "শিক্ষক PDF, স্লাইড বা লিংক দিলে এখানে এক জায়গায় পাবেন।",
        "PDFs, slides and links your teacher shares will be collected here."
    ),
    AssignmentsFilterAll("assignments.filter.all", "সব", "All"),
    AssignmentsFilterUpcoming("assignments.filter.upcoming", "সামনের", "Upcoming"),
    AssignmentsFilterAllClasses("assignments.filter.allClasses", "সব ক্লাস", "All classes"),
    AssignmentsEmptyTitle("assignments.empty.title", "এই ফিল্টারে কোনো অ্যাসাইনমেন্ট নেই", "No assignments match this filter"),
    AssignmentsEmptyBody("assignments.empty.body", "অন্য স্ট্যাটাস বা ক্লাস বেছে দেখুন।", "Try another status or class."),
    AssignmentsClearFilters("assignments.clearFilters", "ফিল্টার মুছুন", "Clear filters"),
    CalendarPrevMonth("calendar.prevMonth", "আগের মাস", "Previous month"),
    CalendarNextMonth("calendar.nextMonth", "পরের মাস", "Next month"),
    CalendarDayEmpty("calendar.dayEmpty", "এই দিনে কোনো ডেডলাইন বা পরীক্ষা নেই", "No deadlines or exams on this day"),
    CalendarDayEmptyBody(
        "calendar.dayEmptyBody",
        "অন্য দিনে ট্যাপ করুন, বা পুরো তালিকা দেখতে অ্যাসাইনমেন্টে যান।",
        "Tap another day, or open Assignments for the full list."
    ),
    CalendarDayA11y("calendar.dayLabel", "{{date}}: {{summary}}", "{{date}}: {{summary}}"),
    NotificationsUnreadOnly("notifications.unreadOnly", "শুধু অপঠিত", "Unread only"),

    // --- status chips ---
    StatusPending("status.pending", "বাকি", "Pending"),
    StatusTurnedIn("status.turnedIn", "জমা হয়েছে", "Turned in"),
    StatusGraded("status.graded", "গ্রেড হয়েছে", "Graded"),
    StatusMissing("status.missing", "মিস হয়েছে", "Missing"),

    // --- placeholder screens (features not built yet) ---
    ClassroomEmptyTitle("classroom.empty.title", "ক্লাসরুম এখনো সংযুক্ত হয়নি", "Classroom isn’t connected yet"),
    ClassroomEmptyBody(
        "classroom.empty.body",
        "Google Classroom সংযুক্ত করলে আপনার সব ক্লাস, অ্যাসাইনমেন্ট আর ম্যাটেরিয়াল এখানে দেখাবে।",
        "Connect Google Classroom to see all your classes, assignments and materials here."
    ),
    CoursesEmptyTitle("myCourses.empty.title", "এখনো কোনো কোর্স নেই", "No courses yet"),
    CoursesEmptyBody(
        "myCourses.empty.body",
        "কেনা কোর্সগুলো এখানে থাকবে — যেখানে থেমেছিলেন সেখান থেকেই আবার শুরু করবেন।",
        "Courses you buy live here — pick up right where you left off."
    ),
    ExamEmptyTitle("examMode.empty.title", "পরীক্ষার তারিখ সেট করুন", "Set your exam date"),
    ExamEmptyBody(
        "examMode.empty.body",
        "Mid বা Final-এর তারিখ দিলে দিন গুনে পড়ার পরিকল্পনা সাজিয়ে দেব।",
        "Add your mid or final date and we’ll plan your revision day by day."
    ),
    ExploreEmptyTitle("explore.empty.title", "বিষয় বা কোর্স কোড দিয়ে খুঁজুন", "Search by subject or course code"),
    ExploreEmptyBody(
        "explore.empty.body",
        "যেমন CSE-2203 Data Structures — খোঁজার সুবিধা পরের আপডেটে এই স্ক্রিনে আসছে।",
        "For example CSE-2203 Data Structures — search arrives on this screen in the next update."
    ),
    MockTestsEmptyTitle("mockTests.empty.title", "এখনো কোনো Mock Test দেননি", "No mock tests taken yet"),
    MockTestsEmptyBody(
        "mockTests.empty.body",
        "কোর্স কিনলে সেই কোর্সের টাইমড Mock Test এখানে পাবেন।",
        "Timed mock tests from your courses will appear here."
    ),
    NotesEmptyTitle("notes.empty.title", "কোনো নোট বা বুকমার্ক নেই", "No notes or bookmarks"),
    NotesEmptyBody(
        "notes.empty.body",
        "লেসন দেখার সময় নোট নিলে বা বুকমার্ক করলে সেগুলো এখানে জমা থাকবে।",
        "Notes and bookmarks you make while watching lessons are kept here."
    ),
    RequestsEmptyTitle("requests.empty.title", "যে কোর্স খুঁজছেন পাচ্ছেন না?", "Can’t find the course you need?"),
    RequestsEmptyBody(
        "requests.empty.body",
        "বিষয় আর কোর্স কোড জানিয়ে অনুরোধ করুন — শিক্ষকরা অনুরোধ দেখে নতুন কোর্স বানান।",
        "Request it with the subject and course code — teachers build new courses from requests."
    ),
    NotificationsEmptyTitle("notifications.empty.title", "কোনো নতুন নোটিফিকেশন নেই", "No new notifications"),
    NotificationsEmptyBody(
        "notifications.empty.body",
        "নতুন অ্যাসাইনমেন্ট, গ্রেড আর ডেডলাইনের রিমাইন্ডার এখানে আসবে।",
        "New assignments, grades and deadline reminders will show up here."
    ),
    SettingsEmptyTitle("settings.empty.title", "সেটিংস", "Settings"),
    SettingsEmptyBody(
        "settings.empty.body",
        "নোটিফিকেশন, থিম আর ভাষার সেটিংস পরের আপডেটে এখানে আসছে।",
        "Notification, theme and language settings arrive here in the next update."
    ),
    StudioEmptyTitle("studio.empty.title", "Teacher Studio", "Teacher Studio"),
    StudioEmptyBody(
        "studio.empty.body",
        "বিক্রি, প্রশ্নোত্তর আর রিভিউ দেখার সুবিধা পরের আপডেটে আসছে। পুরো স্টুডিও ওয়েবে: shikkhasathi.com/teach",
        "Sales, Q&A and reviews arrive in the next update. The full studio is on the web: shikkhasathi.com/teach"
    ),
    ProfileEmptyBody(
        "profile.empty.body",
        "একাডেমিক তথ্য ও প্রোফাইল সম্পাদনার সুবিধা পরের আপডেটে আসছে।",
        "Editing your academic details and profile arrives in the next update."
    ),
    ProfileLogout("profile.logout", "লগ আউট", "Sign out"),

    // --- Phase 11 (keys shared with the web catalogue where the web has them) ---
    NavDownloads("nav.downloads", "ডাউনলোড", "Downloads"),
    DurationMinutes("duration.minutes", "{{n}} মিনিট", "{{n}} min"),
    DurationHours("duration.hours", "{{n}} ঘণ্টা", "{{n}} h"),
    DurationHoursMinutes("duration.hoursMinutes", "{{h}} ঘণ্টা {{m}} মিনিট", "{{h}} h {{m}} min"),
    SizeKb("size.kb", "{{n}} KB", "{{n}} KB"),
    SizeMb("size.mb", "{{n}} MB", "{{n}} MB"),
    SizeGb("size.gb", "{{n}} GB", "{{n}} GB"),
    CheckoutCouponTooShort("checkout.couponTooShort", "কুপন কোডটি পুরো লিখুন", "Enter the full coupon code"),
    CheckoutCouponInvalid("checkout.couponInvalid", "এই কুপনটি এই কোর্সে চলবে না", "This coupon doesn’t work for this course"),
    CheckoutFailedBody(
        "checkout.failedBody",
        "পেমেন্ট সম্পন্ন হয়নি। টাকা কাটা হলে ২৪ ঘণ্টার মধ্যে ফেরত যাবে — আবার চেষ্টা করতে পারেন।",
        "The payment didn’t go through. If money was deducted it returns within 24 hours — you can try again."
    ),
    CheckoutBrowserMissing(
        "checkout.browserMissing",
        "পেমেন্ট পেজ খোলা যায়নি। ফোনে একটি ব্রাউজার (যেমন Chrome) চালু আছে কিনা দেখুন।",
        "Couldn’t open the payment page. Check that a browser (e.g. Chrome) is installed."
    ),
    PlayerLocked("player.locked", "এই লেসন দেখতে কোর্সটি কিনুন", "Buy the course to watch this lesson"),
    PlayerNoLessons("player.noLessons", "এই কোর্সে এখনো কোনো লেসন নেই", "This course has no lessons yet"),
    PlayerVideoError(
        "player.videoError",
        "ভিডিওটি চালানো যাচ্ছে না। ইন্টারনেট দেখে আবার চেষ্টা করুন।",
        "The video won’t play. Check your connection and try again."
    ),
    NotesBookmarked("notes.bookmarked", "বুকমার্ক করা হয়েছে", "Bookmarked"),
    QaTooShort("qa.tooShort", "প্রশ্নটা একটু খুলে লিখুন (অন্তত ৫ অক্ষর)", "Write a little more (at least 5 characters)"),
    QaAsked("qa.asked", "প্রশ্ন পাঠানো হয়েছে — উত্তর এলে নোটিফিকেশন পাবেন", "Question sent — you’ll be notified when it’s answered"),
    ExamPlanCreated("exam.planCreated", "পরীক্ষার প্ল্যান তৈরি হয়েছে", "Exam plan created"),
    ExamFormSubjectRequired("exam.form.subjectRequired", "বিষয়ের নাম লিখুন", "Enter the subject name"),
    ExamFormTypeRequired("exam.form.typeRequired", "পরীক্ষার ধরন বেছে নিন", "Choose the exam type"),
    ExamFormDateRequired("exam.form.dateRequired", "পরীক্ষার তারিখ দিন", "Pick the exam date"),
    ExamFormDatePast("exam.form.datePast", "তারিখটি আজ বা পরের কোনো দিন হতে হবে", "The date must be today or later"),
    MockAutoSubmitted("mt.autoSubmitted", "সময় শেষ — উত্তরগুলো স্বয়ংক্রিয়ভাবে জমা হয়েছে", "Time’s up — your answers were submitted"),
    DownloadStarted("download.started", "ডাউনলোড শুরু হয়েছে — ডাউনলোড পেজে দেখুন", "Download started — see Downloads"),
    DownloadUnavailable("download.unavailable", "এই লেসনটি এখন অফলাইনে রাখা যাচ্ছে না", "This lesson can’t be saved offline right now"),

    // --- common (Phase 11) ---
    CommonCancel("common.cancel", "বাতিল", "Cancel"),
    CommonDelete("common.delete", "মুছুন", "Delete"),
    CommonEdit("common.edit", "সম্পাদনা", "Edit"),
    CommonOk("common.ok", "ঠিক আছে", "OK"),
    CommonSave("common.save", "সেভ করুন", "Save"),
    BrandDisclaimerUniversity(
        "brand.disclaimerUniversity",
        "কোনো বিশ্ববিদ্যালয়ের সঙ্গে অফিসিয়ালি যুক্ত নয়",
        "Not officially affiliated with any university"
    ),
    NavMyCourses("nav.myCourses", "আমার কোর্স", "My courses"),
    NavExamMode("nav.examMode", "Exam Mode", "Exam Mode"),

    // --- explore + course page ---
    ExploreSearchPlaceholder(
        "explore.searchPlaceholder",
        "বিষয়, কোর্স কোড বা শিক্ষক (যেমন CSE-2203)",
        "Subject, course code or teacher (e.g. CSE-2203)"
    ),
    ExploreClear("explore.clear", "খোঁজা মুছুন", "Clear search"),
    ExploreInterpreted("explore.interpreted", "“{{term}}” মানে ধরে নিচ্ছি: {{meaning}}", "Treating “{{term}}” as: {{meaning}}"),
    ExploreResultCount("explore.resultCount", "{{n}}টি কোর্স", "{{n}} courses"),
    ExploreNoResultsTitle("explore.noResults.title", "মিলে যায় এমন কোর্স পাওয়া যায়নি", "No matching courses"),
    ExploreNoResultsBody(
        "explore.noResults.body",
        "কোর্স কোড বা বিষয়ের নাম অন্যভাবে লিখে দেখুন। না পেলে কোর্স অনুরোধ করুন — শিক্ষকরা অনুরোধ দেখে কোর্স বানান।",
        "Try the course code or subject name another way. Still nothing? Request the course — teachers build from requests."
    ),
    SuggestInstitution("explore.suggest.institution", "ইউনিভার্সিটি", "University"),
    SuggestSubject("explore.suggest.subject", "বিষয়", "Subject"),
    SuggestCode("explore.suggest.code", "কোর্স কোড", "Course code"),
    SuggestTeacher("explore.suggest.teacher", "শিক্ষক", "Teacher"),
    SuggestCourse("explore.suggest.course", "কোর্স", "Course"),
    SortBest("sort.best", "সবচেয়ে মানানসই", "Best match"),
    SortNewest("sort.newest", "নতুন", "Newest"),
    SortRating("sort.rating", "রেটিং", "Top rated"),
    SortPriceAsc("sort.priceAsc", "দাম: কম থেকে বেশি", "Price: low to high"),
    SortPriceDesc("sort.priceDesc", "দাম: বেশি থেকে কম", "Price: high to low"),
    SortPopular("sort.popular", "জনপ্রিয়", "Popular"),
    CourseFree("course.free", "ফ্রি", "Free"),
    CourseRating("course.rating", "{{avg}} ({{n}})", "{{avg}} ({{n}})"),
    CourseStudents("course.students", "{{n}} জন শিক্ষার্থী", "{{n}} students"),
    CourseLessonsLength("course.lessonsLength", "{{n}}টি লেসন · {{length}}", "{{n}} lessons · {{length}}"),
    CourseLearnings("course.learnings", "যা শিখবেন", "What you’ll learn"),
    CourseCurriculum("course.curriculum", "কোর্সের সূচি", "Curriculum"),
    CourseResources("course.resources", "কোর্সে যা যা আছে", "What’s included"),
    CourseNotFor("course.notFor", "এই কোর্স কাদের জন্য নয়", "Who this course is not for"),
    CourseFaq("course.faq", "সাধারণ প্রশ্ন", "FAQ"),
    CourseExamDisclaimer(
        "course.examDisclaimer",
        "“সম্ভাব্য গুরুত্বপূর্ণ টপিক” শিক্ষকের অভিজ্ঞতা থেকে বাছাই করা — পরীক্ষায় আসবেই এমন নিশ্চয়তা নেই।",
        "“Likely important topics” are picked from the teacher’s experience — not a guarantee of what the exam will ask."
    ),
    CourseBuy("course.buy", "কোর্সটি কিনুন", "Buy course"),
    CourseEnrollFree("course.enrollFree", "ফ্রি-তে এনরোল করুন", "Enroll for free"),
    CourseStart("course.start", "পড়া শুরু করুন", "Start learning"),
    CoursePreviewOwn("course.previewOwn", "নিজের কোর্স দেখুন", "Preview your course"),
    CourseWishlistAdd("course.wishlistAdd", "পরে দেখার জন্য সেভ করুন", "Save for later"),
    CourseWishlistRemove("course.wishlistRemove", "সেভ থেকে সরান", "Remove from saved"),
    ResourceVideos("course.res.videos", "{{n}}টি ভিডিও লেসন", "{{n}} video lessons"),
    ResourceNotes("course.res.notes", "{{n}}টি PDF নোট", "{{n}} PDF notes"),
    ResourcePreviousQuestions("course.res.previousQuestions", "আগের বছরের প্রশ্ন: {{years}}", "Previous questions: {{years}}"),
    ResourceMockTests("course.res.mockTests", "{{n}}টি মক টেস্ট", "{{n}} mock tests"),
    ResourceFlashcards("course.res.flashcards", "{{n}}টি ফ্ল্যাশকার্ড", "{{n}} flashcards"),
    LessonImportant("lesson.important", "সম্ভাব্য গুরুত্বপূর্ণ", "Likely important"),
    LessonPreview("lesson.preview", "ফ্রি প্রিভিউ", "Free preview"),
    LessonLocked("lesson.locked", "কোর্সটি কিনলে খুলবে", "Unlocks with the course"),

    // --- checkout ---
    CheckoutTitle("checkout.title", "পেমেন্ট", "Checkout"),
    CheckoutSubtotal("checkout.subtotal", "কোর্সের দাম", "Price"),
    CheckoutDiscount("checkout.discount", "কুপন ছাড়", "Coupon discount"),
    CheckoutTotal("checkout.total", "মোট", "Total"),
    CheckoutCouponLabel("checkout.couponLabel", "কুপন কোড", "Coupon code"),
    CheckoutCouponApply("checkout.couponApply", "প্রয়োগ করুন", "Apply"),
    CheckoutCouponApplied("checkout.couponApplied", "{{code}} প্রয়োগ হয়েছে — {{amount}} ছাড়", "{{code}} applied — {{amount}} off"),
    CheckoutCouponRemove("checkout.couponRemove", "সরান", "Remove"),
    CheckoutMethod("checkout.method", "কীভাবে পেমেন্ট করবেন?", "How would you like to pay?"),
    PayBkash("checkout.pay.bkash", "বিকাশ", "bKash"),
    PayNagad("checkout.pay.nagad", "নগদ", "Nagad"),
    PayRocket("checkout.pay.rocket", "রকেট", "Rocket"),
    PayCard("checkout.pay.card", "ডেবিট / ক্রেডিট কার্ড", "Debit / credit card"),
    CheckoutPay("checkout.pay", "{{amount}} পেমেন্ট করুন", "Pay {{amount}}"),
    CheckoutSecureNote(
        "checkout.secureNote",
        "পেমেন্ট হবে গেটওয়ের নিরাপদ পেজে — আপনার পিন বা কার্ডের তথ্য শিক্ষাসাথী কখনো দেখে না।",
        "You pay on the gateway’s secure page — ShikkhaSathi never sees your PIN or card details."
    ),
    CheckoutRefundNote(
        "checkout.refundNote",
        "কেনার ৪৮ ঘণ্টার মধ্যে আর ২০%-এর কম দেখা হলে রিফান্ড চাইতে পারবেন।",
        "You can ask for a refund within 48 hours if you’ve watched less than 20%."
    ),
    CheckoutWaitingTitle("checkout.waitingTitle", "পেমেন্ট শেষ হওয়ার অপেক্ষায়", "Waiting for the payment"),
    CheckoutWaitingBody(
        "checkout.waitingBody",
        "গেটওয়ে পেজে পেমেন্ট শেষ করে এই অ্যাপে ফিরে আসুন — আমরা নিজে থেকেই যাচাই করে কোর্স খুলে দেব।",
        "Finish paying on the gateway page and come back — we’ll verify it and unlock the course automatically."
    ),
    CheckoutCheckAgain("checkout.checkAgain", "আবার যাচাই করুন", "Check again"),
    CheckoutChangeMethod("checkout.changeMethod", "অন্যভাবে পেমেন্ট করুন", "Pay another way"),
    CheckoutVerifying("checkout.verifying", "পেমেন্ট যাচাই হচ্ছে…", "Verifying the payment…"),
    CheckoutPendingTitle("checkout.pendingTitle", "পেমেন্ট এখনো নিশ্চিত হয়নি", "Payment not confirmed yet"),
    CheckoutPendingBody(
        "checkout.pendingBody",
        "কখনো কখনো গেটওয়ে একটু সময় নেয়। টাকা কাটা হলে কয়েক মিনিটের মধ্যে কোর্সটি আমার কোর্সে চলে আসবে।",
        "Gateways sometimes take a moment. If money was deducted, the course appears in My courses within minutes."
    ),
    CheckoutSuccessTitle("checkout.successTitle", "পেমেন্ট সফল হয়েছে", "Payment successful"),
    CheckoutSuccessBody("checkout.successBody", "{{title}} এখন আপনার। চলুন শুরু করি!", "{{title}} is yours now. Let’s begin!"),
    CheckoutInvoice("checkout.invoice", "ইনভয়েস নম্বর: {{number}}", "Invoice: {{number}}"),
    CheckoutFailedTitle("checkout.failedTitle", "পেমেন্ট হয়নি", "Payment didn’t go through"),

    // --- my courses ---
    MyCoursesInProgress("myCourses.inProgress", "চলছে", "In progress"),
    MyCoursesCompleted("myCourses.completed", "শেষ", "Completed"),
    MyCoursesExpiring("myCourses.expiring", "মেয়াদ শেষ হচ্ছে", "Expiring"),
    MyCoursesExpiringSoon("myCourses.expiringSoon", "মেয়াদ শেষ হচ্ছে শিগগিরই", "Access ends soon"),
    MyCoursesExpired("myCourses.expired", "মেয়াদ শেষ", "Expired"),
    MyCoursesFilterEmpty("myCourses.filterEmpty", "এই ফিল্টারে কোনো কোর্স নেই", "No courses in this filter"),
    MyCoursesAccessUntil("myCourses.accessUntil", "মেয়াদ: {{date}}", "Access until {{date}}"),

    // --- player ---
    PlayerTabLessons("player.tab.lessons", "লেসন", "Lessons"),
    PlayerTabNotes("player.tab.notes", "নোট", "Notes"),
    PlayerTabQa("player.tab.qa", "প্রশ্নোত্তর", "Q&A"),
    PlayerProgress("player.progress", "{{done}}/{{total}} লেসন", "{{done}}/{{total}} lessons"),
    PlayerPlay("player.play", "চালান", "Play"),
    PlayerPause("player.pause", "থামান", "Pause"),
    PlayerBack10("player.backTen", "১০ সেকেন্ড পেছনে", "Back 10 seconds"),
    PlayerForward10("player.forwardTen", "১০ সেকেন্ড সামনে", "Forward 10 seconds"),
    PlayerShowControls("player.showControls", "কন্ট্রোল দেখান বা লুকান", "Show or hide controls"),
    PlayerResumedAt("player.resumedAt", "{{time}} থেকে চালু হয়েছে", "Resumed at {{time}}"),
    PlayerTakenOver(
        "player.takenOver",
        "অন্য ডিভাইসে এই অ্যাকাউন্টে ভিডিও চলছে। একসঙ্গে একটি ডিভাইসেই দেখা যায়।",
        "This account is playing on another device. Only one device can play at a time."
    ),
    PlayerPlayHere("player.playHere", "এখানে চালান", "Play here"),
    PlayerYoutubeBody("player.youtubeBody", "এই লেসনটি YouTube-এ রাখা আছে।", "This lesson is hosted on YouTube."),
    PlayerOpenYoutube("player.openYoutube", "YouTube-এ দেখুন", "Watch on YouTube"),
    PlayerOpenPdf("player.openPdf", "PDF খুলুন", "Open PDF"),
    PlayerMarkComplete("player.markComplete", "সম্পন্ন হিসেবে চিহ্নিত করুন", "Mark as complete"),
    PlayerCompleted("player.completed", "সম্পন্ন", "Completed"),
    PlayerNext("player.next", "পরের লেসন", "Next lesson"),
    PlayerPrevious("player.previous", "আগের লেসন", "Previous lesson"),
    NotesPlaceholder("notes.placeholder", "নোট লিখুন…", "Write a note…"),
    NotesAttachTime("notes.attachTime", "ভিডিওর এই সময় যোগ করুন", "Attach the current video time"),
    NotesBookmark("notes.bookmark", "এই মুহূর্ত বুকমার্ক করুন", "Bookmark this moment"),
    NotesBookmarks("notes.bookmarks", "বুকমার্ক", "Bookmarks"),
    NotesBookmarksEmpty("notes.bookmarksEmpty", "কোনো বুকমার্ক নেই", "No bookmarks"),
    NotesSearch("notes.search", "নোটে খুঁজুন", "Search notes"),
    NotesLessonEmptyTitle("notes.lessonEmpty.title", "এই লেসনে এখনো নোট নেই", "No notes on this lesson yet"),
    NotesLessonEmptyBody(
        "notes.lessonEmpty.body",
        "গুরুত্বপূর্ণ মুহূর্তে নোট রাখুন — পরীক্ষার আগে এক জায়গায় পাবেন।",
        "Note the key moments — you’ll find them in one place before the exam."
    ),
    QaPlaceholder("qa.placeholder", "আপনার প্রশ্ন লিখুন — কোন অংশে আটকে গেছেন?", "Write your question — where did you get stuck?"),
    QaAsk("qa.ask", "প্রশ্ন পাঠান", "Send question"),
    QaEmptyTitle("qa.empty.title", "এখনো কেউ প্রশ্ন করেনি", "No questions yet"),
    QaEmptyBody("qa.empty.body", "প্রথম প্রশ্নটা আপনিই করুন — শিক্ষক নোটিফিকেশন পাবেন।", "Ask the first one — your teacher gets notified."),
    QaTeacher("qa.teacher", "শিক্ষক", "Teacher"),
    QaAnswered("qa.answered", "উত্তর দেওয়া হয়েছে", "Answered"),

    // --- downloads ---
    DownloadKeepOffline("download.keepOffline", "অফলাইনে রাখুন", "Keep offline"),
    DownloadDone("download.done", "ডাউনলোড হয়েছে", "Downloaded"),
    DownloadsEmptyTitle("downloads.empty.title", "কোনো ডাউনলোড নেই", "No downloads"),
    DownloadsEmptyBody(
        "downloads.empty.body",
        "PDF লেসনে “অফলাইনে রাখুন” চাপলে ইন্টারনেট ছাড়াও পড়তে পারবেন — শুধু এই ফোনে, এই অ্যাকাউন্টে।",
        "Tap “Keep offline” on a PDF lesson to read without internet — on this phone and account only."
    ),
    DownloadsTotal("downloads.total", "মোট {{size}}", "{{size}} in total"),
    DownloadsDownloading("downloads.downloading", "ডাউনলোড হচ্ছে", "Downloading"),
    DownloadsFailed("downloads.failed", "ডাউনলোড হয়নি", "Failed"),
    DownloadsReady("downloads.ready", "অফলাইনে আছে", "Available offline"),
    DownloadsNeedsOnline("downloads.needsOnline", "একবার অনলাইনে আসুন", "Go online once"),
    DownloadsExpired("downloads.expired", "মেয়াদ শেষ", "Expired"),
    DownloadsPrivacyNote(
        "downloads.privacyNote",
        "ফাইলগুলো এনক্রিপ্ট করা থাকে, শুধু এই ফোনে খোলে। লগ আউট করলে বা কোর্সের মেয়াদ শেষ হলে মুছে যায়।",
        "Files are encrypted and open only on this phone. They’re removed when you sign out or your access ends."
    ),

    // --- exam mode + flashcards ---
    ExamAddPlan("exam.addPlan", "নতুন পরীক্ষা যোগ করুন", "Add an exam"),
    ExamTodayPlan("exam.todayPlan", "আজকের প্ল্যান", "Today’s plan"),
    ExamRevisionDay("exam.revisionDay", "রিভিশনের দিন", "Revision day"),
    ExamTodayEmpty("exam.todayEmpty", "আজ কোনো কাজ বাকি নেই", "Nothing left for today"),
    ExamSyllabus("exam.syllabus", "সিলেবাস চেকলিস্ট", "Syllabus checklist"),
    ExamImportant("exam.important", "সম্ভাব্য গুরুত্বপূর্ণ টপিক", "Likely important topics"),
    ExamPractice("exam.practice", "অনুশীলন", "Practice"),
    ExamFlashcards("exam.flashcards", "ফ্ল্যাশকার্ড", "Flashcards"),
    ExamFlashcardsKnown("exam.flashcardsKnown", "{{known}}/{{total}} জানা", "{{known}}/{{total}} known"),
    ExamFlashcardsToday("exam.flashcardsToday", "{{n}}টি ফ্ল্যাশকার্ড", "{{n}} flashcards"),
    ExamNoPractice(
        "exam.noPractice",
        "যুক্ত কোর্সে মক টেস্ট বা ফ্ল্যাশকার্ড থাকলে এখানে দেখাবে।",
        "Mock tests and flashcards from linked courses appear here."
    ),
    ExamRevision("exam.revision", "রিভিশন", "Revision"),
    ExamRevisionPack("exam.revisionPack", "পরীক্ষার আগের রাত", "The night before"),
    ExamRevisionPackBody(
        "exam.revisionPackBody",
        "{{lessons}}টি গুরুত্বপূর্ণ লেসন · {{notes}}টি নোট · {{wrong}}টি ভুল উত্তর",
        "{{lessons}} key lessons · {{notes}} notes · {{wrong}} wrong answers"
    ),
    ExamBreakdown("exam.breakdown", "লেসন {{lessons}}% · মক টেস্ট {{mock}}%", "Lessons {{lessons}}% · mock tests {{mock}}%"),
    ExamStart("exam.start", "শুরু করুন", "Start"),
    ExamFormSubject("exam.form.subject", "বিষয়ের নাম", "Subject name"),
    ExamFormCode("exam.form.code", "কোর্স কোড (ঐচ্ছিক)", "Course code (optional)"),
    ExamFormType("exam.form.type", "পরীক্ষার ধরন", "Exam type"),
    ExamFormPickDate("exam.form.pickDate", "পরীক্ষার তারিখ বেছে নিন", "Pick the exam date"),
    ExamFormTime("exam.form.time", "শুরুর সময়", "Start time"),
    FlashcardsEmptyTitle("flashcards.empty.title", "এই কোর্সগুলোতে ফ্ল্যাশকার্ড নেই", "No flashcards in these courses"),
    FlashcardsEmptyBody(
        "flashcards.empty.body",
        "শিক্ষক ফ্ল্যাশকার্ড যোগ করলে এখানে পাবেন।",
        "They’ll appear here when the teacher adds them."
    ),
    FlashcardsPosition("flashcards.position", "{{n}} / {{total}}", "{{n}} / {{total}}"),
    FlashcardsFront("flashcards.front", "প্রশ্ন", "Question"),
    FlashcardsBack("flashcards.back", "উত্তর", "Answer"),
    FlashcardsFlip("flashcards.flip", "উল্টে দেখুন", "Flip"),
    FlashcardsTapHint("flashcards.tapHint", "কার্ডে চাপ দিলে উত্তর দেখাবে", "Tap the card to see the answer"),
    FlashcardsKnown("flashcards.known", "জানি", "I know it"),
    FlashcardsNotYet("flashcards.notYet", "আবার দেখব", "Not yet"),
    FlashcardsDone("flashcards.done", "সব কার্ড দেখা শেষ!", "All cards done!"),
    FlashcardsSummary("flashcards.summary", "{{known}}টি জানা, {{notYet}}টি আবার দেখবেন।", "{{known}} known, {{notYet}} to review again."),
    FlashcardsAgain("flashcards.again", "আবার দেখব যেগুলো", "Review the rest"),

    // --- mock tests ---
    MockQuestions("mock.questions", "{{n}}টি প্রশ্ন", "{{n}} questions"),
    MockBestScore("mock.bestScore", "সেরা {{p}}%", "Best {{p}}%"),
    MockResume("mock.resume", "চালিয়ে যান", "Resume"),
    MockRetake("mock.retake", "আবার দিন", "Retake"),
    MockQuestionOf("mock.questionOf", "প্রশ্ন {{n}} / {{total}}", "Question {{n}} of {{total}}"),
    MockTimeLeft("mock.timeLeft", "বাকি সময়", "Time left"),
    MockFlag("mock.flag", "রিভিউর জন্য চিহ্নিত করুন", "Flag for review"),
    MockUnflag("mock.unflag", "চিহ্ন সরান", "Remove flag"),
    MockUnsaved("mock.unsaved", "সংযোগ নেই — উত্তর আবার সেভ করার চেষ্টা চলছে", "Offline — retrying to save answers"),
    MockPrevious("mock.previous", "আগের", "Previous"),
    MockNext("mock.next", "পরের", "Next"),
    MockSubmit("mock.submit", "জমা দিন", "Submit"),
    MockSubmitTitle("mock.submitTitle", "টেস্ট জমা দেবেন?", "Submit the test?"),
    MockSubmitUnanswered("mock.submitUnanswered", "{{n}}টি প্রশ্নের উত্তর বাকি আছে।", "{{n}} questions are unanswered."),
    MockSubmitAllAnswered("mock.submitAllAnswered", "সব প্রশ্নের উত্তর দেওয়া হয়েছে।", "All questions are answered."),
    MockKeepGoing("mock.keepGoing", "আরও দেখব", "Keep going"),
    MockResultTitle("mock.resultTitle", "ফলাফল", "Result"),
    MockScoreLine(
        "mock.scoreLine",
        "সঠিক {{correct}} · ভুল {{wrong}} · বাদ {{skipped}}",
        "Correct {{correct}} · wrong {{wrong}} · skipped {{skipped}}"
    ),
    MockTimeUsed("mock.timeUsed", "সময় লেগেছে {{time}}", "Time used {{time}}"),
    MockByChapter("mock.byChapter", "চ্যাপ্টার অনুযায়ী", "By chapter"),
    MockOnlyMistakes("mock.onlyMistakes", "শুধু ভুল ও বাদ দেখান", "Show only wrong and skipped"),
    MockCorrectAnswer("mock.correctAnswer", "সঠিক উত্তর", "Correct answer"),
    MockYourAnswer("mock.yourAnswer", "আপনার উত্তর", "Your answer"),
    MockSkipped("mock.skipped", "উত্তর দেননি", "Not answered"),
    ;

    fun text(
        locale: AppLocale = AppLocale.Bn,
        vararg args: Pair<String, Any>,
    ): String = render(locale, args.asList())

    internal fun render(
        locale: AppLocale,
        args: List<Pair<String, Any>>,
    ): String {
        var out = if (locale == AppLocale.Bn) bn else en
        args.forEach { (name, value) ->
            val shown =
                when {
                    value is UiText -> value.resolve(locale)
                    value is Number && locale == AppLocale.Bn -> toBanglaDigits(value.toString())
                    else -> value.toString()
                }
            out = out.replace("{{$name}}", shown)
        }
        return out
    }

    companion object {
        private val byKey: Map<String, Str> by lazy { entries.associateBy { it.key } }

        fun fromKey(key: String): Str? = byKey[key]
    }
}

/** `"60"` → `"৬০"`. Bangla UI shows Bangla numerals for counts and timers. */
fun toBanglaDigits(input: String): String =
    buildString(input.length) {
        input.forEach { c -> append(if (c in '0'..'9') '০' + (c - '0') else c) }
    }

/** Text the UI should show: either a catalogue entry (+ args) or text that already arrived localised (server errors are Bangla). */
sealed interface UiText {
    data class Res(val str: Str, val args: List<Pair<String, Any>> = emptyList()) : UiText

    data class Raw(val text: String) : UiText

    /** Wall-clock time, e.g. "রাত ১১:৫৯" / "11:59 PM". Already in Asia/Dhaka. */
    data class ClockTime(val hour: Int, val minute: Int) : UiText

    /** "১২ নভেম্বর" / "12 Nov". */
    data class DayMonth(val day: Int, val month: Int) : UiText

    /** ISO day number (Monday = 1): "বৃহস্পতিবার" or, [short], "বৃহঃ". */
    data class Weekday(val isoDay: Int, val short: Boolean = false) : UiText

    /** A count rendered in the locale's digits ("১২"). */
    data class Count(val value: Int) : UiText

    /** "নভেম্বর ২০২৬". */
    data class MonthYear(val month: Int, val year: Int) : UiText

    /** Pre-formatted digits/punctuation shown in the locale's numerals: "05:23" → "০৫:২৩", "2.4" → "২.৪". */
    data class Digits(val text: String) : UiText

    fun resolve(locale: AppLocale = AppLocale.Bn): String =
        when (this) {
            is Res -> str.render(locale, args)
            is Raw -> text
            is ClockTime -> DateText.clock(locale, hour, minute)
            is DayMonth -> DateText.dayMonth(locale, day, month)
            is Weekday -> DateText.weekday(locale, isoDay, short)
            is Count -> DateText.digits(locale, value.toString())
            is MonthYear -> DateText.monthName(locale, month) + " " + DateText.digits(locale, year.toString())
            is Digits -> DateText.digits(locale, text)
        }
}

fun Str.asUiText(vararg args: Pair<String, Any>): UiText = UiText.Res(this, args.toList())
