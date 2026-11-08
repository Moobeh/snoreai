package com.moobehgames.snoresafe.core.settings

import com.moobehgames.snoresafe.core.analysis.DetectionSettings
import com.moobehgames.snoresafe.core.support.SupportState
import kotlinx.serialization.Serializable

@Serializable
enum class RecordingMode {
    /** Keep only the loudest snore clips and the clearest other-sound clips. Smallest. */
    SNORE_CLIPS,

    /** Keep a clip of every snoring episode and every other sound. */
    ALL_SNORING,

    /** Record the whole night, plus event clips. Largest. */
    FULL_NIGHT;

    companion object {
        val DEFAULT = ALL_SNORING
    }
}

@Serializable
data class StorageSettings(
    /** Audio budget across all nights. */
    val limitMb: Int = 1_000,
    /** All audio is kept for this many most recent nights. */
    val keepAllAudioNights: Int = 7,
    /** For older nights, only this many clips are kept (pinned clips are always kept). */
    val clipsPerOlderNight: Int = 5,
) {
    init {
        require(limitMb > 0 && keepAllAudioNights >= 0 && clipsPerOlderNight >= 0)
    }
}

@Serializable
data class AppSettings(
    val detection: DetectionSettings = DetectionSettings(),
    val recordingMode: RecordingMode = RecordingMode.DEFAULT,
    /** Also save clips of other sounds (alarms, baby, talking…). */
    val saveOtherSoundClips: Boolean = true,
    /** Wait this long after pressing start before listening. */
    val startDelayMinutes: Int = 0,
    /** A pause ends by itself after this many minutes. 0 = never. */
    val autoResumeMinutes: Int = 15,
    val storage: StorageSettings = StorageSettings(),
    val onboardingDone: Boolean = false,
    val resultsTutorialSeen: Boolean = false,
    val support: SupportState = SupportState(),
) {
    init {
        require(startDelayMinutes in 0..120 && autoResumeMinutes in 0..240)
    }

    companion object {
        val START_DELAY_CHOICES = listOf(0, 5, 10, 15, 20, 30, 45, 60)
        val AUTO_RESUME_CHOICES = listOf(0, 5, 10, 15, 30, 60)
        val STORAGE_LIMIT_CHOICES_MB = listOf(250, 500, 1_000, 2_000, 5_000, 10_000)
        val KEEP_NIGHTS_CHOICES = listOf(3, 7, 14, 30, 90)
        val CLIPS_PER_OLDER_NIGHT_CHOICES = listOf(0, 3, 5, 10, 20)
    }
}
