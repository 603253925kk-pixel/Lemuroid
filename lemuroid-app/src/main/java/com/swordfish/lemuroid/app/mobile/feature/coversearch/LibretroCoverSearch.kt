package com.swordfish.lemuroid.app.mobile.feature.coversearch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
* Online cover search backed by the public libretro-thumbnails library.
*
* For a given system, the library exposes an Apache-style directory index at
* `https://thumbnails.libretro.com/<repository>/Named_Boxarts/` which lists every
* available boxart as a PNG file. This object fetches that index once, then ranks
* file names against the user's keywords locally, so editing the keywords does
* not require another download.
*/
object LibretroCoverSearch {
private const val THUMBNAILS_BASE_URL = "https://thumbnails.libretro.com"
private const val BOXARTS_DIRECTORY = "Named_Boxarts"

const val MAX_RESULTS = 24
private const val MAX_COVER_DIMENSION = 1024
private const val COVER_JPEG_QUALITY = 85
private const val MAX_COMPARED_LENGTH = 64
private const val MIN_CANDIDATE_SCORE = 0.15

private val HREF_REGEX = Regex("""href\s*=\s*"([^"]+?\.png)"""", RegexOption.IGNORE_CASE)
private val BRACKETED_REGEX = Regex("""[\(\[）]*[\)\]】）]""")
private val VERSION_REGEX = Regex("""\d+(?:\.\d+)+|\bv\d+\b""", RegexOption.IGNORE_CASE)
private val TAG_REGEX = Regex("""\([^)]*\)|\[[^\]]*\]""")
private val NON_WORD_REGEX = Regex("""[^\p{L}\p{N}]+""")
private val WHITESPACE_REGEX = Regex("""\s+""")
private val EXTENSION_REGEX = Regex("""[A-Za-z0-9]{2,4}""")
private val PNG_SUFFIX_REGEX = Regex("""\.png$""", RegexOption.IGNORE_CASE)

/** Edition markers commonly appended to Chinese ROM release names. */
private val EDITION_SUFFIXES =
listOf(
"正式版",
"汉化版",
"中文版",
"完全版",
"最终版",
"重制版",
"复刻版",
"加强版",
"美版",
"日版",
"欧版",
"中文",
"汉化",
)

private val ARTICLES = setOf("the", "a", "an")

    /**
     * Lemuroid system -> directory name on thumbnails.libretro.com.
     *
     * Values match [GameSystem.libretroFullName], except for MAME2003PLUS whose
     * thumbnails live in the generic "MAME" repository (same special case the
     * metadata provider applies when computing cover URLs).
     */
    private val REPOSITORY_NAMES: Map<SystemID, String> =
        mapOf(
            SystemID.ATARI2600 to "Atari - 2600",
            SystemID.ATARI7800 to "Atari - 7800",
            SystemID.LYNX to "Atari - Lynx",
            SystemID.DOS to "DOS",
            SystemID.FBNEO to "FBNeo - Arcade Games",
            SystemID.MAME2003PLUS to "MAME",
            SystemID.PC_ENGINE to "NEC - PC Engine - TurboGrafx 16",
            SystemID.NES to "Nintendo - Nintendo Entertainment System",
            SystemID.SNES to "Nintendo - Super Nintendo Entertainment System",
            SystemID.GB to "Nintendo - Game Boy",
            SystemID.GBC to "Nintendo - Game Boy Color",
            SystemID.GBA to "Nintendo - Game Boy Advance",
            SystemID.N64 to "Nintendo - Nintendo 64",
            SystemID.NDS to "Nintendo - Nintendo DS",
            SystemID.NINTENDO_3DS to "Nintendo - Nintendo 3DS",
            SystemID.GENESIS to "Sega - Mega Drive - Genesis",
            SystemID.SEGACD to "Sega - Mega-CD - Sega CD",
            SystemID.SMS to "Sega - Master System - Mark III",
            SystemID.GG to "Sega - Game Gear",
            SystemID.PSX to "Sony - PlayStation",
            SystemID.PSP to "Sony - PlayStation Portable",
            SystemID.NGP to "SNK - Neo Geo Pocket",
            SystemID.NGC to "SNK - Neo Geo Pocket Color",
            SystemID.WS to "Bandai - WonderSwan",
            SystemID.WSC to "Bandai - WonderSwan Color",
        )

    data class CoverCandidate(
        val fileName: String,
        val displayName: String,
        val imageUrl: String,
        val score: Double,
    )

    /**
     * Resolves the thumbnails repository directory for a game. Falls back to the
     * system's libretro full name, then to the raw system id, so unknown systems
     * still produce a best-effort URL (which will simply yield no results).
     */
    fun repositoryNameFor(game: Game): String {
        val systemId = SystemID.values().firstOrNull { it.dbname == game.systemId }
        return REPOSITORY_NAMES[systemId]
            ?: runCatching { GameSystem.findById(game.systemId).libretroFullName }
                .getOrElse { game.systemId }
    }

    fun boxartsIndexUrl(repositoryName: String): HttpUrl =
        THUMBNAILS_BASE_URL.toHttpUrl()
            .newBuilder()
            .addPathSegment(repositoryName)
            .addPathSegment(BOXARTS_DIRECTORY)
            .build()

    fun boxartUrl(
        repositoryName: String,
        fileName: String,
    ): String =
        THUMBNAILS_BASE_URL.toHttpUrl()
            .newBuilder()
            .addPathSegment(repositoryName)
            .addPathSegment(BOXARTS_DIRECTORY)
            .addPathSegment(fileName)
            .build()
            .toString()

    /** Downloads and parses the boxart directory index for [repositoryName]. */
    suspend fun fetchBoxartFileNames(
        client: OkHttpClient,
        repositoryName: String,
    ): List<String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(boxartsIndexUrl(repositoryName)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Boxart index request failed with code ${response.code}")
                }
                val html = response.body?.string()
                    ?: throw IOException("Empty boxart index response")
                parseBoxartFileNames(html)
            }
        }

    private fun parseBoxartFileNames(html: String): List<String> =
        HREF_REGEX.findAll(html)
            .map { match -> percentDecode(match.groupValues[1].substringAfterLast('/')) }
            .filter { it.endsWith(".png", ignoreCase = true) }
            .distinct()
            .toList()

    /**
     * Turns a raw game title into the default search keywords: file extensions,
     * bracketed notes, version numbers and Chinese edition suffixes are removed.
     * For example "起源心金4.03正式版" becomes "起源心金".
     */
    fun cleanSearchTitle(rawTitle: String): String {
        // Only strip a trailing extension when the suffix actually looks like
        // one (titles themselves may contain dots, e.g. in version numbers).
        val extension = rawTitle.substringAfterLast('.', "")
        var title =
            if (extension.matches(EXTENSION_REGEX)) {
                rawTitle.substringBeforeLast('.')
            } else {
                rawTitle
            }
        if (title.isBlank()) {
            title = rawTitle
        }
        title = title.replace(BRACKETED_REGEX, " ")
        title = title.replace(VERSION_REGEX, " ")
        EDITION_SUFFIXES.forEach { suffix -> title = title.replace(suffix, " ") }
        return title.replace(WHITESPACE_REGEX, " ").trim()
    }

    /**
     * Ranks [fileNames] against [query] and returns the best [MAX_RESULTS]
     * candidates, most similar first. Purely local: safe to call again whenever
     * the keywords change.
     */
    fun rankCandidates(
        fileNames: List<String>,
        repositoryName: String,
        query: String,
    ): List<CoverCandidate> {
        val queryTokens = tokensOf(normalizeName(query))
        if (queryTokens.isEmpty()) {
            return emptyList()
        }
        val queryJoined = queryTokens.joinToString("").take(MAX_COMPARED_LENGTH)

        return fileNames.mapNotNull { fileName ->
            val displayName = fileName.replace(PNG_SUFFIX_REGEX, "")
            val candidateTokens = tokensOf(normalizeName(displayName))
            if (candidateTokens.isEmpty()) {
                return@mapNotNull null
            }

            val tokenCoverage = tokenCoverage(queryTokens, candidateTokens)
            val candidateJoined = candidateTokens.joinToString("").take(MAX_COMPARED_LENGTH)
            val substringBonus =
                if (candidateJoined.contains(queryJoined) || queryJoined.contains(candidateJoined)) {
                    1.0
                } else {
                    0.0
                }

            if (tokenCoverage == 0.0 && substringBonus == 0.0) {
                return@mapNotNull null
            }

            val editSimilarity = levenshteinRatio(queryJoined, candidateJoined)
            val score = 0.6 * tokenCoverage + 0.25 * substringBonus + 0.15 * editSimilarity
            if (score < MIN_CANDIDATE_SCORE) {
                return@mapNotNull null
            }

            CoverCandidate(
                fileName = fileName,
                displayName = displayName,
                imageUrl = boxartUrl(repositoryName, fileName),
                score = score,
            )
        }.sortedWith(
            compareByDescending<CoverCandidate> { it.score }.thenBy { it.displayName },
        ).take(MAX_RESULTS)
    }

    /**
     * Downloads the original boxart, downscales it if needed and writes it as a
     * JPEG into [targetFile]. The file can then go through the exact same
     * set-custom-cover path used for images picked from the gallery.
     */
    suspend fun downloadCoverAsJpegFile(
        client: OkHttpClient,
        candidate: CoverCandidate,
        targetFile: File,
    ): File =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(candidate.imageUrl).build()
            val bytes =
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Cover download failed with code ${response.code}")
                    }
                    response.body?.bytes() ?: throw IOException("Empty cover response")
                }

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: throw IOException("Downloaded cover is not a readable image")
            val scaled = scaleDown(bitmap, MAX_COVER_DIMENSION)

            targetFile.parentFile?.mkdirs()
            FileOutputStream(targetFile).use { output ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, COVER_JPEG_QUALITY, output)) {
                    throw IOException("Failed to encode cover as JPEG")
                }
            }
            Timber.d("Saved downloaded cover to ${targetFile.absolutePath}")
            targetFile
        }

    private fun scaleDown(
        bitmap: Bitmap,
        maxDimension: Int,
    ): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDimension) {
            return bitmap
        }
        val ratio = maxDimension.toDouble() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    /** Lowercases, drops region/revision tags and punctuation, keeps letters and digits. */
    private fun normalizeName(name: String): String =
        name.replace(TAG_REGEX, " ")
            .lowercase()
            .replace(NON_WORD_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private fun tokensOf(normalizedName: String): List<String> =
        normalizedName.split(' ').filter { it.isNotBlank() && it !in ARTICLES }

    /** Weighted share of query tokens that have a counterpart among the candidate tokens. */
    private fun tokenCoverage(
        queryTokens: List<String>,
        candidateTokens: List<String>,
    ): Double {
        val total =
            queryTokens.sumOf { queryToken ->
                candidateTokens.maxOfOrNull { candidateToken ->
                    when {
                        candidateToken == queryToken -> 1.0
                        minOf(candidateToken.length, queryToken.length) >= 3 &&
                            (candidateToken.contains(queryToken) || queryToken.contains(candidateToken)) -> 0.5
                        else -> 0.0
                    }
                } ?: 0.0
            }
        return total / queryTokens.size
    }

    /** 1 - (edit distance / longest length), in [0, 1]. */
    private fun levenshteinRatio(
        first: String,
        second: String,
    ): Double {
        if (first.isEmpty() || second.isEmpty()) {
            return 0.0
        }
        var previous = IntArray(second.length + 1) { it }
        var current = IntArray(second.length + 1)
        for (i in 1..first.length) {
            current[0] = i
            for (j in 1..second.length) {
                val cost = if (first[i - 1] == second[j - 1]) 0 else 1
                current[j] =
                    minOf(
                        previous[j] + 1,
                        current[j - 1] + 1,
                        previous[j - 1] + cost,
                    )
            }
            val swap = previous
            previous = current
            current = swap
        }
        val distance = previous[second.length]
        return 1.0 - distance.toDouble() / maxOf(first.length, second.length)
    }

    /** Decodes %XX sequences as UTF-8. Unlike URLDecoder, '+' is kept as-is (path segment rules). */
    private fun percentDecode(value: String): String {
        if (!value.contains('%')) {
            return value
        }
        val bytes = ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            val hexPair =
                if (char == '%' && index + 2 < value.length) {
                    value.substring(index + 1, index + 3)
                } else {
                    null
                }
            val decodedByte = hexPair?.toIntOrNull(16)
            if (decodedByte != null) {
                bytes.write(decodedByte)
                index += 3
            } else {
                bytes.write(char.toString().toByteArray(Charsets.UTF_8))
                index++
            }
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}
