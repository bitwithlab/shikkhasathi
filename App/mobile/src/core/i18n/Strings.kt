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
            val shown = if (locale == AppLocale.Bn && value is Number) toBanglaDigits(value.toString()) else value.toString()
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

    fun resolve(locale: AppLocale = AppLocale.Bn): String =
        when (this) {
            is Res -> str.render(locale, args)
            is Raw -> text
        }
}

fun Str.asUiText(vararg args: Pair<String, Any>): UiText = UiText.Res(this, args.toList())
