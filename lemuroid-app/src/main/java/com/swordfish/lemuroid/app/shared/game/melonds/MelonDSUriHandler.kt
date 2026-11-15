package com.swordfish.lemuroid.app.shared.game.melonds

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import me.magnum.melonds.common.uridelegates.UriHandler
import java.io.File

/**
 * Host implementation of the melonDS engine's [UriHandler].
 *
 * The engine opens every file (ROM, SRAM, BIOS) through this delegate via
 * `ContentResolver.openFileDescriptor`, so both `content://` URIs handed out by
 * the Storage Access Framework (game ROMs) and plain `file://` URIs (the save
 * bridge keeps engine saves next to the host saves, see [MelonDSSaveBridge])
 * have to work here.
 */
class MelonDSUriHandler(private val context: Context) : UriHandler {
    override fun fileExists(uri: Uri): Boolean {
        return when (uri.scheme) {
            "file" -> uri.path?.let { File(it).exists() } ?: false
            else -> DocumentFile.fromSingleUri(context, uri)?.exists() ?: false
        }
    }

    override fun createFileDocument(uri: Uri): DocumentFile? {
        return when (uri.scheme) {
            "file" -> {
                val file = File(uri.path ?: return null)
                file.parentFile?.mkdirs()
                if (!file.exists()) {
                    file.createNewFile()
                }
                DocumentFile.fromFile(file)
            }
            // A sibling document cannot be created from a single SAF document
            // URI. In practice this is only ever called for the SRAM file,
            // which the save bridge always addresses with a file:// URI.
            else -> getUriDocument(uri)
        }
    }

    override fun getUriDocument(uri: Uri): DocumentFile? {
        return when (uri.scheme) {
            "file" -> uri.path?.let { DocumentFile.fromFile(File(it)) }
            else -> DocumentFile.fromSingleUri(context, uri)
        }
    }

    override fun getUriTreeDocument(uri: Uri): DocumentFile? {
        return when (uri.scheme) {
            "file" -> uri.path?.let { DocumentFile.fromFile(File(it)) }
            else -> DocumentFile.fromTreeUri(context, uri)
        }
    }
}
