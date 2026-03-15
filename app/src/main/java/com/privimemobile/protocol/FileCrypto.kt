package com.privimemobile.protocol

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM file encryption — ports crypto.ts to native Android.
 *
 * Uses javax.crypto (hardware-accelerated on Android) instead of node-forge (pure JS).
 * Same format: ciphertext + 16-byte auth tag appended (compatible with Web Crypto API).
 */
object FileCrypto {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_SIZE = 32   // 256-bit
    private const val IV_SIZE = 12    // 96-bit
    private const val TAG_BITS = 128  // 16 bytes

    /** Generate a random AES-256-GCM key + IV. Returns hex strings. */
    fun generateFileKey(): Pair<String, String> {
        val random = SecureRandom()
        val keyBytes = ByteArray(KEY_SIZE).also { random.nextBytes(it) }
        val ivBytes = ByteArray(IV_SIZE).also { random.nextBytes(it) }
        return bufToHex(keyBytes) to bufToHex(ivBytes)
    }

    /** Encrypt raw bytes with AES-256-GCM. Returns ciphertext + 16-byte auth tag. */
    fun encrypt(plaintext: ByteArray, keyHex: String, ivHex: String): ByteArray {
        val key = SecretKeySpec(hexToBuf(keyHex), "AES")
        val spec = GCMParameterSpec(TAG_BITS, hexToBuf(ivHex))
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        return cipher.doFinal(plaintext)
        // Note: Android's AES-GCM appends the auth tag automatically
    }

    /** Decrypt AES-256-GCM ciphertext (last 16 bytes = auth tag). Throws on bad key/IV. */
    fun decrypt(ciphertext: ByteArray, keyHex: String, ivHex: String): ByteArray {
        val key = SecretKeySpec(hexToBuf(keyHex), "AES")
        val spec = GCMParameterSpec(TAG_BITS, hexToBuf(ivHex))
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(ciphertext)
    }

    /** Convert byte array to hex string. */
    fun bufToHex(buf: ByteArray): String {
        return buf.joinToString("") { "%02x".format(it) }
    }

    /** Convert hex string to byte array. */
    fun hexToBuf(hex: String): ByteArray {
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
}
