package com.swordfish.lemuroid.app.mobile.feature.coversearch

import com.swordfish.lemuroid.app.mobile.feature.coversearch.LibretroCoverSearch.CoverCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * Cover candidates from general web image search engines, used alongside the
 * libretro thumbnails library (see [LibretroCoverSearch]).
 *
 * The thumbnails library only carries official boxart under No-Intro English
 * file names, so it can never match Chinese-titled or modified ROMs (the case
 * that motivated this: a modded Pokémon HeartGold whose library entry is just
 * "起源心金"). Image search engines index the open web — including Chinese
 * ROM sites and forums — which is where those covers actually live. Every
 * provider here is keyless plain HTTP(S), and each one fails independently:
 * the view model merges whatever came back.
 */
object WebCoverSearch {
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

    private const val MAX_RESULTS_PER_SOURCE = 18

    private val CJK_REGEX = Regex("""[\p{IsHan}]""")
    private val BING_MURL_REGEX = Regex(""""murl":"((?:[^"\\]|\\.)*)"""")
    private val BING_TURL_REGEX = Regex(""""turl":"((?:[^"\\]|\\.)*)"""")
    private val BING_TITLE_REGEX = Regex(""""t":"((?:[^"\\]|\\.)*)"""")

    /** Baidu image search: the strongest source for Chinese game titles. */
    suspend fun searchBaidu(
        client: OkHttpClient,
        query: String,
    ): List<CoverCandidate> =
        withContext(Dispatchers.IO) {
            val url =
                "https://image.baidu.com/search/acjson".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("tn", "resultjson_com")
                    .addQueryParameter("ie", "utf-8")
                    .addQueryParameter("oe", "utf-8")
                    .addQueryParameter("word", "$query 封面")
                    .addQueryParameter("pn", "0")
                    .addQueryParameter("rn", "30")
                    .build()
            val request =
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://image.baidu.com/")
                    .build()
            val body =
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Baidu image search failed with code ${response.code}")
                    }
                    response.body?.string() ?: throw IOException("Empty Baidu image search response")
                }

            val data = JSONObject(body).optJSONArray("data") ?: return@withContext emptyList()
            val candidates = mutableListOf<CoverCandidate>()
            for (index in 0 until data.length()) {
                val item = data.optJSONObject(index) ?: continue
                val thumbUrl = item.optString("thumbURL")
                val middleUrl = item.optString("middleURL")
                val originalUrl = item.optString("objURL")
                val fullImageUrl = originalUrl.ifBlank { middleUrl }.ifBlank { thumbUrl }
                val previewUrl = middleUrl.ifBlank { thumbUrl }.ifBlank { fullImageUrl }
                if (fullImageUrl.isBlank() || previewUrl.isBlank()) {
                    continue
                }
                val title = item.optString("fromPageTitleEnc").ifBlank { query }
                candidates +=
                    CoverCandidate(
                        fileName = "web-baidu-$index-${fullImageUrl.hashCode()}",
                        displayName = title,
                        imageUrl = previewUrl,
                        score = 0.0,
                        fullImageUrl = fullImageUrl,
                        sourceLabel = "百度图片",
                    )
                if (candidates.size >= MAX_RESULTS_PER_SOURCE) {
                    break
                }
            }
            candidates
        }

    /** Bing image search: the fallback that covers English/original titles. */
    suspend fun searchBing(
        client: OkHttpClient,
        query: String,
    ): List<CoverCandidate> =
        withContext(Dispatchers.IO) {
            val searchQuery = if (CJK_REGEX.containsMatchIn(query)) "$query 封面" else "$query boxart cover"
            val url =
                "https://www.bing.com/images/search".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("q", searchQuery)
                    .addQueryParameter("form", "HDRSC2")
                    .addQueryParameter("first", "1")
                    .build()
            val request =
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()
            val html =
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Bing image search failed with code ${response.code}")
                    }
                    response.body?.string() ?: throw IOException("Empty Bing image search response")
                }

            val originals = BING_MURL_REGEX.findAll(html).map { unescapeJson(it.groupValues[1]) }.toList()
            val previews = BING_TURL_REGEX.findAll(html).map { unescapeJson(it.groupValues[1]) }.toList()
            val titles = BING_TITLE_REGEX.findAll(html).map { unescapeJson(it.groupValues[1]) }.toList()

            originals.mapIndexed { index, originalUrl ->
                CoverCandidate(
                    fileName = "web-bing-$index-${originalUrl.hashCode()}",
                    displayName = titles.getOrNull(index)?.ifBlank { query } ?: query,
                    imageUrl = previews.getOrNull(index)?.ifBlank { originalUrl } ?: originalUrl,
                    score = 0.0,
                    fullImageUrl = originalUrl,
                    sourceLabel = "Bing 图片",
                )
            }.take(MAX_RESULTS_PER_SOURCE)
        }

    /** Undoes the minimal JSON string escaping used inside Bing's result blobs. */
    private fun unescapeJson(value: String): String =
        value
            .replace("\\/", "/")
            .replace("\\\"", "\"")
            .replace("\\u0026", "&")
            .replace("\\u003d", "=")
            .replace("\\u003f", "?")
}
