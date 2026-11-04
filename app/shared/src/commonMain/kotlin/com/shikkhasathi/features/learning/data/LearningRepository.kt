package com.shikkhasathi.features.learning.data

import com.shikkhasathi.core.model.AskBody
import com.shikkhasathi.core.model.Bookmark
import com.shikkhasathi.core.model.CompleteBody
import com.shikkhasathi.core.model.CreateBookmarkBody
import com.shikkhasathi.core.model.CreateNoteBody
import com.shikkhasathi.core.model.DocumentUrl
import com.shikkhasathi.core.model.ItemsResponse
import com.shikkhasathi.core.model.LessonDetail
import com.shikkhasathi.core.model.MyCourse
import com.shikkhasathi.core.model.MyCoursesFilter
import com.shikkhasathi.core.model.Note
import com.shikkhasathi.core.model.OkResponse
import com.shikkhasathi.core.model.Page
import com.shikkhasathi.core.model.Playback
import com.shikkhasathi.core.model.PlaybackBody
import com.shikkhasathi.core.model.PlayerOutline
import com.shikkhasathi.core.model.ProgressBody
import com.shikkhasathi.core.model.ProgressResponse
import com.shikkhasathi.core.model.Question
import com.shikkhasathi.core.model.UpdateNoteBody
import com.shikkhasathi.core.network.ApiClient
import com.shikkhasathi.core.network.segment
import com.shikkhasathi.core.network.withQuery

/** My Courses, player, notes/bookmarks and lesson Q&A (contracts `learning`). */
@Suppress("TooManyFunctions") // one method per endpoint
interface LearningRepository {
    suspend fun myCourses(filter: MyCoursesFilter = MyCoursesFilter.All): List<MyCourse>

    suspend fun outline(courseId: String): PlayerOutline

    suspend fun lesson(lessonId: String): LessonDetail

    suspend fun playback(
        lessonId: String,
        deviceId: String,
        deviceName: String?,
    ): Playback

    suspend fun progress(
        lessonId: String,
        body: ProgressBody,
    ): ProgressResponse

    suspend fun complete(
        lessonId: String,
        completed: Boolean,
    ): ProgressResponse

    suspend fun documentUrl(documentId: String): DocumentUrl

    suspend fun notes(
        lessonId: String? = null,
        courseId: String? = null,
        query: String? = null,
        cursor: String? = null,
    ): Page<Note>

    suspend fun addNote(
        lessonId: String,
        timestampSec: Int?,
        body: String,
    ): Note

    suspend fun editNote(
        noteId: String,
        body: String,
    ): Note

    suspend fun deleteNote(noteId: String)

    suspend fun bookmarks(cursor: String? = null): Page<Bookmark>

    suspend fun addBookmark(
        lessonId: String,
        timestampSec: Int?,
    ): Bookmark

    suspend fun deleteBookmark(bookmarkId: String)

    suspend fun questions(
        lessonId: String,
        cursor: String? = null,
    ): Page<Question>

    suspend fun ask(
        lessonId: String,
        body: String,
        timestampSec: Int?,
    ): Question
}

@Suppress("TooManyFunctions")
class ApiLearningRepository(private val api: ApiClient) : LearningRepository {
    override suspend fun myCourses(filter: MyCoursesFilter): List<MyCourse> =
        api.get<ItemsResponse<MyCourse>>(withQuery("me/courses", "filter" to filter.wire)).items

    override suspend fun outline(courseId: String): PlayerOutline = api.get("learn/${segment(courseId)}")

    override suspend fun lesson(lessonId: String): LessonDetail = api.get("lessons/${segment(lessonId)}")

    override suspend fun playback(
        lessonId: String,
        deviceId: String,
        deviceName: String?,
    ): Playback =
        api.post(
            "lessons/${segment(lessonId)}/playback",
            PlaybackBody(deviceId = deviceId, deviceName = deviceName?.take(DEVICE_NAME_MAX))
        )

    override suspend fun progress(
        lessonId: String,
        body: ProgressBody,
    ): ProgressResponse = api.post("lessons/${segment(lessonId)}/progress", body)

    override suspend fun complete(
        lessonId: String,
        completed: Boolean,
    ): ProgressResponse = api.post("lessons/${segment(lessonId)}/complete", CompleteBody(completed))

    override suspend fun documentUrl(documentId: String): DocumentUrl = api.get("documents/${segment(documentId)}/url")

    override suspend fun notes(
        lessonId: String?,
        courseId: String?,
        query: String?,
        cursor: String?,
    ): Page<Note> =
        api.get(
            withQuery("notes", "lessonId" to lessonId, "courseId" to courseId, "q" to query?.trim()?.ifEmpty { null }, "cursor" to cursor),
        )

    override suspend fun addNote(
        lessonId: String,
        timestampSec: Int?,
        body: String,
    ): Note = api.post("notes", CreateNoteBody(lessonId = lessonId, timestampSec = timestampSec, body = body.trim()))

    override suspend fun editNote(
        noteId: String,
        body: String,
    ): Note = api.patch("notes/${segment(noteId)}", UpdateNoteBody(body.trim()))

    override suspend fun deleteNote(noteId: String) {
        api.delete<OkResponse>("notes/${segment(noteId)}")
    }

    override suspend fun bookmarks(cursor: String?): Page<Bookmark> = api.get(withQuery("bookmarks", "cursor" to cursor))

    override suspend fun addBookmark(
        lessonId: String,
        timestampSec: Int?,
    ): Bookmark = api.post("bookmarks", CreateBookmarkBody(lessonId = lessonId, timestampSec = timestampSec))

    override suspend fun deleteBookmark(bookmarkId: String) {
        api.delete<OkResponse>("bookmarks/${segment(bookmarkId)}")
    }

    override suspend fun questions(
        lessonId: String,
        cursor: String?,
    ): Page<Question> = api.get(withQuery("lessons/${segment(lessonId)}/questions", "cursor" to cursor))

    override suspend fun ask(
        lessonId: String,
        body: String,
        timestampSec: Int?,
    ): Question = api.post("lessons/${segment(lessonId)}/questions", AskBody(body = body.trim(), timestampSec = timestampSec))

    private companion object {
        const val DEVICE_NAME_MAX = 80
    }
}
