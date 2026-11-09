package com.moobehgames.snoresafe.export

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object Sharing {
    private fun authority(context: Context) = "${context.packageName}.files"

    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, authority(context), file)

    /** Opens the system share sheet. Nothing is sent unless the user picks an app. */
    fun share(context: Context, files: List<File>, mime: String, title: String) {
        val uris = files.map { uriFor(context, it) }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.single())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = mime
        intent.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
