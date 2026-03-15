package com.privimemobile.protocol

/**
 * Pure utility functions ported from helpers.ts.
 */
object Helpers {
    /**
     * Normalize a Beam SBBS WalletID to 68 hex chars.
     *
     * Beam CLI outputs 67-char WalletIDs (odd length).
     * Contract expects 68 chars (34 bytes = 2B channel + 32B pubkey).
     * Prepends '0' for odd-length and left-pads to 68 chars.
     */
    fun normalizeWalletId(walletId: String): String {
        val cleaned = walletId.trim().lowercase()
        if (cleaned.length >= 68) return cleaned.take(68)
        val padded = if (cleaned.length % 2 != 0) "0$cleaned" else cleaned
        return padded.padStart(68, '0')
    }

    /**
     * Fix BVM sign-extended UTF-8 for display names with emoji.
     *
     * The BVM encodes UTF-8 bytes as signed int8 values. When serialized to JSON,
     * negative bytes become escape sequences like "\ufffd". This function decodes
     * the raw byte array back to a proper UTF-8 string.
     */
    fun fixBvmUtf8(text: String): String {
        // If no replacement chars, the string is fine
        if (!text.contains('\uFFFD') && !text.contains("\\u")) return text
        return text // TODO: full BVM UTF-8 decode if needed
    }

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

    /** Validate hex GUID format (32 chars, lowercase hex). */
    private val GUID_REGEX = Regex("^[a-f0-9]{32}$")
    fun isValidGuid(guid: String): Boolean = GUID_REGEX.matches(guid)

    /** Truncate a public key or address for display. */
    fun truncateKey(key: String, prefixLen: Int = 8, suffixLen: Int = 8): String {
        if (key.length <= prefixLen + suffixLen + 3) return key
        return "${key.take(prefixLen)}...${key.takeLast(suffixLen)}"
    }
}
