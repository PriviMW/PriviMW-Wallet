package com.privimemobile.chat.transport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.ChatDatabase
import com.privimemobile.protocol.Config
import com.privimemobile.protocol.Helpers
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * FileTransport — handles file encryption, image compression, inline SBBS delivery.
 *
 * Encryption: AES-256-GCM
 * - Key: 32 random bytes (256-bit) → 64 hex chars
 * - IV: 12 random bytes (96-bit) → 24 hex chars
 * - Auth tag: 128-bit (16 bytes), appended automatically by GCM
 *
 * Delivery: Inline only — base64 in SBBS message (max 750KB after encryption).
 * Beam's IPFS node is used for DApp store only, not chat file sharing.
 */
object IpfsTransport {
    private const val TAG = "IpfsTransport"
    private const val GCM_TAG_BITS = 128
    var uploadInProgress = false
        private set

    // File cache directory
    internal var cacheDir: File? = null
    internal var filesDir: File? = null
    private const val MAX_CACHED_FILES = 100

    fun init(context: Context) {
        cacheDir = File(context.cacheDir, "privime-files").also { it.mkdirs() }
        filesDir = context.filesDir
    }

    // === Encryption ===

    /** Generate random AES-256 key + IV. Returns (keyHex, ivHex). */
    fun generateKey(): Pair<String, String> {
        val key = ByteArray(32)
        val iv = ByteArray(12)
        SecureRandom().nextBytes(key)
        SecureRandom().nextBytes(iv)
        return key.toHex() to iv.toHex()
    }

    /** Encrypt plaintext with AES-256-GCM. Returns ciphertext (includes 16-byte auth tag). */
    fun encrypt(plaintext: ByteArray, keyHex: String, ivHex: String): ByteArray {
        val key = SecretKeySpec(keyHex.hexToBytes(), "AES")
        val iv = GCMParameterSpec(GCM_TAG_BITS, ivHex.hexToBytes())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        return cipher.doFinal(plaintext)
    }

    /** Decrypt ciphertext with AES-256-GCM. Returns plaintext. */
    fun decrypt(ciphertext: ByteArray, keyHex: String, ivHex: String): ByteArray {
        val key = SecretKeySpec(keyHex.hexToBytes(), "AES")
        val iv = GCMParameterSpec(GCM_TAG_BITS, ivHex.hexToBytes())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, iv)
        return cipher.doFinal(ciphertext)
    }

    // === Image Compression ===

    /** Compress image if JPEG/PNG and > COMPRESS_MIN_SIZE. Returns compressed bytes or original. */
    fun compressImage(data: ByteArray, mimeType: String): ByteArray {
        if (data.size < Config.COMPRESS_MIN_SIZE) return data
        if (mimeType == "image/gif") return data // preserve animation

        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size) ?: return data

        // Resize if larger than max dimension
        val maxDim = Config.IMAGE_MAX_DIM
        val scale = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            maxDim.toFloat() / maxOf(bitmap.width, bitmap.height)
        } else 1f

        val resized = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap

        val out = ByteArrayOutputStream()
        val format = if (mimeType == "image/png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        resized.compress(format, Config.IMAGE_QUALITY, out)

        val compressed = out.toByteArray()
        // Only use compressed if smaller
        return if (compressed.size < data.size) compressed else data
    }

    // === Send File ===

    /**
     * Prepare a file for sending: compress → encrypt → base64 for inline SBBS delivery.
     *
     * @return Map with file metadata (cid, key, iv, name, size, mime, data).
     * @throws Exception if file too large after compression (>750KB).
     */
    suspend fun prepareFile(
        context: Context,
        fileUri: Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String,
    ): Map<String, Any?> {
        if (uploadInProgress) {
            throw Exception("Upload already in progress")
        }

        if (fileSize > Config.MAX_FILE_SIZE) {
            val sizeMB = String.format("%.1f", fileSize / (1024.0 * 1024.0))
            throw Exception("File too large (${sizeMB}MB). Max is ${Config.MAX_FILE_SIZE / (1024 * 1024)}MB.")
        }

        uploadInProgress = true
        try {
            // Read file bytes (support both content:// and file:// URIs)
            var data = if (fileUri.scheme == "file") {
                java.io.File(fileUri.path!!).readBytes()
            } else {
                context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
            } ?: throw Exception("Could not read file")

            // Compress images
            if (Helpers.isImageMime(mimeType)) {
                data = compressImage(data, mimeType)
            }

            // Encrypt
            val (keyHex, ivHex) = generateKey()
            val ciphertext = encrypt(data, keyHex, ivHex)

            val sanitizedName = sanitizeFilename(fileName)

            // Check inline size limit (BBS max = 1MB, base64 overhead ~33%)
            if (ciphertext.size > Config.MAX_INLINE_SIZE) {
                val sizeMB = String.format("%.1f", data.size / (1024.0 * 1024.0))
                val limitKB = Config.MAX_INLINE_SIZE / 1024
                Log.w(TAG, "File too large for inline: ${data.size} bytes ($sizeMB MB), limit=${limitKB}KB")
                throw Exception("File too large (${sizeMB}MB). Max size is ${limitKB}KB after compression.")
            }

            val inlineData = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
            val cid = "inline-${System.currentTimeMillis().toString(36)}${(0..999).random().toString(36)}"
            return mapOf(
                "cid" to cid,
                "key" to keyHex,
                "iv" to ivHex,
                "name" to sanitizedName,
                "size" to data.size,
                "mime" to mimeType,
                "data" to inlineData,
            )
        } finally {
            uploadInProgress = false
        }
    }

    // === Download / Decrypt File ===

    /**
     * Decrypt an inline file attachment. Returns local file path.
     */
    suspend fun downloadFile(
        attachmentId: Long,
        ipfsCid: String?,
        keyHex: String,
        ivHex: String,
        inlineData: String?,
    ): String {
        val db = ChatService.db ?: throw IllegalStateException("DB not ready")

        db.attachmentDao().updateStatus(attachmentId, "downloading")
        Log.d(TAG, "downloadFile: id=$attachmentId, cid=$ipfsCid, keyLen=${keyHex.length}, hasInline=${inlineData != null}")

        try {
            val ciphertext: ByteArray = if (inlineData != null) {
                Log.d(TAG, "Decoding inline data: ${inlineData.length} chars")
                Base64.decode(inlineData, Base64.DEFAULT)
            } else {
                throw Exception("No inline data — IPFS P2P not supported for chat files")
            }

            Log.d(TAG, "Got ${ciphertext.size} bytes ciphertext, decrypting...")
            db.attachmentDao().updateStatus(attachmentId, "decrypting")

            val plaintext = decrypt(ciphertext, keyHex, ivHex)
            Log.d(TAG, "Decrypted to ${plaintext.size} bytes")

            // Save to cache
            val cacheFile = getCacheFile(ipfsCid ?: "inline-${attachmentId}")
            cacheFile.writeBytes(plaintext)

            // Evict old files if cache is full
            evictOldFiles(db)

            db.attachmentDao().setDownloaded(attachmentId, cacheFile.absolutePath)
            return cacheFile.absolutePath
        } catch (e: Exception) {
            db.attachmentDao().updateStatus(attachmentId, "error")
            throw e
        }
    }

    /** Get local file path from cache (if already downloaded). */
    fun getLocalFilePath(cid: String): String? {
        val file = getCacheFile(cid)
        return if (file.exists()) file.absolutePath else null
    }

    // === File Cache ===

    private fun getCacheFile(cid: String): File {
        val safeName = cid.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(cacheDir ?: File("/tmp"), safeName)
    }

    private suspend fun evictOldFiles(db: ChatDatabase) {
        val count = db.attachmentDao().countCached()
        if (count <= MAX_CACHED_FILES) return
        val oldest = db.attachmentDao().getOldestCached() ?: return
        oldest.localPath?.let { File(it).delete() }
        db.attachmentDao().updateStatus(oldest.id, "idle")
    }

    fun clearCache() {
        cacheDir?.listFiles()?.forEach { it.delete() }
    }

    // === Helpers ===

    private fun sanitizeFilename(name: String): String {
        val clean = name.replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
        return if (clean.length > 80) clean.take(80) else clean.ifEmpty { "file" }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        val len = length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(this[i], 16) shl 4) + Character.digit(this[i + 1], 16)).toByte()
        }
        return data
    }
}
