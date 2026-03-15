package com.privimemobile.protocol

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * File sharing — IPFS upload/download with AES-256-GCM encryption.
 *
 * Ports file-sharing.ts to Kotlin.
 * Handles both inline delivery (small files embedded in SBBS) and IPFS delivery.
 */
object FileSharing {
    private const val TAG = "FileSharing"

    // In-memory cache: CID -> local file path
    private val downloadedFiles = mutableMapOf<String, String>()
    private val activeDownloads = mutableSetOf<String>()

    @Volatile
    var uploadInProgress = false
        private set

    // === Download status listeners ===

    enum class DownloadStatus { IDLE, DOWNLOADING, DECRYPTING, DONE, ERROR }

    private val statusListeners = mutableSetOf<(String, DownloadStatus, String?) -> Unit>()

    fun onDownloadStatus(listener: (cid: String, status: DownloadStatus, path: String?) -> Unit): () -> Unit {
        statusListeners.add(listener)
        return { statusListeners.remove(listener) }
    }

    private fun emitStatus(cid: String, status: DownloadStatus, path: String? = null) {
        statusListeners.forEach { it(cid, status, path) }
    }

    /** Get local file path for a cached file. */
    fun getLocalFilePath(cid: String): String? {
        return downloadedFiles[cid] ?: FileCache.getCachedFilePath(cid)
    }

    /** Check if a file is already downloaded/cached. */
    suspend fun isFileDownloaded(cid: String): Boolean {
        if (downloadedFiles.containsKey(cid)) return true
        return FileCache.getCachedFilePath(cid) != null
    }

    /**
     * Send a file to a conversation.
     *
     * @param context Android context
     * @param fileUri Content URI of the picked file
     * @param fileName Display name
     * @param fileSize File size in bytes
     * @param mimeType MIME type
     * @param convKey Conversation key (e.g., "@handle")
     * @param caption Optional text caption
     */
    suspend fun sendFile(
        context: Context,
        fileUri: Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String,
        convKey: String,
        caption: String = "",
    ) {
        if (uploadInProgress) throw IllegalStateException("Upload already in progress")

        val identity = ProtocolStartup.identity.value
            ?: throw IllegalStateException("Not registered")
        val contact = ProtocolStartup.contacts.value[convKey]
            ?: throw IllegalStateException("Contact not resolved")
        if (contact.walletId.isEmpty()) throw IllegalStateException("Recipient address not resolved")

        if (fileSize > Config.MAX_FILE_SIZE) throw IllegalArgumentException("File too large (max ${Helpers.formatFileSize(Config.MAX_FILE_SIZE.toLong())})")

        var mime = mimeType
        if (mime !in Config.ALLOWED_MIME_TYPES) throw IllegalArgumentException("Unsupported file type: $mime")

        uploadInProgress = true
        try {
            // 1. Read file bytes
            var plaintext = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(fileUri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Cannot read file")
            }

            // 2. Compress image if applicable
            if (Helpers.isImageMime(mime) && plaintext.size > Config.COMPRESS_MIN_SIZE && mime != "image/gif") {
                val compressed = compressImage(plaintext, Config.IMAGE_MAX_DIM, Config.IMAGE_QUALITY)
                if (compressed != null && compressed.size < plaintext.size) {
                    plaintext = compressed
                    mime = "image/jpeg"
                }
            }

            // 3. Generate key + IV
            val (keyHex, ivHex) = FileCrypto.generateFileKey()

            // 4. Encrypt
            val ciphertext = FileCrypto.encrypt(plaintext, keyHex, ivHex)

            // 5. Decide delivery: inline vs IPFS
            val fileCid: String
            var inlineData: String? = null

            if (ciphertext.size <= Config.MAX_INLINE_SIZE) {
                inlineData = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
                fileCid = "inline-${System.currentTimeMillis().toString(36)}${(Math.random() * 1000000).toLong().toString(36)}"
            } else {
                fileCid = uploadToIpfs(ciphertext)
            }

            // 6. Build SBBS file message
            val ts = System.currentTimeMillis() / 1000
            val truncName = if (fileName.length > 60) fileName.take(57) + "..." else fileName
            val fileObj = mutableMapOf<String, Any?>(
                "cid" to fileCid,
                "key" to keyHex,
                "iv" to ivHex,
                "name" to truncName,
                "size" to fileSize,
                "mime" to mime,
            )
            if (inlineData != null) fileObj["data"] = inlineData

            val toHandle = convKey.removePrefix("@")
            val msgObj = mutableMapOf<String, Any?>(
                "v" to 1, "t" to "file", "ts" to ts,
                "from" to identity.handle,
                "dn" to identity.displayName,
                "to" to toHandle,
                "file" to fileObj,
            )
            if (caption.isNotEmpty()) msgObj["msg"] = caption

            // 7. Send via SBBS with retries
            SbbsMessaging.sendWithRetry(contact.walletId, msgObj)

            // 8. Optimistic UI update
            val msg = ChatMessage(
                id = "$ts-${identity.handle}-$toHandle",
                from = identity.handle,
                to = toHandle,
                text = caption,
                timestamp = ts,
                sent = true,
                displayName = identity.displayName,
                file = FileAttachment(cid = fileCid, name = truncName, size = fileSize),
                type = "file",
            )
            val convs = ProtocolStartup.conversations.value.toMutableMap()
            val msgs = convs.getOrPut(convKey) { emptyList() }.toMutableList()
            msgs.add(msg)
            convs[convKey] = msgs
            ProtocolStartup.updateConversations(convs)
        } finally {
            uploadInProgress = false
        }
    }

    /**
     * Download and decrypt a file.
     * Returns local file path for display.
     * Emits download status events for UI progress tracking.
     */
    suspend fun downloadFile(
        context: Context,
        cid: String,
        keyHex: String,
        ivHex: String,
        mime: String,
        inlineData: String? = null,
    ): String {
        // Check caches
        downloadedFiles[cid]?.let {
            emitStatus(cid, DownloadStatus.DONE, it)
            return it
        }
        FileCache.getCachedFilePath(cid)?.let {
            downloadedFiles[cid] = it
            emitStatus(cid, DownloadStatus.DONE, it)
            return it
        }

        if (activeDownloads.contains(cid)) {
            // Wait for in-progress download
            while (activeDownloads.contains(cid)) delay(200)
            return downloadedFiles[cid] ?: throw IllegalStateException("Download failed")
        }

        activeDownloads.add(cid)
        try {
            emitStatus(cid, DownloadStatus.DOWNLOADING)

            val ciphertext: ByteArray = if (inlineData != null) {
                // Inline: data embedded in message
                Base64.decode(inlineData, Base64.DEFAULT)
            } else {
                // IPFS: download from network
                downloadFromIpfs(cid)
            }

            if (ciphertext.size > Config.MAX_FILE_SIZE) throw IllegalStateException("File exceeds size limit")

            // Decrypt
            emitStatus(cid, DownloadStatus.DECRYPTING)
            val plaintext = withContext(Dispatchers.Default) {
                FileCrypto.decrypt(ciphertext, keyHex, ivHex)
            }

            // Cache to disk
            val localPath = withContext(Dispatchers.IO) {
                FileCache.cacheFile(context, cid, plaintext, mime)
            }

            downloadedFiles[cid] = localPath
            emitStatus(cid, DownloadStatus.DONE, localPath)
            return localPath
        } catch (e: Exception) {
            emitStatus(cid, DownloadStatus.ERROR)
            throw e
        } finally {
            activeDownloads.remove(cid)
        }
    }

    /**
     * Auto-download images under size threshold (up to 3 concurrent).
     * Returns map of CID -> local file path for successfully downloaded files.
     */
    suspend fun autoDownloadImages(
        context: Context,
        messages: List<ChatMessage>,
    ): Map<String, String> {
        val results = mutableMapOf<String, String>()
        val toDownload = messages.filter { msg ->
            val file = msg.file ?: return@filter false
            !downloadedFiles.containsKey(file.cid) &&
            !activeDownloads.contains(file.cid) &&
            (file.data != null || (Helpers.isImageMime(file.mime) && file.size <= Config.AUTO_DL_MAX_SIZE))
        }
        // Download up to 3 concurrently
        val batch = toDownload.take(3)
        coroutineScope {
            batch.map { msg ->
                async {
                    val file = msg.file ?: return@async
                    try {
                        val path = downloadFile(context, file.cid, file.key, file.iv, file.mime, file.data)
                        results[file.cid] = path
                    } catch (_: Exception) { /* non-fatal */ }
                }
            }.awaitAll()
        }
        return results
    }

    private suspend fun uploadToIpfs(data: ByteArray): String = suspendCancellableCoroutine { cont ->
        val dataList = data.map { it.toInt() and 0xFF }
        WalletApi.call("ipfs_add", mapOf(
            "data" to dataList,
            "pin" to true,
            "timeout" to Config.IPFS_GET_TIMEOUT,
        )) { result ->
            if (result.containsKey("error")) {
                val err = result["error"]
                val msg = when (err) {
                    is Map<*, *> -> err["message"] as? String ?: "IPFS upload failed"
                    is String -> err
                    else -> "IPFS upload failed"
                }
                if (cont.isActive) cont.resumeWithException(Exception(msg))
            } else {
                val hash = result["hash"] as? String
                if (hash != null && cont.isActive) cont.resume(hash)
                else if (cont.isActive) cont.resumeWithException(Exception("No IPFS hash returned"))
            }
        }
    }

    private suspend fun downloadFromIpfs(cid: String): ByteArray = suspendCancellableCoroutine { cont ->
        WalletApi.call("ipfs_get", mapOf(
            "hash" to cid,
            "timeout" to Config.IPFS_GET_TIMEOUT,
        )) { result ->
            if (result.containsKey("error")) {
                val err = result["error"]
                val msg = when (err) {
                    is Map<*, *> -> err["message"] as? String ?: "IPFS download failed"
                    is String -> err
                    else -> "IPFS download failed"
                }
                if (cont.isActive) cont.resumeWithException(Exception(msg))
            } else {
                @Suppress("UNCHECKED_CAST")
                val data = result["data"] as? List<Number>
                if (data != null && cont.isActive) {
                    cont.resume(ByteArray(data.size) { data[it].toByte() })
                } else if (cont.isActive) {
                    cont.resumeWithException(Exception("No data returned from IPFS"))
                }
            }
        }
    }

    /** Compress an image to JPEG with max dimension and quality. */
    private fun compressImage(data: ByteArray, maxDim: Int, quality: Int): ByteArray? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, opts)

            val w = opts.outWidth
            val h = opts.outHeight
            if (w <= 0 || h <= 0) return null

            // Calculate sample size for efficient memory use
            var sampleSize = 1
            while (w / sampleSize > maxDim * 2 || h / sampleSize > maxDim * 2) sampleSize *= 2

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, decodeOpts) ?: return null

            // Scale to max dimension
            val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1f)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else bitmap

            // Compress to JPEG
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            out.toByteArray()
        } catch (_: Exception) {
            null
        }
    }
}
