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
    if (ms in 1 until 60_000) return "${(ms + 999) / 1000}s"
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

fun soundLabelRes(sound: NightSound): Int = when (sound) {
    NightSound.SPEECH -> R.string.sound_speech
    NightSound.SHOUTING -> R.string.sound_shouting
    NightSound.LAUGHTER -> R.string.sound_laughter
    NightSound.CRYING -> R.string.sound_crying
    NightSound.BABY -> R.string.sound_baby
    NightSound.COUGH -> R.string.sound_cough
    NightSound.BREATHING -> R.string.sound_breathing
    NightSound.BODY -> R.string.sound_body
    NightSound.PET -> R.string.sound_pet
    NightSound.BIRDS -> R.string.sound_birds
    NightSound.INSECTS -> R.string.sound_insects
    NightSound.FAN_OR_AC -> R.string.sound_fan_or_ac
    NightSound.APPLIANCE -> R.string.sound_appliance
    NightSound.TRAFFIC -> R.string.sound_traffic
    NightSound.EMERGENCY_SIREN -> R.string.sound_emergency_siren
    NightSound.CAR_ALARM -> R.string.sound_car_alarm
    NightSound.SMOKE_ALARM -> R.string.sound_smoke_alarm
    NightSound.ALARM -> R.string.sound_alarm
    NightSound.PHONE -> R.string.sound_phone
    NightSound.DOORBELL -> R.string.sound_doorbell
    NightSound.DOOR -> R.string.sound_door
    NightSound.FOOTSTEPS -> R.string.sound_footsteps
    NightSound.WATER -> R.string.sound_water
    NightSound.GLASS -> R.string.sound_glass
    NightSound.BANG -> R.string.sound_bang
    NightSound.AIRCRAFT -> R.string.sound_aircraft
    NightSound.TRAIN -> R.string.sound_train
    NightSound.MEDIA -> R.string.sound_media
    NightSound.RAIN -> R.string.sound_rain
    NightSound.THUNDER -> R.string.sound_thunder
    NightSound.WIND -> R.string.sound_wind
}

@Composable
fun soundLabel(sound: NightSound): String = stringResource(soundLabelRes(sound))

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.0f MB".format(bytes / (1024.0 * 1024))
    else -> "%.0f KB".format(bytes / 1024.0)
}

fun formatSeconds(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
