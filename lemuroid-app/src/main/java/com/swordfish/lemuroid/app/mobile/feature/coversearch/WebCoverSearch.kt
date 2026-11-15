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
 * the view model merges whatever came back. The engines are Baidu (needs a
 * cookie warm-up or its anti-spider check answers nothing), Bing (its result
 * JSON arrives HTML-escaped) and 360 image search (plain JSON, no cookie).
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
        systemToken: String = "",
    ): List<CoverCandidate> =
        withContext(Dispatchers.IO) {
            // Baidu's anti-spider check rejects cookie-less requests
            // ("Forbid spider access"), so warm up the session first:
            // the GET drops a BAIDUID cookie into the client's jar and
            // the acjson call below then carries it. Best-effort: if the
            // warm-up itself fails, the search still runs.
            runCatching {
                val warmUp =
                    Request.Builder()
                        .url("https://image.baidu.com/")
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", "https://image.baidu.com/")
                        .build()
                client.newCall(warmUp).execute().use { }
            }
            val url =
                "https://image.baidu.com/search/acjson".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("tn", "resultjson_com")
                    .addQueryParameter("ie", "utf-8")
                    .addQueryParameter("oe", "utf-8")
                    // The system token ("NDS", "GBA", ...) keeps generic
                    // titles from matching posters, books and wallpapers.
                    .addQueryParameter(
                        "word",
                        listOf(query, systemToken, "封面").filter { it.isNotBlank() }.joinToString(" "),
                    )
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
                // objURL is now an ipprf_ encrypted token, not a real
                // address; only trust it when it is an http(s) URL and
                // otherwise fall back to the real CDN addresses.
                val fullImageUrl =
                    originalUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                        ?: middleUrl.ifBlank { thumbUrl }
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
                        referer = "https://image.baidu.com/",
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
        systemToken: String = "",
    ): List<CoverCandidate> =
        withContext(Dispatchers.IO) {
            val searchQuery =
                if (CJK_REGEX.containsMatchIn(query)) {
                    listOf(query, systemToken, "封面").filter { it.isNotBlank() }.joinToString(" ")
                } else {
                    listOf(query, systemToken, "boxart cover").filter { it.isNotBlank() }.joinToString(" ")
                }
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

            // Bing now HTML-escapes the quotes inside its result JSON
            // (&quot;murl&quot;:&quot;...&quot;), which the regexes below
            // cannot match; decode the entities first. turl values also
            // carry &amp; that must become &.
            val decodedHtml = html.replace("&quot;", "\"").replace("&amp;", "&")
            val originals = BING_MURL_REGEX.findAll(decodedHtml).map { unescapeJson(it.groupValues[1]) }.toList()
            val previews = BING_TURL_REGEX.findAll(decodedHtml).map { unescapeJson(it.groupValues[1]) }.toList()
            val titles = BING_TITLE_REGEX.findAll(decodedHtml).map { unescapeJson(it.groupValues[1]) }.toList()

            originals.mapIndexed { index, originalUrl ->
                CoverCandidate(
                    fileName = "web-bing-$index-${originalUrl.hashCode()}",
                    displayName = titles.getOrNull(index)?.ifBlank { query } ?: query,
                    imageUrl = previews.getOrNull(index)?.ifBlank { originalUrl } ?: originalUrl,
                    score = 0.0,
                    fullImageUrl = originalUrl,
                    sourceLabel = "Bing 图片",
                    referer = "https://www.bing.com/",
                )
            }.take(MAX_RESULTS_PER_SOURCE)
        }

    /** 360 image search: keyless JSON API, strongest for Chinese titles. */
    suspend fun search360(
        client: OkHttpClient,
        query: String,
        systemToken: String = "",
    ): List<CoverCandidate> =
        withContext(Dispatchers.IO) {
            val url =
                "https://image.so.com/j".toHttpUrl()
                    .newBuilder()
                    .addQueryParameter(
                        "q",
                        listOf(query, systemToken, "封面").filter { it.isNotBlank() }.joinToString(" "),
                    )
                    .addQueryParameter("src", "srp")
                    .addQueryParameter("sn", "0")
                    .addQueryParameter("pn", "30")
                    .build()
            val request =
                Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://image.so.com/")
                    .build()
            val body =
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("360 image search failed with code ${response.code}")
                    }
                    response.body?.string() ?: throw IOException("Empty 360 image search response")
                }

            val list = JSONObject(body).optJSONArray("list") ?: return@withContext emptyList()
            val candidates = mutableListOf<CoverCandidate>()
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val fullImageUrl = item.optString("img")
                val previewUrl = item.optString("thumb").ifBlank { fullImageUrl }
                if (fullImageUrl.isBlank() || previewUrl.isBlank()) {
                    continue
                }
                val title = item.optString("title").ifBlank { query }
                candidates +=
                    CoverCandidate(
                        fileName = "web-360-$index-${fullImageUrl.hashCode()}",
                        displayName = title,
                        imageUrl = previewUrl,
                        score = 0.0,
                        fullImageUrl = fullImageUrl,
                        sourceLabel = "360 图片",
                        referer = "https://image.so.com/",
                    )
                if (candidates.size >= MAX_RESULTS_PER_SOURCE) {
                    break
                }
            }
            candidates
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
