package com.privimemobile.protocol

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pure utility functions — fully ports helpers.ts from RN build.
 */
object Helpers {

    // === Error handling (ports getErrorMessage, extractError) ===

    fun getErrorMessage(e: Any?): String = when (e) {
        is Exception -> e.message ?: "Unknown error"
        is String -> e
        else -> "Unknown error"
    }

    fun extractError(r: Map<String, Any?>?): String {
        if (r == null) return "Unknown error"
        val error = r["error"]
        if (error is Map<*, *>) {
            return (error["message"] as? String) ?: error.toString()
        }
        if (error is String) return error
        return (r["message"] as? String) ?: "Unknown error"
    }

    // === Wallet ID handling (ports normalizeWalletId, shortWalletId) ===

    /**
     * Normalize a Beam SBBS WalletID to 68 hex chars.
     * Prepends '0' for odd-length and left-pads to 68 chars.
     */
    fun normalizeWalletId(walletId: String): String? {
        var cleaned = walletId.trim().replace("\\s".toRegex(), "")
        if (cleaned.lowercase().startsWith("0x")) cleaned = cleaned.substring(2)
        if (!Regex("^[0-9a-fA-F]*$").matches(cleaned)) return null
        if (cleaned.length % 2 != 0) cleaned = "0$cleaned"
        if (cleaned.length < 62 || cleaned.length > 68) return null
        if (cleaned.length < 68) cleaned = cleaned.padStart(68, '0')
        return cleaned
    }

    fun isValidWalletId(walletId: String): Boolean = normalizeWalletId(walletId) != null

    /** Truncate a wallet ID for display (8+8). */
    fun shortWalletId(wid: String): String {
        if (wid.length < 16) return wid
        return "${wid.take(8)}...${wid.takeLast(8)}"
    }

    /** Truncate a public key or address for display. */
    fun truncateKey(key: String, prefixLen: Int = 8, suffixLen: Int = 8): String {
        if (key.length <= prefixLen + suffixLen + 3) return key
        return "${key.take(prefixLen)}...${key.takeLast(suffixLen)}"
    }

    // === Date/time formatting (ports formatTs, formatTime, formatDateSep) ===

    /** Relative time: "now", "5m", "2h", "3d" */
    fun formatTs(ts: Long): String {
        if (ts == 0L) return ""
        val now = System.currentTimeMillis() / 1000
        val diff = now - ts
        return when {
            diff < 60 -> "now"
            diff < 3600 -> "${diff / 60}m"
            diff < 86400 -> "${diff / 3600}h"
            else -> "${diff / 86400}d"
        }
    }

    /** Time only: "HH:mm" */
    fun formatTime(ts: Long): String {
        if (ts == 0L) return ""
        return try {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts * 1000))
        } catch (_: Exception) { "" }
    }

    /** Date separator: "Today", "Yesterday", "MMM d" or "MMM d, yyyy" */
    fun formatDateSep(ts: Long): String {
        if (ts == 0L) return ""
        val now = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { timeInMillis = ts * 1000 }

        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val msgDay = Calendar.getInstance().apply {
            timeInMillis = ts * 1000
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val diffDays = ((today.timeInMillis - msgDay.timeInMillis) / 86400000).toInt()

        return when (diffDays) {
            0 -> "Today"
            1 -> "Yesterday"
            else -> {
                val fmt = if (msgCal.get(Calendar.YEAR) != now.get(Calendar.YEAR))
                    SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                else
                    SimpleDateFormat("MMM d", Locale.getDefault())
                fmt.format(Date(ts * 1000))
            }
        }
    }

    // === File utilities (ports formatFileSize, isImageMime, truncateFilename) ===

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1048576) return String.format("%.1f KB", bytes / 1024.0)
        return String.format("%.1f MB", bytes / 1048576.0)
    }

    fun isImageMime(mime: String?): Boolean {
        return mime != null && mime in listOf("image/jpeg", "image/png", "image/gif", "image/webp")
    }

    fun truncateFilename(name: String?, max: Int = Config.MAX_FILENAME_LEN): String {
        if (name == null || name.length <= max) return name ?: ""
        val dotIdx = name.lastIndexOf('.')
        val ext = if (dotIdx != -1) name.substring(dotIdx) else ""
        return name.substring(0, max - ext.length - 3) + "..." + ext
    }

    // === Beam unit conversion ===

    /** Convert groth (10^-8 BEAM) to display string. */
    fun formatBeam(groth: Long): String {
        if (groth == 0L) return "0"
        val beam = groth / 100_000_000.0
        return String.format("%.8f", beam).trimEnd('0').trimEnd('.')
    }

    /** Convert BEAM display string to groth. */
    fun parseBeamToGroth(beamStr: String): Long {
        return try {
            (beamStr.toDouble() * 100_000_000).toLong()
        } catch (_: Exception) {
            0L
        }
    }

    // === BVM UTF-8 fix ===

    /**
     * Fix BVM sign-extended UTF-8 for display names with emoji.
     *
     * The BVM's DocAddText treats char as signed, so bytes > 127 become
     * sign-extended → mangled as ￿ff[hex] in strings.
     * Reconstructs the original UTF-8 bytes and decodes them.
     *
     * Fully ports fixBvmUtf8() from RN helpers.ts.
     */
    fun fixBvmUtf8(str: String?): String {
        if (str.isNullOrEmpty()) return ""

        // Check for mangled BVM encoding: ￿ (U+FFFD) followed by "ff" + 2 hex chars
        var hasMangled = false
        for (i in str.indices) {
            val code = str[i].code
            if (code >= 0xFFFD && i + 4 < str.length &&
                str.substring(i + 1, i + 3).lowercase() == "ff") {
                hasMangled = true
                break
            }
        }
        if (!hasMangled) return str

        // Reconstruct UTF-8 bytes
        val bytes = mutableListOf<Byte>()
        var i = 0
        val hexRegex = Regex("^ff([0-9a-f]{2})$")
        while (i < str.length) {
            val code = str[i].code
            if (code >= 0xFFFD && i + 4 < str.length) {
                val tail = str.substring(i + 1, i + 5).lowercase()
                val match = hexRegex.find(tail)
                if (match != null) {
                    bytes.add(match.groupValues[1].toInt(16).toByte())
                    i += 5
                    continue
                }
            }
            if (code < 128) {
                bytes.add(code.toByte())
            }
            i++
        }

        return try {
            String(bytes.toByteArray(), Charsets.UTF_8)
        } catch (_: Exception) {
            str
        }
    }

    // === Validation ===

    /** Validate hex GUID format (32 chars, lowercase hex). */
    private val GUID_REGEX = Regex("^[a-f0-9]{32}$")
    fun isValidGuid(guid: String): Boolean = GUID_REGEX.matches(guid)
}
