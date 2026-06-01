package com.privimemobile.ui.chat

import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.privimemobile.R
import com.privimemobile.chat.voice.VoiceRecorder
import com.privimemobile.protocol.Config
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.WalletApi
import com.privimemobile.ui.chat.input.formatVoiceDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Send message pipeline extracted from [ChatScreen] (Phase 3B). */
object ChatSendPipeline {
    fun handlePickedUri(deps: ChatSendDeps, uri: Uri) {
        val info = getFileInfo(deps.context, uri)
        if (info != null) {
            android.util.Log.d("ChatScreen", "File picked: ${info.name}, ${info.size} bytes, ${info.mimeType}")
            val isImage = Helpers.isImageMime(info.mimeType)
            // Images: allow up to MAX_FILE_SIZE (compression will shrink them)
            // Non-images: cap at MAX_INLINE_SIZE (no compression, must fit inline)
            val limit = if (isImage) Config.MAX_FILE_SIZE else Config.MAX_INLINE_SIZE
            if (info.size > limit) {
                android.util.Log.w("ChatScreen", "File too large: ${info.size} > $limit (isImage=$isImage)")
                val msg = if (isImage) {
                    deps.context.getString(R.string.chat_image_too_large, Config.MAX_FILE_SIZE / 1024 / 1024)
                } else {
                    deps.context.getString(R.string.chat_file_too_large, Config.MAX_INLINE_SIZE / 1024)
                }
                Toast.makeText(deps.context, msg, Toast.LENGTH_LONG).show()
                return
            }
            deps.input.pendingFile = PendingFile(uri = uri, name = info.name, size = info.size, mimeType = info.mimeType)
            deps.media.showAttachPicker = false
        } else {
            android.util.Log.w("ChatScreen", "File info is null for uri: $uri")
        }
    }

    fun scheduleMessage(deps: ChatSendDeps, text: String, scheduledAt: Long) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        deps.scope.launch {
            val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get() ?: return@launch
            val myHandle = state.myHandle ?: return@launch
            val ts = System.currentTimeMillis() / 1000
            val schedConvId = if (deps.isGroupMode && deps.groupId != null) {
                com.privimemobile.chat.ChatService.groups.getOrCreateGroupConversation(deps.groupId, deps.groupName ?: deps.context.getString(R.string.chat_group_name_fallback))
            } else {
                val conv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(deps.convKey, deps.handle)
                if (conv.deletedAtTs > 0) com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(conv.id)
                conv.id
            }
            val dedupKey = "$ts:${trimmed.hashCode().toString(16)}:scheduled:true"
            val entity = com.privimemobile.chat.db.entities.MessageEntity(
                conversationId = schedConvId,
                text = trimmed,
                timestamp = ts,
                sent = true,
                type = if (deps.isGroupMode) "group_msg" else "dm",
                senderHandle = myHandle,
                sbbsDedupKey = dedupKey,
                scheduledAt = scheduledAt,
            )
            com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)
            val sdf = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
            val timeStr = sdf.format(java.util.Date(scheduledAt * 1000))
            withContext(Dispatchers.Main) {
                Toast.makeText(deps.context, deps.context.getString(R.string.toast_message_scheduled, timeStr), Toast.LENGTH_LONG).show()
            }
        }
        deps.input.setInputText("")
    }

    suspend fun sendVoiceMessage(deps: ChatSendDeps) {
        val file = deps.voice.voicePreviewFile ?: return
        val waveform = deps.voice.voicePreviewWaveform
        val durationMs = deps.voice.voicePreviewDuration

        if (!deps.isGroupMode && deps.resolvedSbbsAddress.isNullOrEmpty()) {
            Toast.makeText(deps.context, R.string.toast_cannot_send_no_address, Toast.LENGTH_SHORT).show()
            return
        }

        val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
        if (state?.myHandle == null) return

        val ts = System.currentTimeMillis() / 1000

        // Read audio file and encrypt for inline delivery
        val audioBytes = withContext(Dispatchers.IO) { file.readBytes() }

        val (key, iv) = com.privimemobile.protocol.FileCrypto.generateFileKey()
        val ciphertext = com.privimemobile.protocol.FileCrypto.encrypt(audioBytes, key, iv)
        val inlineData = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)

        // Check actual size limit (SBBS max inline size ~750KB after base64)
        if (inlineData.length > com.privimemobile.protocol.Config.MAX_INLINE_SIZE) {
            val sizeKB = inlineData.length / 1024
            val limitKB = com.privimemobile.protocol.Config.MAX_INLINE_SIZE / 1024
            withContext(Dispatchers.Main) {
                Toast.makeText(deps.context, deps.context.getString(R.string.toast_voice_too_large, sizeKB, limitKB), Toast.LENGTH_LONG).show()
            }
            file.delete()
            deps.voice.voicePreviewFile = null
            deps.voice.voicePreviewWaveform = null
            deps.voice.voicePreviewDuration = 0L
            return
        }

        // Encode waveform as base64
        val waveformB64 = if (waveform != null) android.util.Base64.encodeToString(waveform, android.util.Base64.NO_WRAP) else null
        val extras = org.json.JSONObject().apply {
            if (waveformB64 != null) put("waveform", waveformB64)
            put("duration_ms", durationMs)
        }.toString()

        // Build file metadata (OGG/Opus format from native encoder)
        val fileName = "voice_${ts}.ogg"
        val fileMeta = mapOf<String, Any?>(
            "cid" to "inline-${java.util.UUID.randomUUID()}",
            "key" to key,
            "iv" to iv,
            "name" to fileName,
            "size" to audioBytes.size,
            "mime" to "audio/ogg",
            "data" to inlineData,
        )

        val payload = mutableMapOf<String, Any?>(
            "v" to 1, "t" to "file", "ts" to ts,
            "from" to state.myHandle, "to" to (if (deps.isGroupMode) deps.groupId!! else deps.handle),
            "dn" to (state.myDisplayName ?: ""),
            "file" to fileMeta,
            "extras" to extras,
        )

        // Optimistic DB insert
        val voiceConvId = if (deps.isGroupMode) deps.convId else {
            val conv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(deps.convKey, deps.handle)
            if (conv.deletedAtTs > 0) com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(conv.id)
            conv.id
        }
        val dedupKey = "$ts:file:${fileMeta["cid"]}:true"
        val entity = com.privimemobile.chat.db.entities.MessageEntity(
            conversationId = voiceConvId, text = null,
            timestamp = ts, sent = true, type = "file",
            senderHandle = state.myHandle, sbbsDedupKey = dedupKey,
        )
        val msgId = com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)

        // Insert attachment with extras
        if (msgId > 0) {
            com.privimemobile.chat.ChatService.db!!.attachmentDao().insert(
                com.privimemobile.chat.db.entities.AttachmentEntity(
                    messageId = msgId, conversationId = voiceConvId,
                    ipfsCid = fileMeta["cid"] as? String ?: "",
                    encryptionKey = key, encryptionIv = iv,
                    fileName = fileName, fileSize = audioBytes.size.toLong(),
                    mimeType = "audio/ogg",
                    inlineData = inlineData,
                    downloadStatus = "done",
                    extras = extras,
                )
            )
        }

        val preview = "\uD83C\uDFA4 Voice ${formatVoiceDuration(durationMs)}"

        // Delete temp recording file and clear preview state
        file.delete()
        deps.voice.voicePreviewFile = null
        deps.voice.voicePreviewWaveform = null
        deps.voice.voicePreviewDuration = 0L

        // Send via SBBS
        // Update preview BEFORE network send — survives early navigation
        if (deps.isGroupMode && deps.groupId != null) {
            val youLabel = deps.context.getString(R.string.chat_sender_you)
            com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(deps.groupId, ts, "$youLabel: $preview")
            com.privimemobile.chat.ChatService.groups.sendGroupPayload(deps.groupId, payload)
        } else {
            com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(voiceConvId, ts, preview)
            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(deps.resolvedSbbsAddress!!, payload)
        }

        // Clean up temp file
        withContext(Dispatchers.IO) { file.delete() }
    }

    // Send voice message (direct from RecordingResult - Telegram-style release to send)
    suspend fun sendVoiceMessage(deps: ChatSendDeps, result: com.privimemobile.chat.voice.VoiceRecorder.RecordingResult) {
        if (!deps.isGroupMode && deps.resolvedSbbsAddress.isNullOrEmpty()) {
            Toast.makeText(deps.context, R.string.toast_cannot_send_no_address, Toast.LENGTH_SHORT).show()
            result.file.delete()
            return
        }

        val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
        if (state?.myHandle == null) {
            result.file.delete()
            return
        }

        val ts = System.currentTimeMillis() / 1000

        // Read audio file and encrypt for inline delivery
        val audioBytes = withContext(Dispatchers.IO) { result.file.readBytes() }

        val (key, iv) = com.privimemobile.protocol.FileCrypto.generateFileKey()
        val ciphertext = com.privimemobile.protocol.FileCrypto.encrypt(audioBytes, key, iv)
        val inlineData = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)

        // Check actual size limit (SBBS max inline size ~750KB after base64)
        if (inlineData.length > com.privimemobile.protocol.Config.MAX_INLINE_SIZE) {
            val sizeKB = inlineData.length / 1024
            val limitKB = com.privimemobile.protocol.Config.MAX_INLINE_SIZE / 1024
            withContext(Dispatchers.Main) {
                Toast.makeText(deps.context, deps.context.getString(R.string.toast_voice_too_large, sizeKB, limitKB), Toast.LENGTH_LONG).show()
            }
            result.file.delete()
            return
        }

        // Encode waveform as base64
        val waveformB64 = android.util.Base64.encodeToString(result.waveform, android.util.Base64.NO_WRAP)
        val extras = org.json.JSONObject().apply {
            put("waveform", waveformB64)
            put("duration_ms", result.durationMs)
        }.toString()

        // Build file metadata (OGG/Opus format from native encoder)
        val fileName = "voice_${ts}.ogg"
        val fileMeta = mapOf<String, Any?>(
            "cid" to "inline-${java.util.UUID.randomUUID()}",
            "key" to key,
            "iv" to iv,
            "name" to fileName,
            "size" to audioBytes.size,
            "mime" to "audio/ogg",
            "data" to inlineData,
        )

        val payload = mutableMapOf<String, Any?>(
            "v" to 1, "t" to "file", "ts" to ts,
            "from" to state.myHandle, "to" to (if (deps.isGroupMode) deps.groupId!! else deps.handle),
            "dn" to (state.myDisplayName ?: ""),
            "file" to fileMeta,
            "extras" to extras,
        )

        // Optimistic DB insert
        val voiceConvId = if (deps.isGroupMode) deps.convId else {
            val conv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(deps.convKey, deps.handle)
            if (conv.deletedAtTs > 0) com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(conv.id)
            conv.id
        }
        val dedupKey = "$ts:file:${fileMeta["cid"]}:true"
        val entity = com.privimemobile.chat.db.entities.MessageEntity(
            conversationId = voiceConvId, text = null,
            timestamp = ts, sent = true, type = "file",
            senderHandle = state.myHandle, sbbsDedupKey = dedupKey,
        )
        val msgId = com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)

        // Insert attachment with extras
        if (msgId > 0) {
            com.privimemobile.chat.ChatService.db!!.attachmentDao().insert(
                com.privimemobile.chat.db.entities.AttachmentEntity(
                    messageId = msgId, conversationId = voiceConvId,
                    ipfsCid = fileMeta["cid"] as? String ?: "",
                    encryptionKey = key, encryptionIv = iv,
                    fileName = fileName, fileSize = audioBytes.size.toLong(),
                    mimeType = "audio/ogg",
                    inlineData = inlineData,
                    downloadStatus = "done",
                    extras = extras,
                )
            )
        }

        val preview = "\uD83C\uDFA4 Voice ${formatVoiceDuration(result.durationMs)}"

        // Delete temp recording file
        result.file.delete()

        // Update preview BEFORE network send — survives early navigation
        if (deps.isGroupMode && deps.groupId != null) {
            val youLabel = deps.context.getString(R.string.chat_sender_you)
            com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(deps.groupId, ts, "$youLabel: $preview")
            com.privimemobile.chat.ChatService.groups.sendGroupPayload(deps.groupId, payload)
        } else {
            com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(voiceConvId, ts, preview)
            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(deps.resolvedSbbsAddress!!, payload)
        }
    }

    fun handleSend(deps: ChatSendDeps) {
        val now = System.currentTimeMillis()
        if (now - deps.getLastSendTime() < 3000) {
            android.widget.Toast.makeText(deps.context, R.string.toast_slow_down, android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        deps.setLastSendTime(now)

        // Clear draft on send and dismiss unread divider
        deps.clearInitialUnreadDivider()
        deps.clearDraft()

        val trimmed = deps.input.inputText.text.trim()

        // Edit mode — update existing message instead of creating new one
        if (deps.input.editingMsg != null) {
            val editTarget = deps.input.editingMsg!!
            if (trimmed.isNotEmpty() && trimmed != editTarget.text) {
                deps.scope.launch {
                    val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                    if (state?.myHandle != null && deps.convId > 0L) {
                        // Update local DB
                        com.privimemobile.chat.ChatService.db?.messageDao()?.editMessage(
                            deps.convId, editTarget.timestamp, state.myHandle!!, trimmed
                        )
                        // Update chat list preview if this was the latest message
                        val convEntity = com.privimemobile.chat.ChatService.db?.conversationDao()?.findById(deps.convId)
                        if (convEntity != null && convEntity.lastMessageTs == editTarget.timestamp) {
                            com.privimemobile.chat.ChatService.db?.conversationDao()?.updateLastMessage(deps.convId, editTarget.timestamp, trimmed.take(100))
                        }
                        // Update group preview if this is a group conversation
                        if (deps.isGroupMode && deps.groupId != null) {
                            val group = com.privimemobile.chat.ChatService.db?.groupDao()?.findByGroupId(deps.groupId)
                            if (group != null && group.lastMessageTs == editTarget.timestamp) {
                                val senderLabel = deps.context.getString(com.privimemobile.R.string.chat_sender_you)
                                com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(deps.groupId, editTarget.timestamp, "${senderLabel}: ${trimmed.take(40)}")
                            }
                        }
                        // Send SBBS edit to recipient(s)
                        val editPayload = mapOf(
                            "v" to 1,
                            "t" to "edit",
                            "ts" to System.currentTimeMillis() / 1000,
                            "from" to state.myHandle!!,
                            "to" to (if (deps.isGroupMode) deps.groupId!! else deps.handle),
                            "msg_ts" to editTarget.timestamp,
                            "msg" to trimmed,
                        )
                        if (deps.isGroupMode && deps.groupId != null) {
                            com.privimemobile.chat.ChatService.groups.sendGroupPayload(deps.groupId, editPayload)
                        } else {
                            val sendAddr = deps.resolvedSbbsAddress
                            if (!sendAddr.isNullOrEmpty()) {
                                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(sendAddr, editPayload)
                            }
                        }
                    }
                }
            }
            deps.input.editingMsg = null
            deps.input.setInputText("")
            return
        }

        // /tip command — send BEAM or any asset
        // DM:    /tip <amount> [asset_id] [message]
        // Group: /tip @handle <amount> [asset_id] [message]
        // Group with reply: /tip <amount> [asset_id] [message] (tips the quoted message sender)
        if (trimmed.startsWith("/tip ", ignoreCase = true)) {
            val parts = trimmed.removePrefix("/tip ").trimStart()
            val tokens = parts.split("\\s+".toRegex(), limit = 5)

            // In group mode: first token is @handle, OR use quoted reply sender
            val tipTargetHandle: String
            val tipTokens: List<String>
            if (deps.isGroupMode) {
                val firstToken = tokens.getOrNull(0) ?: ""
                if (firstToken.startsWith("@") && firstToken.length >= 2) {
                    // Explicit @handle
                    tipTargetHandle = firstToken.removePrefix("@")
                    tipTokens = tokens.drop(1)
                } else if (deps.input.replyingTo != null && !deps.input.replyingTo!!.from.isNullOrEmpty() && !deps.input.replyingTo!!.sent) {
                    // Quoted reply — tip the sender of the quoted message
                    tipTargetHandle = deps.input.replyingTo!!.from
                    tipTokens = tokens // all tokens are amount/asset/msg
                } else {
                    Toast.makeText(deps.context, R.string.toast_reply_or_tip_hint, Toast.LENGTH_LONG).show()
                    deps.input.setInputText(""); return
                }
            } else {
                tipTargetHandle = deps.handle
                tipTokens = tokens
            }

            val amountBeam = tipTokens.getOrNull(0)?.replace(',', '.')?.toDoubleOrNull()
            if (amountBeam == null || amountBeam <= 0) {
                val usage = if (deps.isGroupMode) deps.context.getString(R.string.toast_tip_usage_group)
                    else deps.context.getString(R.string.toast_tip_usage_dm)
                Toast.makeText(deps.context, usage, Toast.LENGTH_SHORT).show()
                return
            }

            // Resolve wallet ID (on-chain for money) and sbbs_address (SBBS channel for notification)
            deps.scope.launch {
                val tipWalletId = if (deps.isGroupMode) {
                    // Look up target handle's wallet ID from group members or contacts
                    val member = com.privimemobile.chat.ChatService.db?.groupDao()?.findMember(deps.groupId!!, tipTargetHandle)
                    member?.walletId ?: com.privimemobile.chat.ChatService.db?.contactDao()?.findByHandle(tipTargetHandle)?.walletId
                } else {
                    deps.resolvedWalletId
                }
                val tipSbbsAddress = if (deps.isGroupMode) {
                    val member = com.privimemobile.chat.ChatService.db?.groupDao()?.findMember(deps.groupId!!, tipTargetHandle)
                    member?.sbbsAddress ?: member?.walletId
                        ?: com.privimemobile.chat.ChatService.db?.contactDao()?.findByHandle(tipTargetHandle)?.sbbsAddress
                        ?: com.privimemobile.chat.ChatService.db?.contactDao()?.findByHandle(tipTargetHandle)?.walletId
                } else {
                    deps.resolvedSbbsAddress
                }

                if (tipWalletId.isNullOrEmpty() || tipSbbsAddress.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(deps.context, deps.context.getString(R.string.toast_cannot_resolve, tipTargetHandle), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // Parse asset ID and caption
                val secondToken = tipTokens.getOrNull(1)
                val secondIsAssetId = secondToken?.toIntOrNull() != null
                val assetId = if (secondIsAssetId) secondToken!!.toInt() else 0
                val caption = if (secondIsAssetId) {
                    tipTokens.drop(2).joinToString(" ").trim()
                } else {
                    tipTokens.drop(1).joinToString(" ").trim()
                }
                val amountGroth = (amountBeam * 100_000_000).toLong()
                val assetName = com.privimemobile.wallet.assetTicker(assetId)
                val tipLabel = deps.context.getString(R.string.chat_tip_to, "@$tipTargetHandle", "${Helpers.formatBeam(amountGroth)} $assetName")

                // Balance check
                val bal = com.privimemobile.wallet.WalletEventBus.assetBalances[assetId]
                val spendable = (bal?.available ?: 0L) + (bal?.shielded ?: 0L)
                if (spendable < amountGroth) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(deps.context, deps.context.getString(R.string.toast_insufficient_balance, assetName, Helpers.formatBeam(spendable), Helpers.formatBeam(amountGroth)), Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                if (assetId != 0) {
                    val beamBal = com.privimemobile.wallet.WalletEventBus.assetBalances[0]
                    val beamSpendable = (beamBal?.available ?: 0L) + (beamBal?.shielded ?: 0L)
                    if (beamSpendable <= 0) {
                        withContext(Dispatchers.Main) { Toast.makeText(deps.context, R.string.toast_insufficient_beam_fee, Toast.LENGTH_LONG).show() }
                        return@launch
                    }
                }

                try {
                    val txComment = deps.context.getString(R.string.chat_tip_tx_comment, tipTargetHandle) + if (caption.isNotEmpty()) " — $caption" else ""
                    val txResult = com.privimemobile.protocol.WalletApi.callAsync("tx_send", mapOf(
                        "value" to amountGroth,
                        "address" to tipWalletId,
                        "asset_id" to assetId,
                        "comment" to txComment,
                    ))
                    if (txResult.containsKey("error")) {
                        val errMsg = Helpers.extractError(txResult, deps.context)
                        val tipCancelled = errMsg == deps.context.getString(R.string.tx_cancelled)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(deps.context,
                                if (tipCancelled) deps.context.getString(R.string.tip_cancelled) else deps.context.getString(R.string.tip_failed, errMsg),
                                if (tipCancelled) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                            ).show()
                        }
                        return@launch
                    }

                    // Insert tip message + send SBBS notification
                    val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                    if (state?.myHandle != null) {
                        val ts = System.currentTimeMillis() / 1000
                        val dedupKey = "$ts:tip:$amountGroth:$assetId:true"
                        val entity = com.privimemobile.chat.db.entities.MessageEntity(
                            conversationId = deps.convId,
                            text = "\u2192@$tipTargetHandle" + if (caption.isNotEmpty()) "\n$caption" else "",
                            timestamp = ts,
                            sent = true,
                            type = "tip",
                            tipAmount = amountGroth,
                            tipAssetId = assetId,
                            senderHandle = state.myHandle,
                            sbbsDedupKey = dedupKey,
                        )
                        com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)

                        val payload = mutableMapOf<String, Any?>(
                            "v" to 1, "t" to "tip", "ts" to ts,
                            "from" to state.myHandle!!, "to" to tipTargetHandle,
                            "dn" to (state.myDisplayName ?: ""),
                            "amount" to amountGroth,
                        )
                        if (assetId != 0) payload["asset_id"] = assetId
                        if (caption.isNotEmpty()) payload["msg"] = caption

                        if (deps.isGroupMode && deps.groupId != null) {
                            // Update preview BEFORE network send — survives early navigation
                            val youPrefix = deps.context.getString(R.string.chat_sender_you)
                            com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(deps.groupId, ts, "$youPrefix: $tipLabel")
                            com.privimemobile.chat.ChatService.groups.sendGroupPayload(deps.groupId, payload)
                        } else {
                            val tipConv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(deps.convKey, tipTargetHandle)
                            if (tipConv.deletedAtTs > 0) com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(tipConv.id)
                            com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(tipConv.id, ts, tipLabel)
                            com.privimemobile.chat.ChatService.sbbs.sendWithRetry(tipSbbsAddress, payload)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(deps.context, deps.context.getString(R.string.chat_tip_sent, tipLabel), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        val errorMap = mapOf("error" to mapOf("message" to (e.message ?: deps.context.getString(R.string.chat_send_failed))))
                        val errMsg = Helpers.extractError(errorMap, deps.context)
                        val tipCancelled = errMsg == deps.context.getString(R.string.tx_cancelled)
                        Toast.makeText(deps.context,
                            if (tipCancelled) deps.context.getString(R.string.tip_cancelled) else deps.context.getString(R.string.tip_failed, errMsg),
                            if (tipCancelled) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            deps.input.setInputText("")
            deps.input.replyingTo = null
            return
        }

        // /poll command — create a poll
        // Usage: /poll Question? | Option 1 | Option 2 | Option 3
        if (trimmed.startsWith("/poll ", ignoreCase = true)) {
            val parts = trimmed.removePrefix("/poll ").split("|").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 3) { // question + at least 2 options
                val question = parts[0]
                val options = parts.drop(1).map { mapOf("text" to it, "voters" to emptyList<String>()) }
                val pollJson = org.json.JSONObject().apply {
                    put("question", question)
                    put("options", org.json.JSONArray(options.map { org.json.JSONObject(it) }))
                }.toString()

                deps.scope.launch {
                    val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                    if (state?.myHandle != null) {
                        val ts = System.currentTimeMillis() / 1000
                        val dedupKey = "$ts:poll:${question.hashCode().toString(16)}:true"
                        val entity = com.privimemobile.chat.db.entities.MessageEntity(
                            conversationId = deps.convId,
                            text = question,
                            timestamp = ts,
                            sent = true,
                            type = "poll",
                            senderHandle = state.myHandle,
                            sbbsDedupKey = dedupKey,
                            pollData = pollJson,
                        )
                        com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)
                        val payload = mapOf(
                            "v" to 1, "t" to "poll", "ts" to ts,
                            "from" to state.myHandle!!, "to" to (if (deps.isGroupMode) deps.groupId!! else deps.handle),
                            "dn" to (state.myDisplayName ?: ""),
                            "msg" to question, "poll" to pollJson,
                        )
                        if (deps.isGroupMode && deps.groupId != null) {
                            // Update preview BEFORE network send \u2014 survives early navigation
                            val youLabel = deps.context.getString(R.string.chat_sender_you)
                            com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(deps.groupId, ts, "$youLabel: \uD83D\uDCCA $question")
                            com.privimemobile.chat.ChatService.groups.sendGroupPayload(deps.groupId, payload)
                        } else {
                            val convDb = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(deps.convKey, deps.handle)
                            if (convDb.deletedAtTs > 0) com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(convDb.id)
                            com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(convDb.id, ts, "\uD83D\uDCCA $question")
                            val walletId = deps.resolvedSbbsAddress
                            if (!walletId.isNullOrEmpty()) {
                                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(walletId, payload)
                            }
                        }
                    }
                }
            } else {
                Toast.makeText(deps.context, R.string.toast_poll_usage, Toast.LENGTH_LONG).show()
            }
            deps.input.setInputText("")
            return
        }

        // Send file if pending
        if (deps.input.pendingFile != null) {
            val file = deps.input.pendingFile!!
            android.util.Log.d("ChatScreen", "Sending file: ${file.name}, ${file.size} bytes, isGroup=$deps.isGroupMode")
            if (!deps.isGroupMode && deps.resolvedSbbsAddress.isNullOrEmpty()) {
                android.util.Log.w("ChatScreen", "Cannot send file — no resolved wallet ID")
                return
            }
            // Capture reply text BEFORE coroutine
            val fileReplyText = deps.input.replyingTo?.text?.take(200)?.ifEmpty { null }
            deps.onUploadingChange(true)
            deps.scope.launch {
                try {
                    // Prepare file: compress → encrypt → inline or IPFS
                    android.util.Log.d("ChatScreen", "Calling IpfsTransport.prepareFile...")
                    val fileMeta = com.privimemobile.chat.transport.IpfsTransport.prepareFile(
                        deps.context, file.uri, file.name, file.size, file.mimeType,
                    )
                    if (fileMeta != null) {
                        val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
                        if (state?.myHandle != null) {
                            val ts = System.currentTimeMillis() / 1000
                            val stickerMeta = deps.input.pendingStickerMeta
                            val msgType = if (stickerMeta != null) "sticker" else "file"
                            val payload = mutableMapOf<String, Any?>(
                                "v" to 1, "t" to msgType, "ts" to ts,
                                "from" to state.myHandle!!, "to" to (if (deps.isGroupMode) deps.groupId!! else deps.handle),
                                "dn" to (state.myDisplayName ?: ""),
                                "file" to fileMeta,
                            )
                            if (trimmed.isNotEmpty()) payload["msg"] = trimmed
                            if (fileReplyText != null) payload["reply"] = fileReplyText
                            // Add sticker pack metadata
                            if (stickerMeta != null) {
                                payload["pack_name"] = stickerMeta.packName
                                payload["pack_id"] = stickerMeta.packId
                                payload["pack_total"] = stickerMeta.packTotal
                                if (stickerMeta.emoji != null) payload["sticker_emoji"] = stickerMeta.emoji
                            }

                            // Optimistic DB insert
                            val fileConvId = if (deps.isGroupMode) deps.convId else {
                                val conv = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(deps.convKey, deps.handle)
                                if (conv.deletedAtTs > 0) com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(conv.id)
                                conv.id
                            }
                            val dedupKey = "$ts:$msgType:${fileMeta["cid"]}:true"
                            val entity = com.privimemobile.chat.db.entities.MessageEntity(
                                conversationId = fileConvId, text = trimmed.ifEmpty { null },
                                timestamp = ts, sent = true, type = msgType,
                                senderHandle = state.myHandle, sbbsDedupKey = dedupKey,
                                replyText = fileReplyText,
                                stickerPackName = stickerMeta?.packName,
                                stickerPackId = stickerMeta?.packId,
                                stickerEmoji = stickerMeta?.emoji,
                                stickerPackTotal = stickerMeta?.packTotal ?: 0,
                            )
                            val msgId = com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)

                            // Insert attachment
                            val cid = fileMeta["cid"] as? String ?: ""
                            // Pre-cache decrypted file BEFORE message insert (prevents blank bubble flicker)
                            if (cid.isNotEmpty()) {
                                try {
                                    val dlPath = com.privimemobile.chat.transport.IpfsTransport.downloadFile(
                                        0L, cid,
                                        fileMeta["key"] as? String ?: "",
                                        fileMeta["iv"] as? String ?: "",
                                        fileMeta["data"] as? String,
                                    )
                                    deps.files.filePaths[cid] = dlPath
                                } catch (_: Exception) {}
                            }
                            if (msgId > 0) {
                                com.privimemobile.chat.ChatService.db!!.attachmentDao().insert(
                                    com.privimemobile.chat.db.entities.AttachmentEntity(
                                        messageId = msgId, conversationId = fileConvId,
                                        ipfsCid = cid,
                                        encryptionKey = fileMeta["key"] as? String ?: "",
                                        encryptionIv = fileMeta["iv"] as? String ?: "",
                                        fileName = fileMeta["name"] as? String ?: "file",
                                        fileSize = (fileMeta["size"] as? Number)?.toLong() ?: 0,
                                        mimeType = fileMeta["mime"] as? String ?: "",
                                        inlineData = fileMeta["data"] as? String,
                                        downloadStatus = "done",
                                    )
                                )
                            }

                            val preview = if (msgType == "sticker") {
                                "${stickerMeta?.emoji ?: "\uD83C\uDFAD"} Sticker"
                            } else {
                                "\uD83D\uDCCE ${fileMeta["name"]}"
                            }

                            // Clear UI state BEFORE SBBS send (message already in DB and visible)
                            deps.onUploadingChange(false)
                            deps.input.pendingFile = null
                            deps.input.pendingStickerMeta = null
                            deps.input.setInputText("")
                            deps.input.replyingTo = null

                            if (deps.isGroupMode && deps.groupId != null) {
                                // Update preview BEFORE network send — survives early navigation
                                val youLabel = deps.context.getString(R.string.chat_sender_you)
                                com.privimemobile.chat.ChatService.db?.groupDao()?.updateLastMessage(deps.groupId, ts, "$youLabel: $preview")
                                com.privimemobile.chat.ChatService.groups.sendGroupPayload(deps.groupId, payload)
                            } else {
                                com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(fileConvId, ts, preview)
                                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(deps.resolvedSbbsAddress!!, payload)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatScreen", "File send error: ${e.message}")
                    android.widget.Toast.makeText(deps.context, deps.context.getString(R.string.chat_file_send_failed) + ": " + (e.message ?: ""), android.widget.Toast.LENGTH_LONG).show()
                } finally {
                    deps.onUploadingChange(false)
                    deps.input.pendingFile = null
                    deps.input.pendingStickerMeta = null
                    deps.input.setInputText("")
                    deps.input.replyingTo = null
                }
            }
            return
        }

        // Regular text message
        if (trimmed.isEmpty()) return

        // Group mode — send via GroupManager
        if (deps.isGroupMode && deps.groupId != null) {
            val grpReplyText = deps.input.replyingTo?.text?.take(200)?.ifEmpty { null }
            val grpReplySender = deps.input.replyingTo?.from
            val grpReplyTs = deps.input.replyingTo?.timestamp ?: 0L
            val grpTimer = deps.input.oneShotTimer
            deps.scope.launch {
                com.privimemobile.chat.ChatService.groups.sendGroupMessage(
                    deps.groupId, trimmed, replyText = grpReplyText,
                    replySender = grpReplySender, replyMsgTs = grpReplyTs,
                    ttl = grpTimer,
                )
            }
            deps.input.setInputText("")
            deps.input.replyingTo = null
            deps.input.oneShotTimer = 0
            // Clear draft
            if (deps.convId > 0L) {
                deps.scope.launch { com.privimemobile.chat.ChatService.db?.conversationDao()?.setDraft(deps.convId, null) }
            }
            return
        }

        val sendAddress = deps.resolvedSbbsAddress
        if (sendAddress.isNullOrEmpty()) return

        // Capture state BEFORE coroutine (state may be cleared by main thread before coroutine runs)
        val replyText = deps.input.replyingTo?.text?.take(200)?.ifEmpty { null }
        val replySenderHandle = deps.input.replyingTo?.from
        val replyMsgTs = deps.input.replyingTo?.timestamp ?: 0L
        val capturedTimer = deps.input.oneShotTimer

        // Send via new chat system
        deps.scope.launch {
            val state = com.privimemobile.chat.ChatService.db?.chatStateDao()?.get()
            if (state?.myHandle != null) {
                val ts = System.currentTimeMillis() / 1000
                val payload = mutableMapOf<String, Any?>(
                    "v" to 1,
                    "t" to "dm",
                    "ts" to ts,
                    "from" to state.myHandle!!,
                    "to" to deps.handle,
                    "dn" to (state.myDisplayName ?: ""),
                    "msg" to trimmed,
                )
                if (replyText != null) {
                    payload["reply"] = replyText
                    if (replySenderHandle != null) payload["reply_from"] = replySenderHandle
                    if (replyMsgTs > 0) payload["reply_ts"] = replyMsgTs
                }
                // Disappearing message TTL — per-message one-shot takes priority over conversation-level
                if (capturedTimer > 0) payload["ttl"] = capturedTimer
                val expiresAt = if (capturedTimer > 0) ts + capturedTimer else 0L
                // Optimistic insert into DB — un-delete if conversation was tombstoned
                val convDb = com.privimemobile.chat.ChatService.db!!.conversationDao().getOrCreate(deps.convKey, deps.handle)
                if (convDb.deletedAtTs > 0) {
                    com.privimemobile.chat.ChatService.db!!.conversationDao().undelete(convDb.id)
                }
                val dedupKey = "$ts:${trimmed.hashCode().toString(16)}:true"
                val entity = com.privimemobile.chat.db.entities.MessageEntity(
                    conversationId = convDb.id,
                    text = trimmed,
                    timestamp = ts,
                    sent = true,
                    type = "dm",
                    senderHandle = state.myHandle,
                    sbbsDedupKey = dedupKey,
                    replyText = replyText,
                    replySender = replySenderHandle,
                    replyTs = replyMsgTs,
                    expiresAt = expiresAt,
                )
                com.privimemobile.chat.ChatService.db!!.messageDao().insert(entity)
                com.privimemobile.chat.ChatService.db!!.conversationDao().updateLastMessage(convDb.id, ts, trimmed.take(100))

                // Send via SBBS
                com.privimemobile.chat.ChatService.sbbs.sendWithRetry(sendAddress, payload)
            }
        }

        deps.input.setInputText("")
        deps.input.replyingTo = null
        deps.input.oneShotTimer = 0  // clear per-message timer after send
    }
}
