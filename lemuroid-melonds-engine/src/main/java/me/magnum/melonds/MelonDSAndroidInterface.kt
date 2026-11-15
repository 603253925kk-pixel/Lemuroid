package me.magnum.melonds

import me.magnum.melonds.common.UriFileHandler

object MelonDSAndroidInterface {
    init {
        // Upstream melonDS-android loads the frontend library from its app
        // layer (the Application class), which this vendored module does not
        // include. Without this, the first native call dies with
        // UnsatisfiedLinkError "No implementation found" (seen on device in
        // Build APK #47: ensureSetup stage). Loading here makes the facade
        // self-sufficient; the core library is pulled in as a dependency.
        System.loadLibrary("melonDS-android-frontend")
    }

    external fun setup(uriFileHandler: UriFileHandler)
    external fun cleanup()
}