package com.shikkhasathi.features.notifications.data

import com.shikkhasathi.core.model.ClientPlatform
import com.shikkhasathi.core.model.ItemsResponse
import com.shikkhasathi.core.model.MarkReadBody
import com.shikkhasathi.core.model.NotificationItem
import com.shikkhasathi.core.model.OkResponse
import com.shikkhasathi.core.model.Page
import com.shikkhasathi.core.model.PreferenceRow
import com.shikkhasathi.core.model.PushTokenBody
import com.shikkhasathi.core.model.UnreadCount
import com.shikkhasathi.core.network.ApiClient
import com.shikkhasathi.core.network.withQuery

/** In-app notification center, per type × channel preferences and push-token registration (contracts `notifications`). */
interface NotificationsRepository {
    suspend fun list(
        cursor: String? = null,
        limit: Int = DEFAULT_PAGE,
        unreadOnly: Boolean = false,
    ): Page<NotificationItem>

    suspend fun markRead(
        ids: List<String> = emptyList(),
        all: Boolean = false,
    )

    suspend fun unreadCount(): Int

    suspend fun preferences(): List<PreferenceRow>

    suspend fun updatePreferences(items: List<PreferenceRow>): List<PreferenceRow>

    /** FCM (Android) / APNs (iOS) token → `POST /me/push-tokens`. */
    suspend fun registerPush(
        platform: ClientPlatform,
        token: String,
    )

    companion object {
        const val DEFAULT_PAGE = 20
    }
}

class ApiNotificationsRepository(private val api: ApiClient) : NotificationsRepository {
    override suspend fun list(
        cursor: String?,
        limit: Int,
        unreadOnly: Boolean,
    ): Page<NotificationItem> =
        api.get(withQuery("notifications", "cursor" to cursor, "limit" to limit, "unread" to if (unreadOnly) true else null))

    override suspend fun markRead(
        ids: List<String>,
        all: Boolean,
    ) {
        api.post<MarkReadBody, OkResponse>("notifications/read", if (all) MarkReadBody(all = true) else MarkReadBody(ids = ids))
    }

    override suspend fun unreadCount(): Int = api.get<UnreadCount>("notifications/unread-count").count

    override suspend fun preferences(): List<PreferenceRow> = api.get<ItemsResponse<PreferenceRow>>("me/notification-preferences").items

    override suspend fun updatePreferences(items: List<PreferenceRow>): List<PreferenceRow> =
        api.put<ItemsResponse<PreferenceRow>, ItemsResponse<PreferenceRow>>("me/notification-preferences", ItemsResponse(items)).items

    override suspend fun registerPush(
        platform: ClientPlatform,
        token: String,
    ) {
        api.post<PushTokenBody, OkResponse>("me/push-tokens", PushTokenBody(platform, token))
    }
}
