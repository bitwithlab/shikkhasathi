package com.shikkhasathi.core.model

import kotlinx.serialization.Serializable

// Mirrors of website/src/api/contracts/exam.ts (exam plans, flashcards, mock tests).

/** `EXAM_TYPES` — the label keys are `examType.<wire>` in [com.shikkhasathi.core.i18n.Str]. */
val EXAM_TYPES: List<String> = listOf("midterm", "final", "quiz", "lab", "viva", "admission", "general")

@Serializable
data class ExamPlanInput(
    val subjectName: String,
    val subjectMasterId: String? = null,
    val courseCode: String? = null,
    val examType: String,
    /** Exam start with offset, e.g. `2026-11-20T10:00:00+06:00`. */
    val examDate: String,
    val courseIds: List<String> = emptyList(),
)

@Serializable
data class ExamPlanSummary(
    val id: String,
    val subjectName: String,
    val courseCode: String? = null,
    val examType: String = "general",
    val examDate: String,
    val daysLeft: Int = 0,
    val readiness: Int = 0,
    val courseIds: List<String> = emptyList(),
    val active: Boolean = true,
    val past: Boolean = false,
)

/**
 * One row of a study day. The contract is a discriminated union on `kind`
 * (`lesson` · `mock` · `flashcards` · `revision`); it is flattened here so an unknown kind added later
 * still decodes (and is shown as a plain item) instead of failing the whole plan.
 */
@Serializable
data class PlanItem(
    val kind: String,
    val done: Boolean = false,
    val title: String? = null,
    // lesson
    val lessonId: String? = null,
    val courseId: String? = null,
    val chapterTitle: String? = null,
    val durationSec: Int? = null,
    val lessonType: String? = null,
    val isImportant: Boolean = false,
    // mock
    val testId: String? = null,
    val questionCount: Int? = null,
    // flashcards
    val count: Int? = null,
)

@Serializable
data class PlanDay(
    /** Dhaka day `YYYY-MM-DD`. */
    val date: String,
    val items: List<PlanItem> = emptyList(),
    val totalMinutes: Int = 0,
    val isRevision: Boolean = false,
)

@Serializable
data class SyllabusChapter(
    val courseId: String,
    val chapterId: String,
    val title: String,
    val sectionTitle: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val important: Int = 0,
    /** `todo` · `doing` · `done`. */
    val status: String = "todo",
)

@Serializable
data class MockSummary(
    val id: String,
    val courseId: String,
    val courseTitle: String = "",
    val title: String,
    val questionCount: Int = 0,
    val durationSec: Int = 0,
    val attempts: Int = 0,
    val bestScore: Int? = null,
    val lastScore: Int? = null,
    val inProgressAttemptId: String? = null,
)

@Serializable
data class ReadinessBreakdown(
    val lessons: Int = 0,
    val mock: Int? = null,
    val flashcards: Int? = null,
)

@Serializable
data class PlanCourse(
    val id: String,
    val slug: String,
    val title: String,
    val enrolled: Boolean = false,
)

@Serializable
data class PlanLesson(
    val lessonId: String,
    val courseId: String,
    val title: String,
    val done: Boolean = false,
    val durationSec: Int = 0,
)

@Serializable
data class PreviousQuestion(
    val id: String,
    val title: String,
    val year: Int? = null,
    val courseId: String,
)

@Serializable
data class FlashcardStats(
    val total: Int = 0,
    val known: Int = 0,
)

@Serializable
data class RevisionPack(
    val importantLessons: Int = 0,
    val notes: Int = 0,
    val bookmarks: Int = 0,
    val wrongAnswers: Int = 0,
)

/** `GET /exam-plans/:id` — Exam Mode. */
@Serializable
data class ExamPlanDetail(
    val id: String,
    val subjectName: String,
    val courseCode: String? = null,
    val examType: String = "general",
    val examDate: String,
    val daysLeft: Int = 0,
    val readiness: Int = 0,
    val courseIds: List<String> = emptyList(),
    val active: Boolean = true,
    val past: Boolean = false,
    val readinessBreakdown: ReadinessBreakdown = ReadinessBreakdown(),
    val courses: List<PlanCourse> = emptyList(),
    val today: PlanDay? = null,
    val days: List<PlanDay> = emptyList(),
    val syllabus: List<SyllabusChapter> = emptyList(),
    val important: List<PlanLesson> = emptyList(),
    val shortLectures: List<PlanLesson> = emptyList(),
    val previousQuestions: List<PreviousQuestion> = emptyList(),
    val mockTests: List<MockSummary> = emptyList(),
    val flashcards: FlashcardStats = FlashcardStats(),
    /** "পরীক্ষার আগের রাত" pack unlocks the day before. */
    val revisionUnlocked: Boolean = false,
    val revisionPack: RevisionPack = RevisionPack(),
)

@Serializable
data class Flashcard(
    val id: String,
    val courseId: String,
    val front: String,
    val back: String,
    /** Null = never reviewed. */
    val known: Boolean? = null,
)

@Serializable
data class ReviewCardBody(
    val known: Boolean,
)

@Serializable
data class AttemptOption(
    val id: String,
    val body: String,
)

@Serializable
data class AttemptQuestion(
    val id: String,
    val body: String,
    val chapterTag: String? = null,
    val options: List<AttemptOption> = emptyList(),
)

@Serializable
data class AttemptAnswer(
    val questionId: String,
    val optionId: String? = null,
    val flagged: Boolean = false,
)

/** `POST /mock-tests/:id/attempts` (start or resume) and `GET /mock-attempts/:id`. */
@Serializable
data class Attempt(
    val id: String,
    val testId: String,
    val courseId: String,
    val title: String,
    val durationSec: Int,
    val startedAt: String,
    val deadlineAt: String,
    /** Server clock at response time — the timer corrects the device clock with it. */
    val serverNow: String,
    val submitted: Boolean = false,
    val questions: List<AttemptQuestion> = emptyList(),
    val answers: List<AttemptAnswer> = emptyList(),
)

@Serializable
data class SaveAnswerBody(
    val questionId: String,
    val optionId: String? = null,
    val flagged: Boolean? = null,
)

@Serializable
data class ChapterScore(
    val tag: String,
    val correct: Int = 0,
    val total: Int = 0,
)

@Serializable
data class ReviewQuestion(
    val questionId: String,
    val body: String,
    val chapterTag: String? = null,
    val explanation: String? = null,
    val chosenOptionId: String? = null,
    val correctOptionId: String? = null,
    val options: List<AttemptOption> = emptyList(),
)

/** `POST /mock-attempts/:id/submit` and `GET /mock-attempts/:id/result`. */
@Serializable
data class AttemptResult(
    val attemptId: String,
    val testId: String,
    val courseId: String,
    val title: String,
    /** 0–100. */
    val score: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0,
    val skipped: Int = 0,
    val total: Int = 0,
    val timeUsedSec: Int = 0,
    val autoSubmitted: Boolean = false,
    val bestScore: Int = 0,
    val attempts: Int = 0,
    val chapters: List<ChapterScore> = emptyList(),
    val review: List<ReviewQuestion> = emptyList(),
)
