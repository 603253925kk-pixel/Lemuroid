plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("androidx.navigation.safeargs.kotlin")
    id("kotlinx-serialization")
    id("androidx.baselineprofile")
    id("org.jetbrains.kotlin.plugin.compose")
}

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

// Fusion batch 1: Flycast / Azahar / SameBoy are prebuilt libretro cores
// downloaded from the buildbot at build time (see downloadFusionCores
// below) and bundled as jniLibs. They cannot travel through the git
// snapshot-push channel, and the LemuroidCores submodule is a separate
// repo this fork cannot modify, so the app module fetches them itself.
val fusionCoresDir = layout.buildDirectory.dir("fusion-cores")

android {
    defaultConfig {
        // Every CI build used to ship the same versionCode 252, so the
        // phone's installer offered each new build as a same-version
        // "reinstall" instead of an upgrade (user noticed on Build
        // APK #45). Add the CI run number so versionCode strictly
        // increases with every build; local builds keep the base value.
        versionCode = 252 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0)
        versionName = "1.17.0" // Always remember to update Cores Tag!
        applicationId = "com.swordfish.lemuroid"
    }
    flavorDimensions += listOf("opensource", "cores")

    if (usePlayDynamicFeatures()) {
        println("Building Google Play version. Bundling dynamic features.")
        dynamicFeatures.addAll(
            setOf(
                ":lemuroid_core_desmume",
                ":lemuroid_core_dosbox_pure",
                ":lemuroid_core_fbneo",
                ":lemuroid_core_fceumm",
                ":lemuroid_core_gambatte",
                ":lemuroid_core_genesis_plus_gx",
                ":lemuroid_core_handy",
                ":lemuroid_core_mame2003_plus",
                ":lemuroid_core_mednafen_ngp",
                ":lemuroid_core_mednafen_pce_fast",
                ":lemuroid_core_mednafen_wswan",
                ":lemuroid_core_melonds",
                ":lemuroid_core_mgba",
                ":lemuroid_core_mupen64plus_next_gles3",
                ":lemuroid_core_pcsx_rearmed",
                ":lemuroid_core_ppsspp",
                ":lemuroid_core_prosystem",
                ":lemuroid_core_snes9x",
                ":lemuroid_core_stella",
                ":lemuroid_core_citra",
            ),
        )
    }

    // Since some dependencies are closed source we make a completely free as in free speech variant.

    productFlavors {

        create("free") {
            dimension = "opensource"
        }

        create("play") {
            dimension = "opensource"
        }

        // Include cores in the final apk
        create("bundle") {
            dimension = "cores"
        }

        // Download cores on demand (from GooglePlay or GitHub)
        create("dynamic") {
            dimension = "cores"
        }
    }

    packagingOptions {
        jniLibs {
            // Stripping created some issues with some libretro cores such as ppsspp
            keepDebugSymbols += setOf("*/*/*_libretro_android.so")
            useLegacyPackaging = true
        }
        resources {
            excludes += setOf("META-INF/DEPENDENCIES", "META-INF/library_release.kotlin_module")
        }
    }

    sourceSets {
        named("main") {
            // Prebuilt fusion cores, downloaded by downloadFusionCores.
            jniLibs.srcDir(fusionCoresDir)
        }
    }

    signingConfigs {
        maybeCreate("debug").apply {
            storeFile = file("$rootDir/debug.keystore")
        }

        maybeCreate("release").apply {
            storeFile = file("$rootDir/release.jks")
            keyAlias = "lemuroid"
            storePassword = "lemuroid"
            keyPassword = "lemuroid"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs["release"]
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
            resValue("string", "lemuroid_name", "Lemuroid")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            resValue("string", "lemuroid_name", "LemuroiDebug")
        }
    }

    lint {
        disable += setOf("MissingTranslation", "ExtraTranslation", "EnsureInitializerMetadata")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = deps.versions.kotlinExtension
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    namespace = "com.swordfish.lemuroid"
}

// Downloads the fusion batch 1 prebuilt libretro cores (Flycast, Azahar,
// SameBoy) from the libretro buildbot and stages them under
// build/fusion-cores/<abi>/, where the main sourceSet picks them up as
// jniLibs (see sourceSets above). The buildbot keeps no dated core
// archives — only a rolling `latest/` directory — so the task pins the
// source to `latest/` and instead verifies every downloaded .so: valid
// zip, expected file present, sane size, ELF magic, and 16 KB PT_LOAD
// alignment (the target phone is a 16 KB page-size device; a misaligned
// core would fail to load). arm64-v8a is required for every core and any
// validation failure there fails the build loudly instead of shipping a
// broken APK. The other ABIs are best-effort: a 404, a failed
// validation, or a truncated download only warns and skips that ABI
// (the 32-bit buildbot cores are 4 KB aligned, so they are skipped on
// purpose rather than bundled broken). The download retries a few times:
// the buildbot occasionally answers a transient 404 while it rotates
// `latest/`. Azahar ships arm64-v8a only: the 3DS system entry restricts
// it to that ABI. To bump a core, edit the fetchFusionCore calls below.
tasks.register("downloadFusionCores") {
    // The task downloads from the network and validates the binaries at
    // execution time, which cannot be serialized into the configuration
    // cache; opt out instead of failing the build on cache store.
    notCompatibleWithConfigurationCache(
        "Downloads prebuilt cores from the network and validates them at execution time",
    )
    doLast {
        // Fails the build unless every PT_LOAD segment of the .so is
        // 16 KB aligned. Same check the local tooling uses (verified
        // against the 2026-11-15 nightly downloads).
        fun checkElf16kAlignment(soFile: File) {
            val bytes = soFile.readBytes()
            if (bytes.size < 64) {
                throw org.gradle.api.GradleException("${soFile.name}: too small to be an ELF file")
            }
            if (bytes[0] != 0x7F.toByte() || bytes[1] != 'E'.code.toByte() ||
                bytes[2] != 'L'.code.toByte() || bytes[3] != 'F'.code.toByte()
            ) {
                throw org.gradle.api.GradleException("${soFile.name}: not an ELF file")
            }
            fun u16(off: Int): Int =
                (bytes[off].toInt() and 0xFF) or ((bytes[off + 1].toInt() and 0xFF) shl 8)
            fun u32(off: Int): Long {
                var v = 0L
                for (i in 0 until 4) v = v or ((bytes[off + i].toLong() and 0xFF) shl (8 * i))
                return v
            }
            fun u64(off: Int): Long {
                var v = 0L
                for (i in 0 until 8) v = v or ((bytes[off + i].toLong() and 0xFF) shl (8 * i))
                return v
            }
            val is64Bit = bytes[4].toInt() == 2
            val phoff = if (is64Bit) u64(32) else u32(28)
            val phentsize = if (is64Bit) u16(54) else u16(42)
            val phnum = if (is64Bit) u16(56) else u16(44)
            // p_type at +0 (4 bytes); p_align at +48 (8 bytes) for
            // 64-bit, +28 (4 bytes) for 32-bit.
            val alignOff = if (is64Bit) 48 else 28
            var sawLoad = false
            for (i in 0 until phnum) {
                val entryOff = (phoff + i.toLong() * phentsize).toInt()
                if (u32(entryOff).toInt() != 1) continue // not PT_LOAD
                sawLoad = true
                val align = if (is64Bit) u64(entryOff + alignOff) else u32(entryOff + alignOff)
                if (align < 16384) {
                    throw org.gradle.api.GradleException(
                        "${soFile.name}: PT_LOAD segment $i has p_align=$align, " +
                            "expected 16384 (16 KB page size). Refusing to bundle " +
                            "a core that would fail to load on 16 KB devices.",
                    )
                }
            }
            if (!sawLoad) {
                throw org.gradle.api.GradleException("${soFile.name}: no PT_LOAD segments found")
            }
        }

        // Downloads one core zip, retrying a few times: the buildbot
        // occasionally answers a transient 404 while it rotates
        // `latest/`. Returns null (after warning) when a best-effort
        // ABI stays unavailable; throws for required ABIs.
        fun downloadZip(url: URL, zipName: String, abi: String, required: Boolean): File? {
            var lastCode = -1
            for (attempt in 1..3) {
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Lemuroid-FusionCores/1.0")
                conn.connectTimeout = 30_000
                conn.readTimeout = 180_000
                try {
                    lastCode = conn.responseCode
                    if (lastCode == 200) {
                        val zipFile = File.createTempFile("fusion-core-", ".zip")
                        conn.inputStream.use { input ->
                            zipFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        return zipFile
                    }
                } catch (e: IOException) {
                    lastCode = -1
                } finally {
                    conn.disconnect()
                }
                if (attempt < 3) Thread.sleep(5_000)
            }
            val msg =
                "downloadFusionCores: $zipName for $abi unavailable " +
                    "(HTTP $lastCode)"
            if (required) throw org.gradle.api.GradleException(msg)
            println("$msg, skipping")
            return null
        }

        fun fetchFusionCore(
            zipName: String,
            innerName: String,
            targetName: String,
            abi: String,
            minSizeBytes: Long,
            required: Boolean,
            outDir: File,
        ) {
            val url =
                URL("https://buildbot.libretro.com/nightly/android/latest/$abi/$zipName")
            val zipFile = downloadZip(url, zipName, abi, required) ?: return
            val destFile = File(File(outDir, abi), targetName)
            try {
                if (zipFile.length() < 10_000) {
                    throw org.gradle.api.GradleException(
                        "$zipName for $abi downloaded only ${zipFile.length()} bytes, " +
                            "looks truncated",
                    )
                }
                val destDir = File(outDir, abi)
                var found = false
                ZipInputStream(zipFile.inputStream().buffered()).use { zin ->
                    var entry = zin.nextEntry
                    while (entry != null) {
                        if (entry.name == innerName) {
                            destDir.mkdirs()
                            destFile.outputStream().use { out -> zin.copyTo(out) }
                            found = true
                            break
                        }
                        entry = zin.nextEntry
                    }
                }
                if (!found) {
                    throw org.gradle.api.GradleException("$innerName not found in $zipName ($abi)")
                }
                if (destFile.length() < minSizeBytes) {
                    throw org.gradle.api.GradleException(
                        "$innerName ($abi) is only ${destFile.length()} bytes, " +
                            "expected at least $minSizeBytes",
                    )
                }
                checkElf16kAlignment(destFile)
                println(
                    "downloadFusionCores: $targetName ($abi) " +
                        "${destFile.length()} bytes, 16 KB aligned",
                )
            } catch (e: org.gradle.api.GradleException) {
                // A best-effort ABI that fails validation (e.g. the
                // 32-bit buildbot cores are 4 KB aligned) is skipped
                // with a warning instead of failing the build; the
                // 16 KB check still fails loudly for required ABIs.
                if (required) throw e
                println("downloadFusionCores: ${e.message} ($abi), skipping")
                destFile.delete()
            } finally {
                zipFile.delete()
            }
        }

        val outDir = fusionCoresDir.get().asFile
        // Flycast — Dreamcast / Naomi / Atomiswave.
        fetchFusionCore(
            "flycast_libretro_android.so.zip",
            "flycast_libretro_android.so",
            "libflycast_libretro_android.so",
            "arm64-v8a",
            1_000_000,
            true,
            outDir,
        )
        fetchFusionCore(
            "flycast_libretro_android.so.zip",
            "flycast_libretro_android.so",
            "libflycast_libretro_android.so",
            "armeabi-v7a",
            1_000_000,
            false,
            outDir,
        )
        fetchFusionCore(
            "flycast_libretro_android.so.zip",
            "flycast_libretro_android.so",
            "libflycast_libretro_android.so",
            "x86_64",
            1_000_000,
            false,
            outDir,
        )
        // Azahar — Nintendo 3DS (arm64-v8a only).
        fetchFusionCore(
            "azahar_libretro.so.zip",
            "azahar_libretro.so",
            "azahar_libretro.so",
            "arm64-v8a",
            5_000_000,
            true,
            outDir,
        )
        // SameBoy — Game Boy / Game Boy Color.
        fetchFusionCore(
            "sameboy_libretro_android.so.zip",
            "sameboy_libretro_android.so",
            "libsameboy_libretro_android.so",
            "arm64-v8a",
            100_000,
            true,
            outDir,
        )
        fetchFusionCore(
            "sameboy_libretro_android.so.zip",
            "sameboy_libretro_android.so",
            "libsameboy_libretro_android.so",
            "armeabi-v7a",
            100_000,
            false,
            outDir,
        )
        fetchFusionCore(
            "sameboy_libretro_android.so.zip",
            "sameboy_libretro_android.so",
            "libsameboy_libretro_android.so",
            "x86_64",
            100_000,
            false,
            outDir,
        )
    }
}

tasks.named("preBuild") {
    dependsOn("downloadFusionCores")
}

dependencies {
    implementation(project(":retrograde-util"))
    implementation(project(":retrograde-app-shared"))
    implementation(project(":lemuroid-metadata-libretro-db"))
    implementation(project(":lemuroid-touchinput"))
    implementation(project(":lemuroid-melonds-engine"))

    "baselineProfile"(project(":baselineprofile"))
    implementation(deps.libs.androidx.profileInstaller)

    "bundleImplementation"(project(":bundled-cores"))

    "freeImplementation"(project(":lemuroid-app-ext-free"))
    "playImplementation"(project(":lemuroid-app-ext-play"))

    implementation(deps.libs.androidx.navigation.navigationFragment)
    implementation(deps.libs.androidx.navigation.navigationUi)
    implementation(deps.libs.androidx.navigation.compose)
    implementation(deps.libs.material)
    implementation(deps.libs.coil.coil)
    implementation(deps.libs.coil.coilCompose)
    implementation(deps.libs.androidx.appcompat.constraintLayout)
    implementation(deps.libs.androidx.activity.activity)
    implementation(deps.libs.androidx.activity.activityKtx)
    implementation(deps.libs.androidx.activity.compose)
    implementation(deps.libs.androidx.appcompat.appcompat)
    implementation(deps.libs.androidx.preferences.preferencesKtx)
    implementation(deps.libs.arch.work.runtime)
    implementation(deps.libs.arch.work.runtimeKtx)
    implementation(deps.libs.androidx.lifecycle.commonJava8)
    implementation(deps.libs.androidx.lifecycle.reactiveStreams)

    kapt(deps.libs.androidx.lifecycle.processor)

    implementation(deps.libs.androidx.leanback.leanback)
    implementation(deps.libs.androidx.leanback.leanbackPreference)
    implementation(deps.libs.androidx.leanback.leanbackPaging)

    implementation(deps.libs.androidx.appcompat.recyclerView)
    implementation(deps.libs.androidx.paging.common)
    implementation(deps.libs.androidx.paging.runtime)
    implementation(deps.libs.androidx.room.common)
    implementation(deps.libs.androidx.room.runtime)
    implementation(deps.libs.androidx.room.ktx)
    implementation(deps.libs.dagger.android.core)
    implementation(deps.libs.dagger.android.support)
    implementation(deps.libs.dagger.core)
    implementation(deps.libs.kotlinxCoroutinesAndroid)
    implementation(deps.libs.okHttp3)
    implementation(deps.libs.okio)
    implementation(deps.libs.retrofit)
    implementation(deps.libs.flowPreferences)
    implementation(deps.libs.guava)
    implementation(deps.libs.androidx.documentfile)
    implementation(deps.libs.androidx.leanback.tvProvider)
    implementation(deps.libs.harmony)
    implementation(deps.libs.startup)
    implementation(deps.libs.kotlin.serialization)
    implementation(deps.libs.kotlin.serializationJson)

    implementation(platform(deps.libs.androidx.compose.composeBom))
    implementation(deps.libs.androidx.compose.material3)
    implementation(deps.libs.androidx.compose.constraintLayout)
    debugImplementation(deps.libs.androidx.compose.tooling)
    implementation(deps.libs.androidx.compose.toolingPreview)
    implementation(deps.libs.androidx.compose.extendedIcons)
    implementation(deps.libs.androidx.compose.accompanist.systemUiController)
    implementation(deps.libs.androidx.compose.accompanist.navigationMaterial)
    implementation(deps.libs.androidx.compose.accompanist.drawablePainter)
    implementation(deps.libs.androidx.paging.compose)
    implementation(deps.libs.androidx.lifecycle.viewModelCompose)
    implementation(deps.libs.composeHtmlText)

    implementation(deps.libs.composeSettings.uiTiles)
    implementation(deps.libs.composeSettings.uiTilesExtended)
    implementation(deps.libs.composeSettings.diskStorage)
    implementation(deps.libs.composeSettings.memoryStorage)

    implementation(deps.libs.libretrodroid)

    // Uncomment this when using a local aar file.
    // implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    kapt(deps.libs.dagger.android.processor)
    kapt(deps.libs.dagger.compiler)
}

fun usePlayDynamicFeatures(): Boolean {
    val task = gradle.startParameter.taskRequests.toString()
    return task.contains("Play") && task.contains("Dynamic")
}
