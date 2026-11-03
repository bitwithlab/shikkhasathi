package com.shikkhasathi.core.i18n

/**
 * All user-facing copy of the app, Bangla first (CLAUDE.md §5.1 "no hardcoded UI text" applies here too).
 *
 * Keys use the same dotted namespace as the web client's i18n catalogue (`Web/client/src/lib/i18n`,
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
    TimeMonthYear("time.monthYear", "{{month}} {{year}}", "{{month}} {{year}}"),
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
    GcConnectedToast("gc.connectedToast", "Google Classroom সংযুক্ত হয়েছে — ক্লাসগুলো সিঙ্ক হচ্ছে", "Google Classroom connected — syncing your classes"),
    GcDeniedToast("gc.deniedToast", "সংযোগ বাতিল করা হয়েছে। যখন খুশি আবার চেষ্টা করতে পারবেন।", "Connection cancelled. You can try again any time."),
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
    HomeFeedEmpty("home.feedEmpty", "এখনো কোনো আপডেট নেই। শিক্ষক কিছু পোস্ট করলে এখানে দেখাবে।", "No updates yet. Posts from your teachers will show up here."),
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
    CalendarDayEmptyBody("calendar.dayEmptyBody", "অন্য দিনে ট্যাপ করুন, বা পুরো তালিকা দেখতে অ্যাসাইনমেন্টে যান।", "Tap another day, or open Assignments for the full list."),
    CalendarDayA11y("calendar.dayA11y", "{{date}}: {{summary}}", "{{date}}: {{summary}}"),
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

    fun resolve(locale: AppLocale = AppLocale.Bn): String =
        when (this) {
            is Res -> str.render(locale, args)
            is Raw -> text
            is ClockTime -> DateText.clock(locale, hour, minute)
            is DayMonth -> DateText.dayMonth(locale, day, month)
            is Weekday -> DateText.weekday(locale, isoDay, short)
            is Count -> DateText.digits(locale, value.toString())
            is MonthYear -> DateText.monthName(locale, month) + " " + DateText.digits(locale, year.toString())
        }
}

fun Str.asUiText(vararg args: Pair<String, Any>): UiText = UiText.Res(this, args.toList())
