package com.moobehgames.snoresafe.core.device

/**
 * Phone makers whose own battery savers can stop apps overnight, grouped by shared settings layouts.
 * Everyone else gets the general (stock Android) guide.
 */
enum class PhoneMaker {
    SAMSUNG,
    XIAOMI,
    HUAWEI_HONOR,
    ONEPLUS_OPPO_REALME,
    VIVO,
    OTHER;

    companion object {
        /** From Android's Build.MANUFACTURER / Build.BRAND, which vary in case and wording. */
        fun from(manufacturer: String?, brand: String? = null): PhoneMaker {
            val s = "${manufacturer.orEmpty()} ${brand.orEmpty()}".lowercase()
            return when {
                "samsung" in s -> SAMSUNG
                listOf("xiaomi", "redmi", "poco").any { it in s } -> XIAOMI
                listOf("huawei", "honor").any { it in s } -> HUAWEI_HONOR
                listOf("oneplus", "oppo", "realme").any { it in s } -> ONEPLUS_OPPO_REALME
                listOf("vivo", "iqoo").any { it in s } -> VIVO
                else -> OTHER
            }
        }
    }
}
