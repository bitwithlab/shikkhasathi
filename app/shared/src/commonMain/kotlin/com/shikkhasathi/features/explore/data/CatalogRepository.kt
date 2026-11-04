package com.shikkhasathi.features.explore.data

import com.shikkhasathi.core.model.CourseDetail
import com.shikkhasathi.core.model.OkResponse
import com.shikkhasathi.core.model.SearchFilters
import com.shikkhasathi.core.model.SearchResponse
import com.shikkhasathi.core.model.SuggestResponse
import com.shikkhasathi.core.model.Suggestion
import com.shikkhasathi.core.network.ApiClient
import com.shikkhasathi.core.network.segment
import com.shikkhasathi.core.network.withQuery

/** Discovery (contracts `discovery` + `wishlist`). Every call throws `ApiException` on failure. */
interface CatalogRepository {
    suspend fun search(
        filters: SearchFilters,
        cursor: String? = null,
        limit: Int = DEFAULT_PAGE,
    ): SearchResponse

    suspend fun suggest(query: String): List<Suggestion>

    suspend fun course(slug: String): CourseDetail

    suspend fun setWishlisted(
        courseId: String,
        wishlisted: Boolean,
    )

    companion object {
        const val DEFAULT_PAGE = 20
    }
}

class ApiCatalogRepository(private val api: ApiClient) : CatalogRepository {
    override suspend fun search(
        filters: SearchFilters,
        cursor: String?,
        limit: Int,
    ): SearchResponse = api.get(withQuery("search", filters.toQuery(cursor, limit)))

    override suspend fun suggest(query: String): List<Suggestion> =
        api
            .get<SuggestResponse>(
                withQuery("search/suggest", "q" to query.trim())
            ).items

    override suspend fun course(slug: String): CourseDetail = api.get("courses/${segment(slug)}")

    override suspend fun setWishlisted(
        courseId: String,
        wishlisted: Boolean,
    ) {
        val path = "wishlist/${segment(courseId)}"
        if (wishlisted) api.postEmpty<OkResponse>(path) else api.delete<OkResponse>(path)
    }
}
