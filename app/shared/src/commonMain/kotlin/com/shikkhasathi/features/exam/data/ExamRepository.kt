package com.shikkhasathi.features.exam.data

import com.shikkhasathi.core.model.Attempt
import com.shikkhasathi.core.model.AttemptResult
import com.shikkhasathi.core.model.ExamPlanDetail
import com.shikkhasathi.core.model.ExamPlanInput
import com.shikkhasathi.core.model.ExamPlanSummary
import com.shikkhasathi.core.model.Flashcard
import com.shikkhasathi.core.model.ItemsResponse
import com.shikkhasathi.core.model.MockSummary
import com.shikkhasathi.core.model.OkResponse
import com.shikkhasathi.core.model.ReviewCardBody
import com.shikkhasathi.core.model.SaveAnswerBody
import com.shikkhasathi.core.network.ApiClient
import com.shikkhasathi.core.network.segment

/** Exam Mode and mock tests (contracts `exam`). */
@Suppress("TooManyFunctions") // one method per endpoint
interface ExamRepository {
    suspend fun plans(): List<ExamPlanSummary>

    suspend fun createPlan(input: ExamPlanInput): ExamPlanSummary

    suspend fun plan(planId: String): ExamPlanDetail

    suspend fun flashcards(planId: String): List<Flashcard>

    suspend fun reviewCard(
        cardId: String,
        known: Boolean,
    )

    suspend fun mockTests(): List<MockSummary>

    /** Starts a timed attempt, or returns the open one (resume). */
    suspend fun startAttempt(testId: String): Attempt

    suspend fun attempt(attemptId: String): Attempt

    suspend fun saveAnswer(
        attemptId: String,
        body: SaveAnswerBody,
    )

    suspend fun submit(attemptId: String): AttemptResult

    suspend fun result(attemptId: String): AttemptResult
}

@Suppress("TooManyFunctions")
class ApiExamRepository(private val api: ApiClient) : ExamRepository {
    override suspend fun plans(): List<ExamPlanSummary> = api.get<ItemsResponse<ExamPlanSummary>>("exam-plans").items

    override suspend fun createPlan(input: ExamPlanInput): ExamPlanSummary = api.post("exam-plans", input)

    override suspend fun plan(planId: String): ExamPlanDetail = api.get("exam-plans/${segment(planId)}")

    override suspend fun flashcards(planId: String): List<Flashcard> =
        api
            .get<ItemsResponse<Flashcard>>(
                "exam-plans/${segment(planId)}/flashcards"
            ).items

    override suspend fun reviewCard(
        cardId: String,
        known: Boolean,
    ) {
        api.post<ReviewCardBody, OkResponse>("flashcards/${segment(cardId)}/review", ReviewCardBody(known))
    }

    override suspend fun mockTests(): List<MockSummary> = api.get<ItemsResponse<MockSummary>>("mock-tests").items

    override suspend fun startAttempt(testId: String): Attempt = api.postEmpty("mock-tests/${segment(testId)}/attempts")

    override suspend fun attempt(attemptId: String): Attempt = api.get("mock-attempts/${segment(attemptId)}")

    override suspend fun saveAnswer(
        attemptId: String,
        body: SaveAnswerBody,
    ) {
        api.put<SaveAnswerBody, OkResponse>("mock-attempts/${segment(attemptId)}/answers", body)
    }

    override suspend fun submit(attemptId: String): AttemptResult = api.postEmpty("mock-attempts/${segment(attemptId)}/submit")

    override suspend fun result(attemptId: String): AttemptResult = api.get("mock-attempts/${segment(attemptId)}/result")
}
