package com.drakosanctis.auriga.locale

import java.util.Locale

/**
 * Auriga Locale Support
 *
 * The 25-language selection, researched against:
 * - Global distribution of visually impaired/blind populations (WHO data:
 *   ~90% of blind individuals live in low/middle-income countries; South
 *   Asia carries the single largest regional burden; India alone holds
 *   roughly a fifth of the world's blind population).
 * - Competitor gap analysis (Seeing AI / mainstream accessibility apps
 *   cover Western Europe + a few East Asian languages well, but have
 *   near-zero coverage of Sub-Saharan African languages and thin coverage
 *   of South/Southeast Asian languages despite that being where most
 *   visually impaired people actually live).
 * - On-device TTS feasibility (Android's built-in TextToSpeech engine has
 *   reliable, good-quality voices for most Tier 1 languages, but
 *   inconsistent/device-dependent quality for several Tier 2 languages).
 *
 * TIER_1: reliable on-device TTS across most modern Android devices.
 * TIER_2: on-device TTS quality/availability varies by device and OEM —
 *         the app should detect this at runtime (see [isOnDeviceTtsReliable])
 *         and offer a cloud-assisted fallback rather than silently degrading.
 *         These are also the languages where Auriga has the least
 *         mainstream competition — Hausa, Yoruba, Amharic, and Igbo in
 *         particular have essentially no coverage from Seeing AI, Be My
 *         Eyes' offline mode, or Google/Apple's own assistants.
 */

enum class LocaleTier { TIER_1_RELIABLE, TIER_2_VARIABLE }

data class SupportedLocale(
    val languageTag: String, // BCP-47, e.g. "sw-KE"
    val displayName: String,
    val tier: LocaleTier,
    val rationale: String
)

object LocaleSupport {

    val SUPPORTED: List<SupportedLocale> = listOf(
        // --- Tier 1: reliable on-device TTS, highest VI-population reach ---
        SupportedLocale("en-US", "English", LocaleTier.TIER_1_RELIABLE,
            "Global default, highest overall reach."),
        SupportedLocale("hi-IN", "Hindi", LocaleTier.TIER_1_RELIABLE,
            "India holds roughly 20% of the world's blind population."),
        SupportedLocale("zh-CN", "Mandarin Chinese", LocaleTier.TIER_1_RELIABLE,
            "Largest national population, significant East Asia VI burden."),
        SupportedLocale("bn-BD", "Bengali", LocaleTier.TIER_1_RELIABLE,
            "Bangladesh + West Bengal — major South Asia VI population, thin competitor coverage."),
        SupportedLocale("ur-PK", "Urdu", LocaleTier.TIER_1_RELIABLE,
            "Pakistan — high VI burden, underserved by mainstream accessibility apps."),
        SupportedLocale("id-ID", "Indonesian", LocaleTier.TIER_1_RELIABLE,
            "Southeast Asia's largest population, thin competitor coverage."),
        SupportedLocale("ar-SA", "Arabic", LocaleTier.TIER_1_RELIABLE,
            "North Africa / Middle East reach, weak competitor coverage."),
        SupportedLocale("es-ES", "Spanish", LocaleTier.TIER_1_RELIABLE,
            "Global reach across Latin America and Spain."),
        SupportedLocale("pt-BR", "Portuguese", LocaleTier.TIER_1_RELIABLE,
            "Bridges Brazil and Lusophone Africa (Mozambique, Angola)."),
        SupportedLocale("fr-FR", "French", LocaleTier.TIER_1_RELIABLE,
            "Lingua franca across West and Central Africa (DRC, francophone West Africa)."),
        SupportedLocale("sw-KE", "Swahili", LocaleTier.TIER_1_RELIABLE,
            "East Africa lingua franca (Kenya, Tanzania, Uganda) — primary target market, " +
                "essentially zero mainstream competitor coverage."),
        SupportedLocale("vi-VN", "Vietnamese", LocaleTier.TIER_1_RELIABLE,
            "Southeast Asia, underserved by mainstream accessibility apps."),
        SupportedLocale("fil-PH", "Filipino", LocaleTier.TIER_1_RELIABLE,
            "Philippines has notably high measured VI/blindness rates."),
        SupportedLocale("tr-TR", "Turkish", LocaleTier.TIER_1_RELIABLE,
            "High VI rates; table-stakes coverage already offered by competitors."),
        SupportedLocale("ja-JP", "Japanese", LocaleTier.TIER_1_RELIABLE,
            "Table-stakes coverage, standard among competitors."),
        SupportedLocale("ko-KR", "Korean", LocaleTier.TIER_1_RELIABLE,
            "Table-stakes coverage, standard among competitors."),
        SupportedLocale("ru-RU", "Russian", LocaleTier.TIER_1_RELIABLE,
            "Eastern Europe / Central Asia reach."),
        SupportedLocale("de-DE", "German", LocaleTier.TIER_1_RELIABLE,
            "Table-stakes EU coverage."),
        SupportedLocale("it-IT", "Italian", LocaleTier.TIER_1_RELIABLE,
            "Table-stakes EU coverage."),
        SupportedLocale("pl-PL", "Polish", LocaleTier.TIER_1_RELIABLE,
            "Table-stakes Eastern Europe coverage."),

        // --- Tier 2: on-device TTS quality/availability varies by device ---
        SupportedLocale("ha-NG", "Hausa", LocaleTier.TIER_2_VARIABLE,
            "70M+ speakers across West Africa; zero mainstream competitor support."),
        SupportedLocale("yo-NG", "Yoruba", LocaleTier.TIER_2_VARIABLE,
            "Nigeria; zero mainstream competitor support."),
        SupportedLocale("am-ET", "Amharic", LocaleTier.TIER_2_VARIABLE,
            "Ethiopia, ~118M population with high VI burden; zero mainstream support."),
        SupportedLocale("ig-NG", "Igbo", LocaleTier.TIER_2_VARIABLE,
            "Nigeria; zero mainstream competitor support."),
        SupportedLocale("fa-IR", "Persian/Farsi", LocaleTier.TIER_2_VARIABLE,
            "Iran and broader Persian-speaking reach; thin competitor coverage.")
    )

    fun findByTag(tag: String): SupportedLocale? =
        SUPPORTED.firstOrNull { it.languageTag.equals(tag, ignoreCase = true) }

    fun defaultForDevice(deviceLocale: Locale = Locale.getDefault()): SupportedLocale {
        val deviceLanguage = deviceLocale.language
        return SUPPORTED.firstOrNull {
            Locale.forLanguageTag(it.languageTag).language == deviceLanguage
        } ?: SUPPORTED.first { it.languageTag == "en-US" }
    }

    /**
     * Tier 2 languages should not be assumed to have good on-device TTS.
     * Callers (AudioEngine) should check Android's TextToSpeech.isLanguageAvailable()
     * at runtime for the actual device, and offer a cloud-assisted voice or a
     * graceful "voice quality may vary" notice if it returns
     * LANG_MISSING_DATA or LANG_NOT_SUPPORTED, rather than assuming success.
     */
    fun requiresRuntimeTtsCheck(locale: SupportedLocale): Boolean =
        locale.tier == LocaleTier.TIER_2_VARIABLE
}
