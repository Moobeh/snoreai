package com.moobehgames.snoresafe.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.storage.StorageManager

/** Space the app can use for audio, counting cache the system would clear for us. */
@SuppressLint("UsableSpace") // fallback for Android 7, which has no allocatable-bytes API
fun freeBytes(context: Context): Long {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching {
            val sm = context.getSystemService(StorageManager::class.java)
            return sm.getAllocatableBytes(sm.getUuidForPath(context.filesDir))
        }
    }
    return context.filesDir.usableSpace
}
