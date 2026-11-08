package com.moobehgames.snoresafe

import android.app.Application
import android.content.Context
import com.moobehgames.snoresafe.data.AudioStore
import com.moobehgames.snoresafe.data.CheckpointStore
import com.moobehgames.snoresafe.data.LogStore
import com.moobehgames.snoresafe.data.SessionFileStore
import com.moobehgames.snoresafe.data.SettingsStore
import com.moobehgames.snoresafe.recording.NightFinisher
import com.moobehgames.snoresafe.recording.Notifications
import com.moobehgames.snoresafe.recording.SessionRecovery
import java.io.File

class SnoreSafeApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        Notifications.createChannels(this)
        // A fresh process means no session is running here: any leftover checkpoint
        // is a night the phone cut short. Save what we have.
        graph.recovery.recoverOrphanedSession()
    }
}

/** Hand-wired dependencies. Small enough that a DI framework would add more than it saves. */
class AppGraph(context: Context) {
    val settings = SettingsStore(File(context.filesDir, "settings.json"))
    val sessions = SessionFileStore(File(context.filesDir, "sessions"))
    val audio = AudioStore(File(context.filesDir, "audio"))
    val logs = LogStore(File(context.filesDir, "logs"))
    val checkpoints = CheckpointStore(File(context.filesDir, "active-session.json"))
    val finisher = NightFinisher(sessions, audio, settings, logs)
    val recovery = SessionRecovery(checkpoints, finisher)

    /** Deletes a night and all of its audio. */
    fun deleteNight(sessionId: String) {
        audio.deleteSession(sessionId)
        logs.delete(sessionId)
        sessions.delete(sessionId)
    }
}

val Context.appGraph: AppGraph get() = (applicationContext as SnoreSafeApp).graph
