package com.moobehgames.snoresafe.core.analysis

import kotlinx.serialization.Serializable

/** Non-snore sounds the app recognises and reports separately. */
@Serializable
enum class NightSound {
    SPEECH,
    COUGH,
    BREATHING,
    PET,
    FAN_OR_AC,
    TRAFFIC,
    MEDIA,
    RAIN,
    BABY,
}
