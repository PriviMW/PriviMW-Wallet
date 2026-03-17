package com.privimemobile.chat.transport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.ChatDatabase
import com.privimemobile.chat.db.entities.AttachmentEntity
import com.privimemobile.chat.db.entities.MessageEntity
import com.privimemobile.protocol.Config
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.WalletApi
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * IpfsTransport — handles file encryption, IPFS upload/download, image compression.
 *
 * Encryption: AES-256-GCM
 * - Key: 32 random bytes (256-bit) → 64 hex chars
 * - IV: 12 random bytes (96-bit) → 24 hex chars
 * - Auth tag: 128-bit (16 bytes), appended automatically by GCM
 *
 * Delivery:
 * - Inline (< 200KB ciphertext): base64 in SBBS message
 * - IPFS (>= 200KB): upload encrypted data, send CID in SBBS message
 */
object IpfsTransport {
    private const val TAG = "IpfsTransport"
    private const val GCM_TAG_BITS = 128
    var uploadInProgress = false
        private set

    // File cache directory
    private var cacheDir: File? = null
    private const val MAX_CACHED_FILES = 100

    fun init(context: Context) {
        cacheDir = File(context.cacheDir, "privime-files").also { it.mkdirs() }
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
     * Send a file: compress → encrypt → inline or IPFS → return file metadata for SBBS payload.
     *
     * @return Map with file metadata (cid, key, iv, name, size, mime, data?) or null on error.
     */
    suspend fun prepareFile(
        context: Context,
        fileUri: Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String,
    ): Map<String, Any?>? {
        if (uploadInProgress) {
            Log.w(TAG, "Upload already in progress")
            return null
        }

        // Validate
        if (fileSize > Config.MAX_FILE_SIZE) {
            Log.w(TAG, "File too large: $fileSize > ${Config.MAX_FILE_SIZE}")
            return null
        }
        if (mimeType !in Config.ALLOWED_MIME_TYPES) {
            Log.w(TAG, "MIME type not allowed: $mimeType")
            return null
        }

        uploadInProgress = true
        try {
            // Read file bytes
            var data = context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                ?: return null

            // Compress images
            if (Helpers.isImageMime(mimeType)) {
                data = compressImage(data, mimeType)
            }

            // Encrypt
            val (keyHex, ivHex) = generateKey()
            val ciphertext = encrypt(data, keyHex, ivHex)

            val sanitizedName = sanitizeFilename(fileName)

            // Decide inline vs IPFS
            if (ciphertext.size <= Config.MAX_INLINE_SIZE) {
                // Inline delivery — embed base64 in SBBS message
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
            } else {
                // IPFS upload
                val cid = ipfsAdd(ciphertext) ?: return null
                return mapOf(
                    "cid" to cid,
                    "key" to keyHex,
                    "iv" to ivHex,
                    "name" to sanitizedName,
                    "size" to data.size,
                    "mime" to mimeType,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "prepareFile failed: ${e.message}")
            return null
        } finally {
            uploadInProgress = false
        }
    }

    // === Download File ===

    /**
     * Download and decrypt a file. Returns local file path.
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

        try {
            val ciphertext: ByteArray = if (inlineData != null) {
                // Inline file — decode base64
                Base64.decode(inlineData, Base64.DEFAULT)
            } else if (ipfsCid != null) {
                // IPFS download
                ipfsGet(ipfsCid) ?: throw Exception("IPFS download failed")
            } else {
                throw Exception("No data source (inline or IPFS CID)")
            }

            db.attachmentDao().updateStatus(attachmentId, "decrypting")

            // Decrypt
            val plaintext = decrypt(ciphertext, keyHex, ivHex)

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

    /** Auto-download small inline files + images < 2MB. */
    suspend fun autoDownloadImages(db: ChatDatabase, convId: Long) {
        val attachments = db.attachmentDao().run {
            // Get undone attachments for this conversation — would need a custom query
            // For now, skip — will implement with proper DAO query
        }
        // TODO: implement with proper DAO query for un-downloaded attachments in conversation
    }

    /** Get local file path from cache (if already downloaded). */
    fun getLocalFilePath(cid: String): String? {
        val file = getCacheFile(cid)
        return if (file.exists()) file.absolutePath else null
    }

    // === IPFS Operations ===

    /** Upload encrypted bytes to IPFS. Returns CID or null. */
    private suspend fun ipfsAdd(data: ByteArray): String? {
        return withTimeoutOrNull(Config.IPFS_ADD_TIMEOUT.toLong()) {
            suspendCancellableCoroutine { cont ->
                // Beam API expects data as integer array [0, 123, 255, ...], NOT base64
                val dataArr = data.map { it.toInt() and 0xFF }
                WalletApi.call("ipfs_add", mapOf(
                    "data" to dataArr,
                    "pin" to true,
                    "timeout" to Config.IPFS_ADD_TIMEOUT,
                )) { result ->
                    val cid = result["hash"] as? String
                    if (cid != null && cont.isActive) {
                        Log.d(TAG, "IPFS add success: $cid")
                        cont.resume(cid) {}
                    } else if (cont.isActive) {
                        Log.e(TAG, "IPFS add failed: ${result["error"]}")
                        cont.resume(null) {}
                    }
                }
            }
        }
    }

    /** Download bytes from IPFS by CID. Returns raw bytes or null. */
    private suspend fun ipfsGet(cid: String): ByteArray? {
        return withTimeoutOrNull(Config.IPFS_GET_TIMEOUT.toLong()) {
            suspendCancellableCoroutine { cont ->
                WalletApi.call("ipfs_get", mapOf(
                    "hash" to cid,
                    "timeout" to Config.IPFS_GET_TIMEOUT,
                )) { result ->
                    // Response data can be: List<Int> (byte array) or String (base64)
                    val data = result["data"]
                    if (data is List<*> && cont.isActive) {
                        val bytes = ByteArray(data.size)
                        data.forEachIndexed { i, v -> bytes[i] = ((v as? Number)?.toInt() ?: 0).toByte() }
                        cont.resume(bytes) {}
                    } else if (data is String && cont.isActive) {
                        cont.resume(Base64.decode(data, Base64.DEFAULT)) {}
                    } else if (cont.isActive) {
                        Log.e(TAG, "IPFS get failed for $cid: ${result["error"]}")
                        cont.resume(null) {}
                    }
                }
            }
        }
    }

    // === File Cache ===

    private fun getCacheFile(cid: String): File {
        val safeName = cid.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return File(cacheDir ?: File("/tmp"), safeName)
    }

    private suspend fun evictOldFiles(db: ChatDatabase) {
        val count = db.attachmentDao().countCached()
        if (count <= MAX_CACHED_FILES) return
        // Delete oldest
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
