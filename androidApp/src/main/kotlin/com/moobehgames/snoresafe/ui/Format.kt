package com.moobehgames.snoresafe.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.moobehgames.snoresafe.R
import com.moobehgames.snoresafe.core.analysis.NightSound
import com.moobehgames.snoresafe.core.analysis.SnoreBand
import java.util.Date

fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun formatClock(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60)
}

fun formatTime(context: Context, epochMs: Long): String = DateFormat.getTimeFormat(context).format(Date(epochMs))

fun formatDate(context: Context, epochMs: Long): String = DateFormat.getMediumDateFormat(context).format(Date(epochMs))

@Composable
fun bandLabel(band: SnoreBand): String = stringResource(
    when (band) {
        SnoreBand.QUIET -> R.string.band_quiet
        SnoreBand.LIGHT -> R.string.band_light
        SnoreBand.LOUD -> R.string.band_loud
        SnoreBand.EPIC -> R.string.band_epic
    },
)

@Composable
fun soundLabel(sound: NightSound): String = stringResource(
    when (sound) {
        NightSound.SPEECH -> R.string.sound_speech
        NightSound.COUGH -> R.string.sound_cough
        NightSound.BREATHING -> R.string.sound_breathing
        NightSound.PET -> R.string.sound_pet
        NightSound.FAN_OR_AC -> R.string.sound_fan_or_ac
        NightSound.TRAFFIC -> R.string.sound_traffic
        NightSound.MEDIA -> R.string.sound_media
        NightSound.RAIN -> R.string.sound_rain
        NightSound.BABY -> R.string.sound_baby
    },
)
