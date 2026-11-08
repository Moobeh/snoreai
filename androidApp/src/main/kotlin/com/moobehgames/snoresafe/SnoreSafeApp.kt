package com.moobehgames.snoresafe

import android.app.Application
import android.content.Context
import com.moobehgames.snoresafe.core.analysis.DetectionSettings
import com.moobehgames.snoresafe.data.CheckpointStore
import com.moobehgames.snoresafe.data.SessionFileStore
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
    val sessions = SessionFileStore(File(context.filesDir, "sessions"))
    val checkpoints = CheckpointStore(File(context.filesDir, "active-session.json"))
    val recovery = SessionRecovery(checkpoints, sessions)

    // Settings screen comes later; defaults until then.
    val detectionSettings = DetectionSettings()
}

val Context.appGraph: AppGraph get() = (applicationContext as SnoreSafeApp).graph
