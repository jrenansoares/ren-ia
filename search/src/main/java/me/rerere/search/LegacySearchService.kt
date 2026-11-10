package me.rerere.search

import androidx.compose.runtime.Composable
import kotlinx.serialization.json.JsonObject

/** Keeps older saved settings readable without contacting an external app service. */
object LegacySearchService : SearchService<SearchServiceOptions.LegacySearchOptions> {
    override val name: String = "Legacy search service"

    @Composable
    override fun Description() = Unit

    override fun parameters(options: SearchServiceOptions.LegacySearchOptions) = null

    override fun scrapingParameters(options: SearchServiceOptions.LegacySearchOptions) = null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.LegacySearchOptions,
    ): Result<SearchResult> = Result.failure(
        UnsupportedOperationException("This saved search service is no longer supported"),
    )

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.LegacySearchOptions,
    ): Result<ScrapedResult> = Result.failure(
        UnsupportedOperationException("This saved search service is no longer supported"),
    )
}
