package com.swordfish.lemuroid.app.shared.game.melonds

import android.content.Context
import android.net.Uri
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Smart adaptation data: the NDS fingerprint index and the per-game seed
 * settings (slice 6).
 *
 * The fingerprint index (7547 games, from the libretro-database no-intro
 * Nintendo DS DAT) identifies a game by its 4-character header game code
 * (NDS ROM header offset 0x0C, e.g. "IPKE" for Pokémon HeartGold USA).
 * The seed database (31 games) holds the hand-picked starting settings per
 * game code. Both ship as assets under `smart-adaptation/` and are loaded
 * once, on a background thread, the first time a DS game is launched on
 * the new engine. The index asset is the minified form of the workspace
 * canonical copy (`~/workspace/smart-adaptation/nds-fingerprint-index.json`,
 * same parsed data) to keep the snapshot push well under the size limit.
 *
 * Honest limits, verified against the vendored engine:
 * - the seed "frameskip" field is parsed but NOT applied: the melonDS
 *   engine has no frameskip control anywhere (no such symbol in the
 *   native layer or the Kotlin bridge), so internal-resolution stepping
 *   is the only runtime tuning lever. Nothing is faked.
 * - identification reads only the ROM header (16 bytes); a whole-ROM
 *   hash is never computed at launch.
 */
@Serializable
data class NdsFingerprintEntry(
    @SerialName("game_code") val gameCode: String,
    @SerialName("serial") val serial: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("region") val region: String = "",
    @SerialName("rom_name") val romName: String = "",
    @SerialName("size") val size: Long = 0,
    @SerialName("crc32") val crc32: String = "",
    @SerialName("md5") val md5: String = "",
    @SerialName("sha1") val sha1: String = "",
)

@Serializable
data class SmartAdaptationSeedSettings(
    @SerialName("internalResolution") val internalResolution: Int = 3,
    @SerialName("jit") val jit: Boolean = true,
    /**
     * Parsed for completeness, deliberately not applied: the vendored
     * melonDS engine exposes no frameskip control (see the file doc).
     */
    @SerialName("frameskip") val frameskip: String? = null,
    /**
     * How the game picture fits the shell's screen ("fit" = keep aspect,
     * "fill" = stretch to fill). Null = the system default (FIT). Only
     * set for games that need it — on NDS the difference is ~0.5%
     * vertically (see MelonDSRenderer), so this is the mechanism, not a
     * per-game visual overhaul.
     */
    @SerialName("fit") val fit: String? = null,
)

@Serializable
data class SmartAdaptationSeedGame(
    @SerialName("game_code") val gameCode: String,
    @SerialName("title") val title: String = "",
    @SerialName("crc32") val crc32: String = "",
    @SerialName("md5") val md5: String = "",
    /**
     * Optional shell color theme id for this game (e.g. "midnight") —
     * the seed override wins over the title-keyword rule in
     * SmartAdaptation.resolveShellTheme. Null = no override.
     */
    @SerialName("shell") val shell: String? = null,
    @SerialName("settings") val settings: SmartAdaptationSeedSettings = SmartAdaptationSeedSettings(),
)

object SmartAdaptationDatabase {
    private const val FINGERPRINT_ASSET = "smart-adaptation/nds-fingerprint-index.json"
    private const val SEED_DB_ASSET = "smart-adaptation/smart-adaptation-db-v1.json"

    private val json = Json { ignoreUnknownKeys = true }

    private data class Loaded(
        val fingerprints: Map<String, NdsFingerprintEntry>,
        val seeds: Map<String, SmartAdaptationSeedGame>,
    )

    private val lock = Any()

    @Volatile
    private var loaded: Loaded? = null

    /** Loads both asset files once (call from a background thread). */
    fun load(context: Context): Boolean {
        loaded?.let { return true }
        return runCatching {
            val fingerprints =
                context.assets.open(FINGERPRINT_ASSET).use { stream ->
                    json.decodeFromString(
                        ListSerializer(NdsFingerprintEntry.serializer()),
                        stream.readBytes().decodeToString(),
                    )
                }
            // A few game codes appear more than once (re-dumps of the same
            // game+region); first entry wins, the rest are the same game.
            val fingerprintMap = LinkedHashMap<String, NdsFingerprintEntry>(fingerprints.size)
            for (entry in fingerprints) {
                fingerprintMap.putIfAbsent(entry.gameCode, entry)
            }
            val seeds =
                context.assets.open(SEED_DB_ASSET).use { stream ->
                    json.decodeFromString(
                        ListSerializer(SmartAdaptationSeedGame.serializer()),
                        stream.readBytes().decodeToString(),
                    )
                }
            val seedMap = LinkedHashMap<String, SmartAdaptationSeedGame>(seeds.size)
            for (seed in seeds) {
                seedMap.putIfAbsent(seed.gameCode, seed)
            }
            synchronized(lock) {
                loaded = Loaded(fingerprintMap, seedMap)
            }
            Timber.i("Smart adaptation: loaded ${fingerprintMap.size} fingerprints, ${seedMap.size} seed settings")
            true
        }.onFailure {
            Timber.e(it, "Smart adaptation: failed to load databases, using defaults")
        }.getOrDefault(false)
    }

    fun fingerprintFor(context: Context, gameCode: String): NdsFingerprintEntry? {
        if (loaded == null) load(context)
        return loaded?.fingerprints?.get(gameCode)
    }

    fun seedFor(context: Context, gameCode: String): SmartAdaptationSeedGame? {
        if (loaded == null) load(context)
        return loaded?.seeds?.get(gameCode)
    }
}

/**
 * Reads the 4-character game code from an NDS ROM header (offset 0x0C).
 *
 * Best-effort: any failure returns null and the caller falls back to the
 * default settings. A zip-wrapped ROM is handled by opening the archive
 * and reading the first `.nds` entry's header (the engine itself loads
 * the raw file, so in practice the ROM is already extracted).
 */
object NdsGameCodeReader {
    private const val HEADER_SIZE = 16
    private const val GAME_CODE_OFFSET = 12
    private const val GAME_CODE_LENGTH = 4

    fun readGameCode(context: Context, fileUri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                readFromStream(stream) ?: readFromZip(context, fileUri)
            }
        }.onFailure {
            Timber.w(it, "Smart adaptation: could not read ROM header for game code")
        }.getOrNull()
    }

    private fun readFromStream(stream: InputStream): String? {
        val header = ByteArray(HEADER_SIZE)
        if (!readFully(stream, header)) return null
        if (isZipSignature(header)) return null
        return parseGameCode(header)
    }

    private fun readFromZip(context: Context, fileUri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(fileUri)?.use { stream ->
                ZipInputStream(stream).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name.endsWith(".nds", ignoreCase = true)) {
                            val header = ByteArray(HEADER_SIZE)
                            if (readFully(zip, header)) return parseGameCode(header)
                            return null
                        }
                        entry = zip.nextEntry
                    }
                    null
                }
            }
        }.getOrNull()
    }

    private fun readFully(stream: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val read = stream.read(buffer, offset, buffer.size - offset)
            if (read < 0) return false
            offset += read
        }
        return true
    }

    private fun isZipSignature(header: ByteArray): Boolean {
        return header[0] == 'P'.code.toByte() &&
            header[1] == 'K'.code.toByte() &&
            header[2] == 0x03.toByte() &&
            header[3] == 0x04.toByte()
    }

    private fun parseGameCode(header: ByteArray): String? {
        val code =
            header.copyOfRange(GAME_CODE_OFFSET, GAME_CODE_OFFSET + GAME_CODE_LENGTH)
                .toString(Charsets.US_ASCII)
        // A real game code is 4 printable ASCII characters (letters/digits).
        if (code.length != GAME_CODE_LENGTH || code.any { it.code !in 0x21..0x7E }) return null
        return code
    }
}
