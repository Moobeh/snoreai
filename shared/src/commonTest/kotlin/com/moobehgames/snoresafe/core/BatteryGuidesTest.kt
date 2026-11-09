package com.moobehgames.snoresafe.core

import com.moobehgames.snoresafe.core.device.PhoneMaker
import kotlin.test.Test
import kotlin.test.assertEquals

class BatteryGuidesTest {
    @Test fun makersAreRecognisedWhateverTheWording() {
        assertEquals(PhoneMaker.SAMSUNG, PhoneMaker.from("samsung"))
        assertEquals(PhoneMaker.XIAOMI, PhoneMaker.from("Xiaomi"))
        assertEquals(PhoneMaker.XIAOMI, PhoneMaker.from("unknown", "POCO"))
        assertEquals(PhoneMaker.HUAWEI_HONOR, PhoneMaker.from("HONOR"))
        assertEquals(PhoneMaker.ONEPLUS_OPPO_REALME, PhoneMaker.from("OnePlus"))
        assertEquals(PhoneMaker.ONEPLUS_OPPO_REALME, PhoneMaker.from("realme"))
        assertEquals(PhoneMaker.VIVO, PhoneMaker.from("vivo"))
        assertEquals(PhoneMaker.OTHER, PhoneMaker.from("Google", "google"))
        assertEquals(PhoneMaker.OTHER, PhoneMaker.from(null))
    }
}
