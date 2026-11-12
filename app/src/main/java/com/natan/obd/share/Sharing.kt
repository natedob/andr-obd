package com.natan.obd.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object Sharing {
    /** Files to share live in the cache dir, which the FileProvider exposes (see res/xml/file_paths.xml). */
    fun sharedDir(context: Context): File = File(context.cacheDir, "shared").apply { mkdirs() }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
