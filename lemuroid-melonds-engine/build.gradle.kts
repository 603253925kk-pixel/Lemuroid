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
