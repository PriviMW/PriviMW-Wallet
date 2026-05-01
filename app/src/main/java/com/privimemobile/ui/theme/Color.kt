package com.privimemobile.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.privimemobile.R

/** Theme definitions — all screens reference C.xxx which updates reactively on theme change. */
data class ThemeColors(
    val bg: Color,
    val card: Color,
    val cardAlt: Color,
    val border: Color,
    val accent: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val textDark: Color,
    val error: Color,
    val warning: Color,
    val incoming: Color,
    val outgoing: Color,
    val online: Color,
    val offline: Color,
    val dangerBg: Color,
    val inputBar: Color,
    val bubbleMine: Color,
    val bubbleOther: Color,
    val bubbleText: Color,
    val waveformActive: Color,
    val waveformInactive: Color,
    val isLight: Boolean = false,
)

private val DARK = ThemeColors(
    bg = Color(0xFF0A0E27),
    card = Color(0xFF1A1F3A),
    cardAlt = Color(0xFF162044),
    border = Color(0xFF2A2F4A),
    accent = Color(0xFF25D4D0),
    text = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF8A8FA8),
    textMuted = Color(0xFF555555),
    textDark = Color(0xFF0A0E27),
    error = Color(0xFFFF6B6B),
    warning = Color(0xFFF0A030),
    incoming = Color(0xFF00D8B4),
    outgoing = Color(0xFFFF6B6B),
    online = Color(0xFF25D4D0),
    offline = Color(0xFFFF4444),
    dangerBg = Color(0xFF2A1525),
    inputBar = Color(0xFF0D1230),
    bubbleMine = Color(0xFF1B3A4B),
    bubbleOther = Color(0xFF1A1F3A),
    bubbleText = Color(0xFFFFFFFF),
    waveformActive = Color(0xFF25D4D0),
    waveformInactive = Color(0xFF25D4D0).copy(alpha = 0.3f),
)

private val LIGHT = ThemeColors(
    bg = Color(0xFFF5F5F5),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFF0F0F5),
    border = Color(0xFFE0E0E8),
    accent = Color(0xFF1BAFA8),
    text = Color(0xFF1A1A2E),
    textSecondary = Color(0xFF6B7280),
    textMuted = Color(0xFFAAAAAA),
    textDark = Color(0xFF1A1A2E),
    error = Color(0xFFE53E3E),
    warning = Color(0xFFD97706),
    incoming = Color(0xFF059669),
    outgoing = Color(0xFFE53E3E),
    online = Color(0xFF1BAFA8),
    offline = Color(0xFFE53E3E),
    dangerBg = Color(0xFFFEE2E2),
    inputBar = Color(0xFFFFFFFF),
    bubbleMine = Color(0xFFDCF8C6),
    bubbleOther = Color(0xFFFFFFFF),
    bubbleText = Color(0xFF1A1A2E),
    waveformActive = Color(0xFF1BAFA8),
    waveformInactive = Color(0xFF1BAFA8).copy(alpha = 0.3f),
    isLight = true,
)

private val AMOLED = ThemeColors(
    bg = Color(0xFF000000),
    card = Color(0xFF0D0D0D),
    cardAlt = Color(0xFF0A0A0A),
    border = Color(0xFF1A1A1A),
    accent = Color(0xFF25D4D0),
    text = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF888888),
    textMuted = Color(0xFF444444),
    textDark = Color(0xFF000000),
    error = Color(0xFFFF6B6B),
    warning = Color(0xFFF0A030),
    incoming = Color(0xFF00D8B4),
    outgoing = Color(0xFFFF6B6B),
    online = Color(0xFF25D4D0),
    offline = Color(0xFFFF4444),
    dangerBg = Color(0xFF1A0A0A),
    inputBar = Color(0xFF050505),
    bubbleMine = Color(0xFF1A2A30),
    bubbleOther = Color(0xFF0D0D0D),
    bubbleText = Color(0xFFFFFFFF),
    waveformActive = Color(0xFF25D4D0),
    waveformInactive = Color(0xFF25D4D0).copy(alpha = 0.3f),
)

private val MIDNIGHT_BLUE = ThemeColors(
    bg = Color(0xFF0B1426),
    card = Color(0xFF132040),
    cardAlt = Color(0xFF0F1A35),
    border = Color(0xFF1E3055),
    accent = Color(0xFF4DA6FF),
    text = Color(0xFFE8ECF4),
    textSecondary = Color(0xFF7B8FB0),
    textMuted = Color(0xFF4A5568),
    textDark = Color(0xFF0B1426),
    error = Color(0xFFFF6B6B),
    warning = Color(0xFFFFB347),
    incoming = Color(0xFF4DA6FF),
    outgoing = Color(0xFFFF6B6B),
    online = Color(0xFF4DA6FF),
    offline = Color(0xFFFF4444),
    dangerBg = Color(0xFF1A1020),
    inputBar = Color(0xFF091220),
    bubbleMine = Color(0xFF1A3050),
    bubbleOther = Color(0xFF132040),
    bubbleText = Color(0xFFE8ECF4),
    waveformActive = Color(0xFF4DA6FF),
    waveformInactive = Color(0xFF4DA6FF).copy(alpha = 0.3f),
)

private val EMERALD = ThemeColors(
    bg = Color(0xFF0A1A14),
    card = Color(0xFF122A20),
    cardAlt = Color(0xFF0E2218),
    border = Color(0xFF1E3D2E),
    accent = Color(0xFF34D399),
    text = Color(0xFFE8F5EE),
    textSecondary = Color(0xFF7BAF96),
    textMuted = Color(0xFF4A6B5A),
    textDark = Color(0xFF0A1A14),
    error = Color(0xFFFF6B6B),
    warning = Color(0xFFF0A030),
    incoming = Color(0xFF34D399),
    outgoing = Color(0xFFFF6B6B),
    online = Color(0xFF34D399),
    offline = Color(0xFFFF4444),
    dangerBg = Color(0xFF1A1520),
    inputBar = Color(0xFF081510),
    bubbleMine = Color(0xFF1A3528),
    bubbleOther = Color(0xFF122A20),
    bubbleText = Color(0xFFE8F5EE),
    waveformActive = Color(0xFF34D399),
    waveformInactive = Color(0xFF34D399).copy(alpha = 0.3f),
)

private val PURPLE_HAZE = ThemeColors(
    bg = Color(0xFF120B20),
    card = Color(0xFF1E1435),
    cardAlt = Color(0xFF18102C),
    border = Color(0xFF2E2048),
    accent = Color(0xFFA78BFA),
    text = Color(0xFFF0ECF8),
    textSecondary = Color(0xFF9B8FC0),
    textMuted = Color(0xFF5A4E70),
    textDark = Color(0xFF120B20),
    error = Color(0xFFFF6B6B),
    warning = Color(0xFFF0A030),
    incoming = Color(0xFFA78BFA),
    outgoing = Color(0xFFFF6B6B),
    online = Color(0xFFA78BFA),
    offline = Color(0xFFFF4444),
    dangerBg = Color(0xFF1A0A1A),
    inputBar = Color(0xFF0E0818),
    bubbleMine = Color(0xFF2A1845),
    bubbleOther = Color(0xFF1E1435),
    bubbleText = Color(0xFFF0ECF8),
    waveformActive = Color(0xFFA78BFA),
    waveformInactive = Color(0xFFA78BFA).copy(alpha = 0.3f),
)

private val SOLAR = ThemeColors(
    bg = Color(0xFF1A1008),
    card = Color(0xFF2A1E10),
    cardAlt = Color(0xFF22180C),
    border = Color(0xFF3D2E18),
    accent = Color(0xFFF59E0B),
    text = Color(0xFFF8F0E0),
    textSecondary = Color(0xFFB09870),
    textMuted = Color(0xFF6B5A40),
    textDark = Color(0xFF1A1008),
    error = Color(0xFFFF6B6B),
    warning = Color(0xFFF59E0B),
    incoming = Color(0xFFF59E0B),
    outgoing = Color(0xFFFF6B6B),
    online = Color(0xFFF59E0B),
    offline = Color(0xFFFF4444),
    dangerBg = Color(0xFF1A1010),
    inputBar = Color(0xFF150D05),
    bubbleMine = Color(0xFF352818),
    bubbleOther = Color(0xFF2A1E10),
    bubbleText = Color(0xFFF8F0E0),
    waveformActive = Color(0xFFF59E0B),
    waveformInactive = Color(0xFFF59E0B).copy(alpha = 0.3f),
)

private val OCEAN = ThemeColors(
    bg = Color(0xFF0A1520),
    card = Color(0xFF122030),
    cardAlt = Color(0xFF0E1A28),
    border = Color(0xFF1E3045),
    accent = Color(0xFF22D3EE),
    text = Color(0xFFE8F0F8),
    textSecondary = Color(0xFF7BA0B8),
    textMuted = Color(0xFF4A6878),
    textDark = Color(0xFF0A1520),
    error = Color(0xFFFF6B6B),
    warning = Color(0xFFF0A030),
    incoming = Color(0xFF22D3EE),
    outgoing = Color(0xFFFF6B6B),
    online = Color(0xFF22D3EE),
    offline = Color(0xFFFF4444),
    dangerBg = Color(0xFF1A1020),
    inputBar = Color(0xFF081018),
    bubbleMine = Color(0xFF152838),
    bubbleOther = Color(0xFF122030),
    bubbleText = Color(0xFFE8F0F8),
    waveformActive = Color(0xFF22D3EE),
    waveformInactive = Color(0xFF22D3EE).copy(alpha = 0.3f),
)

val ALL_THEMES = mapOf(
    "dark" to DARK,
    "light" to LIGHT,
    "amoled" to AMOLED,
    "midnight" to MIDNIGHT_BLUE,
    "emerald" to EMERALD,
    "purple" to PURPLE_HAZE,
    "solar" to SOLAR,
    "ocean" to OCEAN,
)

val THEME_NAMES = mapOf(
    "dark" to "Dark",
    "light" to "Light",
    "amoled" to "AMOLED Black",
    "midnight" to "Midnight Blue",
    "emerald" to "Emerald",
    "purple" to "Purple Haze",
    "solar" to "Solar",
    "ocean" to "Ocean",
)

/** Global reactive color object — all screens reference this. */
object C {
    private var current by mutableStateOf(DARK)

    val bg get() = current.bg
    val card get() = current.card
    val cardAlt get() = current.cardAlt
    val border get() = current.border
    val accent get() = current.accent
    val text get() = current.text
    val textSecondary get() = current.textSecondary
    val textMuted get() = current.textMuted
    val textDark get() = current.textDark
    val error get() = current.error
    val warning get() = current.warning
    val incoming get() = current.incoming
    val outgoing get() = current.outgoing
    val online get() = current.online
    val offline get() = current.offline
    val dangerBg get() = current.dangerBg
    val inputBar get() = current.inputBar
    val bubbleMine get() = current.bubbleMine
    val bubbleOther get() = current.bubbleOther
    val bubbleText get() = current.bubbleText
    val waveformActive get() = current.waveformActive
    val waveformInactive get() = current.waveformInactive
    val isLight get() = current.isLight

    /** Apply a theme by key. Saves preference. */
    fun applyTheme(key: String, context: Context? = null) {
        current = ALL_THEMES[key] ?: DARK
        context?.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            ?.edit()?.putString("theme", key)?.apply()
    }

    /** Load saved theme from SharedPreferences. */
    fun loadSavedTheme(context: Context) {
        val key = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("theme", "dark") ?: "dark"
        current = ALL_THEMES[key] ?: DARK
    }

    /** Get current theme key. */
    fun currentThemeKey(context: Context): String {
        return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("theme", "dark") ?: "dark"
    }

    /** Get localized theme display name. */
    fun themeDisplayName(key: String, context: Context): String = when (key) {
        "dark" -> context.getString(R.string.settings_theme_dark)
        "light" -> context.getString(R.string.settings_theme_light)
        "amoled" -> context.getString(R.string.settings_theme_amoled)
        "midnight" -> context.getString(R.string.settings_theme_midnight)
        "emerald" -> context.getString(R.string.settings_theme_emerald)
        "purple" -> context.getString(R.string.settings_theme_purple)
        "solar" -> context.getString(R.string.settings_theme_solar)
        "ocean" -> context.getString(R.string.settings_theme_ocean)
        else -> key
    }
}