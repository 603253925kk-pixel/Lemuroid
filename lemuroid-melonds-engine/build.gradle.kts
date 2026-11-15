plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    namespace = "me.magnum.melonds"
    ndkVersion = "27.2.12479018"

    defaultConfig {
        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86_64"))
        }
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17 -Wno-write-strings")
                // The host pins minSdk 23 for every module, so AGP would
                // invoke CMake with -DANDROID_PLATFORM=android-23. The
                // engine's OpenGL renderer calls GLES 3.2 entry points
                // (glFramebufferTexture, glColorMaski) that only exist in
                // the NDK's android-24+ GLESv3 stub library, so linking
                // against android-23 fails with undefined symbols (verified
                // locally with AGP's exact parameters). AGP appends DSL
                // arguments after its own, so this override wins. The
                // library manifest minSdk stays 23; the engine code path
                // requires API 24+ at runtime, same as upstream
                // melonDS-android.
                arguments("-DANDROID_PLATFORM=android-24")
                // 16 KB page-size devices (Android 15+ flagships, e.g. the
                // user's Samsung) refuse to load shared libraries whose
                // LOAD segments are only 4 KB aligned: Build APK #44's
                // libmelonDS-lib.so and libmelonDS-android-frontend.so were
                // flagged "LOAD segments not aligned" by the system and
                // the engine failed to load on-device. This NDK toolchain
                // switch links every library in this module with
                // -Wl,-z,max-page-size=16384.
                arguments("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

kotlin {
    compilerOptions {
        // Upstream melonDS-android compiles with this opt-in (used by MacAddress.kt).
        freeCompilerArgs.add("-opt-in=kotlin.ExperimentalUnsignedTypes")
    }
}

dependencies {
    implementation(deps.libs.androidx.ktx.coreKtx)
    implementation(deps.libs.androidx.documentfile)
}
