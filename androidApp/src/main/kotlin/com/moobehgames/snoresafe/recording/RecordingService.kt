package com.moobehgames.snoresafe.recording

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.moobehgames.snoresafe.appGraph
import com.moobehgames.snoresafe.audio.ClipRecorder
import com.moobehgames.snoresafe.audio.MicSource
import com.moobehgames.snoresafe.core.analysis.FrameEvidence
import com.moobehgames.snoresafe.core.analysis.Loudness
import com.moobehgames.snoresafe.core.analysis.SnoreDetector
import com.moobehgames.snoresafe.core.analysis.SoundTaxonomy
import com.moobehgames.snoresafe.core.session.EndReason
import com.moobehgames.snoresafe.core.session.SessionAccumulator
import com.moobehgames.snoresafe.data.freeBytes
import com.moobehgames.snoresafe.ml.YamnetClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

/**
 * Overnight listener. A microphone foreground service: audio → YAMNet → snore detector →
 * session statistics, entirely on the phone. Raw audio is analysed and discarded.
 */
class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile private var stopRequested = false
    @Volatile private var pauseRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> if (job == null) stopSelf() else stopRequested = true
            ACTION_PAUSE -> pauseRequested = true
            ACTION_RESUME -> pauseRequested = false
        }
        // Never auto-restart: Android forbids starting the microphone from the background.
        // If the phone kills us, the checkpoint is recovered on next launch instead.
        return START_NOT_STICKY
    }

    private fun start() {
        if (job != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            RecordingStateHolder.set(RecordingState.Failed(RecordingState.Reason.PERMISSION))
            stopSelf()
            return
        }
        try {
            ServiceCompat.startForeground(
                this,
                Notifications.ID_RECORDING,
                Notifications.recording(this),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Could not enter foreground", e)
            RecordingStateHolder.set(RecordingState.Failed(RecordingState.Reason.OTHER, e.message))
            stopSelf()
            return
        }
        stopRequested = false
        pauseRequested = false
        RecordingStateHolder.set(RecordingState.Starting)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SnoreSafe:session")
            .apply { acquire(MAX_SESSION_MS) }
        job = scope.launch { runSession() }
    }

    private fun CoroutineScope.runSession() {
        val graph = appGraph
        val settings = graph.settings.current
        var result: RecordingState = RecordingState.Idle
        try {
            val classifier = try {
                YamnetClassifier.create(this@RecordingService)
            } catch (e: Exception) {
                Log.e(TAG, "Model load failed", e)
                result = RecordingState.Failed(RecordingState.Reason.MODEL, e.message)
                return
            }
            classifier.use { model ->
                val taxonomy = SoundTaxonomy(model.labels)
                val detector = SnoreDetector(settings.detection)
                val window = FloatArray(model.windowSize)
                val hop = model.windowSize / 2
                val hopMs = hop * 1000L / model.sampleRate
                val newestHop = FloatArray(hop)

                val mic = try {
                    MicSource(model.sampleRate)
                } catch (e: IOException) {
                    Log.e(TAG, "Mic failed", e)
                    result = RecordingState.Failed(RecordingState.Reason.MICROPHONE, e.message)
                    return
                }
                mic.use {
                    // Start delay: hold the microphone (Android won't let us open it later from
                    // the background) but discard audio until it's time to listen.
                    val listenAt = System.currentTimeMillis() + settings.startDelayMinutes * 60_000L
                    if (settings.startDelayMinutes > 0) {
                        RecordingStateHolder.set(RecordingState.Waiting(listenAt))
                        while (isActive && !stopRequested && System.currentTimeMillis() < listenAt) {
                            mic.readFully(newestHop, 0, hop)
                        }
                        if (stopRequested) {
                            result = RecordingState.Idle
                            return
                        }
                    }

                    // Wall-clock times derived from the monotonic clock: immune to clock changes,
                    // and it keeps counting through deep sleep, so freezes show up as gaps.
                    val startWall = System.currentTimeMillis()
                    val startElapsed = SystemClock.elapsedRealtime()
                    fun now() = startWall + (SystemClock.elapsedRealtime() - startElapsed)

                    val sessionId = UUID.randomUUID().toString()
                    val acc = SessionAccumulator.start(sessionId, startWall, settings.recordingMode)
                    val clips = ClipRecorder(
                        store = graph.audio,
                        sessionId = sessionId,
                        sampleRate = model.sampleRate,
                        hopSamples = hop,
                        mode = settings.recordingMode,
                        saveOtherSounds = settings.saveOtherSoundClips,
                        freeBytes = { freeBytes(this@RecordingService) },
                    )
                    var lastCheckpoint = startWall
                    var pausedAt: Long? = null

                    mic.readFully(window, 0, window.size)
                    while (isActive && !stopRequested && now() - startWall < MAX_SESSION_MS) {
                        val t = now()
                        // Auto-resume a pause that has run its course.
                        val autoResumeAt = pausedAt?.let { p -> settings.autoResumeMinutes.takeIf { it > 0 }?.let { p + it * 60_000L } }
                        if (autoResumeAt != null && t >= autoResumeAt) pauseRequested = false
                        if (pauseRequested != acc.isPaused) {
                            if (pauseRequested) {
                                acc.pause(t)
                                clips.closeAll(t)
                                pausedAt = t
                            } else {
                                acc.resume(t)
                                pausedAt = null
                            }
                        }
                        if (!acc.isPaused) {
                            val scores = model.classify(window)
                            val evidence = taxonomy.evidence(scores)
                            val frame = FrameEvidence(
                                timestampMs = t - hopMs,
                                durationMs = hopMs,
                                loudnessDbfs = Loudness.dbfs(Loudness.rms(window, window.size - hop, hop)),
                                snoreProbability = evidence.snoreProbability,
                                otherSound = evidence.otherSound,
                                otherLabel = evidence.otherLabel,
                                otherConfidence = evidence.otherConfidence,
                            )
                            val verdict = detector.assess(frame)
                            acc.add(frame, verdict)
                            System.arraycopy(window, window.size - hop, newestHop, 0, hop)
                            clips.onHop(newestHop, t, frame, verdict, scores)
                            RecordingStateHolder.set(
                                RecordingState.Active(
                                    startEpochMs = startWall,
                                    elapsedMs = t - startWall,
                                    paused = false,
                                    snoringNow = verdict.isSnore,
                                    band = verdict.band,
                                    intensity = verdict.intensity,
                                    scoreSoFar = acc.liveScore(),
                                    lastSound = if (verdict.isSnore) null else frame.otherSound,
                                ),
                            )
                        } else {
                            RecordingStateHolder.set(
                                RecordingState.Active(
                                    startEpochMs = startWall,
                                    elapsedMs = t - startWall,
                                    paused = true,
                                    snoringNow = false,
                                    band = com.moobehgames.snoresafe.core.analysis.SnoreBand.QUIET,
                                    intensity = 0f,
                                    scoreSoFar = acc.liveScore(),
                                    autoResumeAtEpochMs = autoResumeAt,
                                ),
                            )
                        }
                        if (t - lastCheckpoint >= CHECKPOINT_EVERY_MS) {
                            graph.checkpoints.write(acc.state)
                            lastCheckpoint = t
                        }
                        // Slide the window by one hop and read fresh audio (keeps reading while paused,
                        // so the microphone stays open and no stale audio is analysed on resume).
                        System.arraycopy(window, hop, window, 0, window.size - hop)
                        mic.readFully(window, window.size - hop, hop)
                    }
                    val end = now()
                    clips.closeAll(end)
                    val summary = acc.finish(end, EndReason.USER)
                    graph.checkpoints.clear()
                    val moment = if (summary.monitoredMs >= MIN_SAVED_MS) {
                        graph.finisher.finish(summary)
                    } else {
                        // Too short to be a night: drop it and its audio.
                        graph.audio.deleteSession(sessionId)
                        null
                    }
                    result = if (summary.monitoredMs >= MIN_SAVED_MS) RecordingState.Finished(summary.id, moment) else RecordingState.Idle
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Session failed", e)
            // Keep whatever was measured: the checkpoint becomes a recovered night.
            graph.recovery.recoverOrphanedSession()
            result = RecordingState.Failed(RecordingState.Reason.OTHER, e.message)
        } finally {
            RecordingStateHolder.set(result)
            scope.launch(Dispatchers.Main) { shutdown() }
        }
    }

    private fun shutdown() {
        job = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RecordingService"
        const val ACTION_START = "com.moobehgames.snoresafe.action.START"
        const val ACTION_STOP = "com.moobehgames.snoresafe.action.STOP"
        const val ACTION_PAUSE = "com.moobehgames.snoresafe.action.PAUSE"
        const val ACTION_RESUME = "com.moobehgames.snoresafe.action.RESUME"

        private const val CHECKPOINT_EVERY_MS = 60_000L
        private const val MAX_SESSION_MS = 16L * 60 * 60 * 1000

        /** Sessions shorter than this are discarded (accidental taps). */
        private const val MIN_SAVED_MS = 60_000L

        fun intent(context: Context, action: String): Intent =
            Intent(context, RecordingService::class.java).setAction(action)

        fun start(context: Context) = ContextCompat.startForegroundService(context, intent(context, ACTION_START))

        fun send(context: Context, action: String) {
            context.startService(intent(context, action))
        }
    }
}
