package com.moobehgames.snoresafe.core.analysis

import kotlinx.serialization.Serializable

/**
 * Non-snore sounds the app recognises and reports separately.
 * [notable] sounds (alarms, sirens, breaking glass, a crying baby…) are highlighted in results.
 */
@Serializable
enum class NightSound(val notable: Boolean = false) {
    SPEECH,
    SHOUTING,
    LAUGHTER,
    CRYING,
    BABY(notable = true),
    COUGH,
    BREATHING,
    BODY,
    PET,
    BIRDS,
    INSECTS,
    FAN_OR_AC,
    APPLIANCE,
    TRAFFIC,
    EMERGENCY_SIREN(notable = true),
    CAR_ALARM(notable = true),
    SMOKE_ALARM(notable = true),
    ALARM(notable = true),
    PHONE,
    DOORBELL(notable = true),
    DOOR,
    FOOTSTEPS,
    WATER,
    GLASS(notable = true),
    BANG(notable = true),
    AIRCRAFT,
    TRAIN,
    MEDIA,
    RAIN,
    THUNDER,
    WIND,
}
