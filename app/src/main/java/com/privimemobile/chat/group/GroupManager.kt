package com.privimemobile.chat.group

import android.util.Log
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.ChatDatabase
import com.privimemobile.chat.db.entities.GroupEntity
import com.privimemobile.chat.db.entities.GroupMemberEntity
import com.privimemobile.chat.db.entities.PendingTxEntity
import com.privimemobile.protocol.Helpers
import com.privimemobile.protocol.ShaderInvoker
import kotlinx.coroutines.*

/**
 * GroupManager — handles group chat contract interactions.
 *
 * TX methods: create, join, leave, remove, set role, update info, transfer, pin, report, delete
 * View methods: view group, list members, list my groups, search groups
 */
class GroupManager(
    private val db: ChatDatabase,
    private val scope: CoroutineScope,
) {
    private val TAG = "GroupManager"

    // ========================================================================
    // Group conversation helpers
    // ========================================================================

    /** Get or create a ConversationEntity for a group (used to store messages). */
    suspend fun getOrCreateGroupConversation(groupId: String, groupName: String = "Group"): Long {
        val convKey = "g_${groupId.take(16)}"
        val existing = db.conversationDao().findByKey(convKey)
        if (existing != null) return existing.id

        val conv = com.privimemobile.chat.db.entities.ConversationEntity(
            convKey = convKey,
            displayName = groupName,
            handle = groupId,
        )
        return db.conversationDao().insert(conv)
    }

    /** Get conversation ID for a group (returns null if not created yet). */
    suspend fun getGroupConversationId(groupId: String): Long? {
        val convKey = "g_${groupId.take(16)}"
        return db.conversationDao().findByKey(convKey)?.id
    }

    // ========================================================================
    // TX methods (invoke contract, generate kernel)
    // ========================================================================

    fun createGroup(
        name: String,
        isPublic: Boolean = false,
        requireApproval: Boolean = false,
        maxMembers: Int = 0,
        defaultPermissions: Int = 0,
        joinPassword: String? = null,
        description: String? = null,
        onResult: ((success: Boolean, groupId: String?, error: String?) -> Unit)? = null,
    ) {
        val args = mutableMapOf<String, Any?>(
            "name" to name,
            "is_public" to if (isPublic) 1 else 0,
            "require_approval" to if (requireApproval) 1 else 0,
            "max_members" to maxMembers,
            "default_permissions" to defaultPermissions,
        )
        if (!joinPassword.isNullOrEmpty()) {
            args["join_password"] = joinPassword
        }
        ShaderInvoker.tx("user", "create_group", args,
            callback = { result ->
                if (result.containsKey("error")) {
                    onResult?.invoke(false, null, com.privimemobile.protocol.Helpers.extractError(result))
                } else {
                    val txId = result["txid"]?.toString() ?: result["txId"]?.toString()
                    Log.d(TAG, "Create group TX submitted: $name txId=$txId")
                    if (txId != null) {
                        scope.launch {
                            val extra = org.json.JSONObject().apply {
                                if (joinPassword != null) put("password", joinPassword)
                                if (description != null) put("description", description)
                            }.toString()
                            ChatService.pendingTxs.trackTx(txId, PendingTxEntity.ACTION_CREATE_GROUP, name, extra)
                        }
                    }
                    onResult?.invoke(true, null, null)
                }
            }
        )
    }

    fun joinGroup(
        groupId: String,
        joinPassword: String? = null,
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        val args = mutableMapOf<String, Any?>("group_id" to groupId)
        if (!joinPassword.isNullOrEmpty()) {
            args["join_password"] = joinPassword
        }

        ShaderInvoker.tx("user", "join_group", args,
            callback = { result ->
                if (result.containsKey("error")) {
                    onResult?.invoke(false, com.privimemobile.protocol.Helpers.extractError(result))
                } else {
                    val txId = result["txid"]?.toString() ?: result["txId"]?.toString()
                    if (txId != null) {
                        scope.launch {
                            ChatService.pendingTxs.trackTx(txId, PendingTxEntity.ACTION_JOIN_GROUP, groupId)
                        }
                    }
                    onResult?.invoke(true, null)
                }
            }
        )
    }

    /** Admin adds another user to the group. Uses join_group with target_handle. */
    /**
     * Send a group invite to a user via SBBS DM.
     * The target user receives the invite and can accept (calls joinGroup themselves).
     */
    suspend fun sendGroupInvite(groupId: String, targetHandle: String): Boolean {
        val state = db.chatStateDao().get() ?: return false
        val myHandle = state.myHandle ?: return false
        val group = db.groupDao().findByGroupId(groupId) ?: return false

        // Resolve target's wallet ID for SBBS
        val contact = ChatService.contacts.resolveHandle(targetHandle)
        val walletId = contact?.walletId
        if (walletId.isNullOrEmpty()) {
            Log.w(TAG, "Cannot invite @$targetHandle — no wallet ID")
            return false
        }

        val payload = mutableMapOf<String, Any?>(
            "v" to 1,
            "t" to "group_invite",
            "ts" to System.currentTimeMillis() / 1000,
            "from" to myHandle,
            "to" to targetHandle,
            "dn" to (state.myDisplayName ?: ""),
            "invite_group_id" to groupId,
            "group_name" to group.name,
            "member_count" to group.memberCount,
        )
        // Include join password for private groups
        if (!group.joinPassword.isNullOrEmpty()) {
            payload["join_password"] = group.joinPassword
        }
        ChatService.sbbs.sendWithRetry(walletId, payload)
        Log.d(TAG, "Sent group invite for '${ group.name}' to @$targetHandle")
        return true
    }

    fun leaveGroup(
        groupId: String,
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        ShaderInvoker.tx("user", "leave_group",
            mapOf("group_id" to groupId),
            callback = { result ->
                if (result.containsKey("error")) {
                    onResult?.invoke(false, com.privimemobile.protocol.Helpers.extractError(result))
                } else {
                    val txId = result["txid"]?.toString() ?: result["txId"]?.toString()
                    if (txId != null) {
                        scope.launch {
                            ChatService.pendingTxs.trackTx(txId, PendingTxEntity.ACTION_LEAVE_GROUP, groupId)
                        }
                    }
                    onResult?.invoke(true, null)
                }
            }
        )
    }

    fun removeMember(
        groupId: String,
        targetHandle: String,
        ban: Boolean = false,
        isUnban: Boolean = false,
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        ShaderInvoker.tx("user", "remove_member",
            mapOf(
                "group_id" to groupId,
                "target_handle" to targetHandle,
                "ban" to if (ban) 1 else 0,
            ),
            callback = { result ->
                if (result.containsKey("error")) {
                    onResult?.invoke(false, com.privimemobile.protocol.Helpers.extractError(result))
                } else {
                    val txId = result["txid"]?.toString() ?: result["txId"]?.toString()
                    if (txId != null) {
                        scope.launch {
                            val extra = when {
                                ban -> "ban:$targetHandle"
                                isUnban -> "unban:$targetHandle"
                                else -> targetHandle
                            }
                            ChatService.pendingTxs.trackTx(txId, PendingTxEntity.ACTION_REMOVE_MEMBER, groupId, extra)
                        }
                    }
                    scope.launch {
                        delay(3000)
                        refreshGroupMembers(groupId)
                    }
                    onResult?.invoke(true, null)
                }
            }
        )
    }

    fun setMemberRole(
        groupId: String,
        targetHandle: String,
        newRole: Int,
        permissions: Int = 0,
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        ShaderInvoker.tx("user", "set_member_role",
            mapOf(
                "group_id" to groupId,
                "target_handle" to targetHandle,
                "new_role" to newRole,
                "permissions" to permissions,
            ),
            callback = { result ->
                if (result.containsKey("error")) {
                    onResult?.invoke(false, com.privimemobile.protocol.Helpers.extractError(result))
                } else {
                    val txId = result["txid"]?.toString() ?: result["txId"]?.toString()
                    if (txId != null) {
                        scope.launch {
                            ChatService.pendingTxs.trackTx(txId, PendingTxEntity.ACTION_SET_ROLE, groupId, targetHandle)
                        }
                    }
                    scope.launch {
                        delay(3000)
                        refreshGroupMembers(groupId)
                    }
                    onResult?.invoke(true, null)
                }
            }
        )
    }

    fun updateGroupInfo(
        groupId: String,
        name: String? = null,
        description: String? = null,
        isPublic: Int? = null,       // null = no change, 0 = private, 1 = public
        requireApproval: Int? = null, // null = no change
        defaultPermissions: Int = 0,  // 0 = no change
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        val args = mutableMapOf<String, Any?>("group_id" to groupId)
        if (name != null) args["name"] = name
        if (description != null) args["description"] = description
        if (isPublic != null) args["is_public"] = isPublic
        if (requireApproval != null) args["require_approval"] = requireApproval
        if (defaultPermissions > 0) args["default_permissions"] = defaultPermissions

        ShaderInvoker.tx("user", "update_group_info", args,
            callback = { result ->
                if (result.containsKey("error")) {
                    onResult?.invoke(false, com.privimemobile.protocol.Helpers.extractError(result))
                } else {
                    val txId = result["txid"]?.toString() ?: result["txId"]?.toString()
                    if (txId != null) {
                        scope.launch {
                            // Store name in extraData so onTxConfirmed can broadcast it
                            val extra = if (name != null) "name:$name" else null
                            ChatService.pendingTxs.trackTx(txId, PendingTxEntity.ACTION_UPDATE_GROUP_INFO, groupId, extra)
                        }
                    }
                    onResult?.invoke(true, null)
                }
            }
        )
    }

    fun transferOwnership(
        groupId: String,
        newCreatorHandle: String,
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        ShaderInvoker.tx("user", "transfer_ownership",
            mapOf(
                "group_id" to groupId,
                "new_creator" to newCreatorHandle,
            ),
            callback = { result ->
                if (result.containsKey("error")) {
                    onResult?.invoke(false, com.privimemobile.protocol.Helpers.extractError(result))
                } else {
                    val txId = result["txid"]?.toString() ?: result["txId"]?.toString()
                    if (txId != null) {
                        scope.launch {
                            ChatService.pendingTxs.trackTx(txId, PendingTxEntity.ACTION_TRANSFER_OWNERSHIP, groupId, newCreatorHandle)
                        }
                    }
                    scope.launch {
                        delay(3000)
                        refreshGroupInfo(groupId)
                        refreshGroupMembers(groupId)
                    }
                    onResult?.invoke(true, null)
                }
            }
        )
    }

    fun deleteGroup(
        groupId: String,
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        ShaderInvoker.tx("user", "delete_group",
            mapOf("group_id" to groupId),
            callback = { result ->
                if (result.containsKey("error")) {
                    onResult?.invoke(false, com.privimemobile.protocol.Helpers.extractError(result))
                } else {
                    val txId = result["txid"]?.toString() ?: result["txId"]?.toString()
                    if (txId != null) {
                        scope.launch {
                            ChatService.pendingTxs.trackTx(txId, PendingTxEntity.ACTION_DELETE_GROUP, groupId)
                        }
                    }
                    onResult?.invoke(true, null)
                }
            }
        )
    }

    // ========================================================================
    // View methods (read contract state, no TX)
    // ========================================================================

    /**
     * Refresh all groups the caller is a member of.
     * Queries contract, updates local DB.
     */
    suspend fun refreshMyGroups() {
        try {
            val result = ShaderInvoker.invokeAsync("user", "list_my_groups")
            val groups = result["groups"] as? List<*> ?: return

            for (item in groups) {
                val g = item as? Map<*, *> ?: continue
                val groupId = g["group_id"] as? String ?: continue
                val name = Helpers.fixBvmUtf8(g["name"] as? String) ?: continue
                val memberCount = (g["member_count"] as? Number)?.toInt() ?: 0
                val role = (g["role"] as? Number)?.toInt() ?: 0
                val isPublic = (g["is_public"] as? Number)?.toInt() ?: 0

                val allGroups = db.groupDao().getAllGroups()
                Log.d(TAG, "DB has ${allGroups.size} groups: ${allGroups.map { "${it.groupId.take(8)}...(pinned=${it.pinned},muted=${it.muted})" }}")
                val existing = db.groupDao().findByGroupId(groupId)
                Log.d(TAG, "Group ${groupId.take(8)}... findByGroupId: existing=${existing != null}, queryId='$groupId' len=${groupId.length}")
                if (existing != null) {
                    Log.d(TAG, "Group $groupId existing: pinned=${existing.pinned} muted=${existing.muted}")
                    // Only update if something actually changed (avoid unnecessary Room Flow emissions)
                    if (existing.name != name || existing.memberCount != memberCount ||
                        existing.myRole != role || existing.isPublic != (isPublic == 1)) {
                        db.groupDao().updateGroup(existing.copy(
                            name = name,
                            memberCount = memberCount,
                            myRole = role,
                            isPublic = isPublic == 1,
                        ))
                    }
                } else {
                    // Check if conversation exists with messages (reinstall case)
                    val convKey = "g_${groupId.take(16)}"
                    val existingConv = db.conversationDao().findByKey(convKey)
                    var lastPreview: String? = null
                    var lastTs: Long = 0
                    if (existingConv != null) {
                        // Restore preview from latest message
                        val lastMsg = db.messageDao().getLatestMessage(existingConv.id)
                        if (lastMsg != null) {
                            lastPreview = lastMsg.text?.take(60)
                            lastTs = lastMsg.timestamp
                        }
                    }

                    db.groupDao().insertGroup(GroupEntity(
                        groupId = groupId,
                        name = name,
                        creatorHandle = "",
                        memberCount = memberCount,
                        myRole = role,
                        isPublic = isPublic == 1,
                        lastMessagePreview = lastPreview,
                        lastMessageTs = lastTs,
                    ))
                    // Fetch full details for new group
                    refreshGroupInfo(groupId)
                    refreshGroupMembers(groupId)
                }
                // Ensure a ConversationEntity exists for this group's messages
                getOrCreateGroupConversation(groupId, name)
            }

            // Clean up local groups that no longer exist on-chain
            // Only run cleanup if we got at least 2 groups (protects against partial shader results)
            // No orphan cleanup — partial shader results can cause false positives
            // Deleted groups are handled by group_deleted SBBS + PendingTxManager

            Log.d(TAG, "Refreshed ${groups.size} groups")
        } catch (e: Exception) {
            Log.e(TAG, "refreshMyGroups error: ${e.message}")
        }
    }

    /**
     * Refresh detailed info for a single group.
     */
    suspend fun refreshGroupInfo(groupId: String) {
        try {
            val result = ShaderInvoker.invokeAsync("user", "view_group",
                mapOf("group_id" to groupId))
            if (result.containsKey("error")) return

            val name = Helpers.fixBvmUtf8(result["name"] as? String) ?: return
            val creator = result["creator"] as? String ?: ""
            val memberCount = (result["member_count"] as? Number)?.toInt() ?: 0
            val isPublic = (result["is_public"] as? Number)?.toInt() ?: 0
            val requireApproval = (result["require_approval"] as? Number)?.toInt() ?: 0
            val maxMembers = (result["max_members"] as? Number)?.toInt() ?: 200
            val defaultPerms = (result["default_permissions"] as? Number)?.toInt() ?: 3
            val createdHeight = (result["created_height"] as? Number)?.toLong() ?: 0
            val avatarHash = result["avatar_hash"] as? String

            val existing = db.groupDao().findByGroupId(groupId)
            if (existing != null) {
                db.groupDao().updateGroup(existing.copy(
                    name = name,
                    creatorHandle = creator,
                    memberCount = memberCount,
                    isPublic = isPublic == 1,
                    requireApproval = requireApproval == 1,
                    maxMembers = maxMembers,
                    defaultPermissions = defaultPerms,
                    createdHeight = createdHeight,
                    // Preserve local avatar/description if contract returns null (SBBS-only fields)
                    avatarHash = avatarHash ?: existing.avatarHash,
                    description = existing.description,
                ))
            } else {
                db.groupDao().insertGroup(GroupEntity(
                    groupId = groupId,
                    name = name,
                    creatorHandle = creator,
                    memberCount = memberCount,
                    isPublic = isPublic == 1,
                    requireApproval = requireApproval == 1,
                    maxMembers = maxMembers,
                    defaultPermissions = defaultPerms,
                    createdHeight = createdHeight,
                    avatarHash = avatarHash,
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshGroupInfo($groupId) error: ${e.message}")
        }
    }

    /**
     * Refresh member list for a group.
     * Also resolves wallet_ids for SBBS messaging.
     */
    suspend fun refreshGroupMembers(groupId: String) {
        try {
            val result = ShaderInvoker.invokeAsync("user", "list_members",
                mapOf("group_id" to groupId))
            val members = result["members"] as? List<*> ?: return

            val state = db.chatStateDao().get()
            val myHandle = state?.myHandle

            for (item in members) {
                val m = item as? Map<*, *> ?: continue
                val handle = m["handle"] as? String ?: continue
                val role = (m["role"] as? Number)?.toInt() ?: 0
                val permissions = (m["permissions"] as? Number)?.toInt() ?: 0
                val joinedHeight = (m["joined_height"] as? Number)?.toLong() ?: 0

                // Resolve wallet_id from contact DB or contract (include banned for admin visibility)
                val contact = db.contactDao().findByHandle(handle)
                val walletId = contact?.walletId

                db.groupDao().insertMember(GroupMemberEntity(
                    groupId = groupId,
                    handle = handle,
                    displayName = contact?.displayName,
                    role = role,
                    permissions = permissions,
                    walletId = walletId,
                    joinedHeight = joinedHeight,
                ))

                // Update my role if this is me
                if (handle == myHandle) {
                    db.groupDao().updateMyRole(groupId, role, permissions)
                }
            }

            // Resolve/refresh wallet_ids for ALL members (catches address changes after wallet restore)
            scope.launch {
                val allMembers = db.groupDao().getActiveMembers(groupId)
                for (member in allMembers) {
                    if (member.handle == myHandle) continue // skip self
                    try {
                        val resolved = ChatService.contacts.resolveHandle(member.handle)
                        if (resolved?.walletId != null && resolved.walletId != member.walletId) {
                            db.groupDao().updateMemberWalletId(groupId, member.handle, resolved.walletId)
                            Log.d(TAG, "Updated wallet_id for @${member.handle} in group $groupId")
                        }
                    } catch (_: Exception) {}
                    delay(200)
                }
            }

            Log.d(TAG, "Refreshed ${members.size} members for group $groupId")
        } catch (e: Exception) {
            Log.e(TAG, "refreshGroupMembers($groupId) error: ${e.message}")
        }
    }

    // ========================================================================
    // Group SBBS messaging
    // ========================================================================

    /**
     * Send a message to all group members via SBBS.
     * Each member gets an individual SBBS send (200ms spacing).
     * The message includes group_id so the receiver routes it correctly.
     */
    suspend fun sendGroupMessage(
        groupId: String,
        text: String,
        replyText: String? = null,
        ttl: Int = 0,
        fwdFrom: String? = null,
        fwdTs: Long = 0,
    ) {
        val state = db.chatStateDao().get() ?: return
        val myHandle = state.myHandle ?: return
        val myDisplayName = state.myDisplayName

        val group = db.groupDao().findByGroupId(groupId) ?: return

        // Block sending if banned
        val myMember = db.groupDao().findMember(groupId, myHandle)
        if (myMember?.role == 3) {
            Log.w(TAG, "Cannot send — banned from group $groupId")
            return
        }

        val convId = getOrCreateGroupConversation(groupId, group.name)

        // Get all member wallet_ids (exclude self)
        val memberWalletIds = db.groupDao().getMemberWalletIds(groupId, myHandle)
            .filterNotNull()
            .filter { it.isNotEmpty() }

        if (memberWalletIds.isEmpty()) {
            Log.w(TAG, "No member wallet_ids for group $groupId — need to refresh members")
            refreshGroupMembers(groupId)
            return
        }

        val ts = System.currentTimeMillis() / 1000

        // Build SBBS payload
        val payload = mutableMapOf<String, Any?>(
            "v" to 1,
            "t" to "group_msg",
            "ts" to ts,
            "from" to myHandle,
            "group_id" to groupId,
            "msg" to text,
        )
        if (!myDisplayName.isNullOrEmpty()) payload["dn"] = myDisplayName
        if (replyText != null) payload["reply"] = replyText
        if (ttl > 0) payload["ttl"] = ttl
        if (fwdFrom != null) payload["fwd_from"] = fwdFrom
        if (fwdTs > 0) payload["fwd_ts"] = fwdTs

        val expiresAt = if (ttl > 0) ts + ttl else 0L

        // Insert own message into DB immediately (optimistic)
        val entity = com.privimemobile.chat.db.entities.MessageEntity(
            conversationId = convId,
            timestamp = ts,
            senderHandle = myHandle,
            text = text,
            type = "group_msg",
            sent = true,
            fwdFrom = fwdFrom,
            fwdTs = fwdTs,
            sbbsDedupKey = "$ts:${text.hashCode().toString(16)}:$myHandle:$groupId".hashCode().toString(16),
            replyText = replyText,
            expiresAt = expiresAt,
        )
        db.messageDao().insert(entity)

        // Update group preview
        db.groupDao().updateLastMessage(groupId, ts, "You: ${text.take(40)}")

        // Send to each member with 200ms spacing
        for (walletId in memberWalletIds) {
            try {
                ChatService.sbbs.sendOnce(walletId, payload)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send group msg to $walletId: ${e.message}")
            }
            delay(200)
        }

        Log.d(TAG, "Sent group msg to ${memberWalletIds.size} members in $groupId")
    }

    /**
     * Send an arbitrary SBBS payload to all group members.
     * Used for reactions, edits, deletes, polls, file messages, etc.
     * Caller builds the payload; this method adds group_id + from and broadcasts.
     */
    suspend fun sendGroupPayload(groupId: String, payload: Map<String, Any?>) {
        val state = db.chatStateDao().get() ?: return
        val myHandle = state.myHandle ?: return

        // Block sending if banned
        val myMember = db.groupDao().findMember(groupId, myHandle)
        if (myMember?.role == 3) { Log.w(TAG, "Cannot send — banned from group $groupId"); return }

        val memberWalletIds = db.groupDao().getMemberWalletIds(groupId, myHandle)
            .filterNotNull()
            .filter { it.isNotEmpty() }

        val fullPayload = mutableMapOf<String, Any?>()
        fullPayload.putAll(payload)
        fullPayload["group_id"] = groupId
        fullPayload["from"] = myHandle

        for (walletId in memberWalletIds) {
            try {
                ChatService.sbbs.sendOnce(walletId, fullPayload)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send group payload to $walletId: ${e.message}")
            }
            delay(200)
        }
        Log.d(TAG, "Sent group payload (${payload["t"]}) to ${memberWalletIds.size} members in $groupId")
    }

    // Group typing throttle — max one per group per 5 seconds
    private val lastGroupTypingSent = mutableMapOf<String, Long>()

    /** Send typing indicator to all group members (throttled). */
    fun sendGroupTyping(groupId: String) {
        val now = System.currentTimeMillis()
        val last = lastGroupTypingSent[groupId] ?: 0
        if (now - last < 5000) return // 5s throttle
        lastGroupTypingSent[groupId] = now

        scope.launch {
            val state = db.chatStateDao().get() ?: return@launch
            val myHandle = state.myHandle ?: return@launch
            val memberWalletIds = db.groupDao().getMemberWalletIds(groupId, myHandle)
                .filterNotNull().filter { it.isNotEmpty() }

            val payload = mapOf(
                "v" to 1, "t" to "typing",
                "from" to myHandle,
                "group_id" to groupId,
                "ts" to (now / 1000),
            )
            for (walletId in memberWalletIds) {
                try { ChatService.sbbs.sendOnce(walletId, payload) } catch (_: Exception) {}
            }
        }
    }

    /**
     * Request avatars for group members whose profile pictures are missing locally.
     * Sends avatar_request to each member without a cached avatar file.
     */
    suspend fun requestMemberAvatars(groupId: String) {
        val state = db.chatStateDao().get() ?: return
        val myHandle = state.myHandle ?: return
        val filesDir = com.privimemobile.chat.transport.IpfsTransport.filesDir ?: return
        val members = db.groupDao().getActiveMembers(groupId)

        for (member in members) {
            if (member.handle == myHandle) continue
            val avatarFile = java.io.File(filesDir, "avatars/${member.handle}.webp")
            if (avatarFile.exists()) continue // already have it
            val walletId = member.walletId ?: continue

            val payload = mapOf(
                "v" to 1, "t" to "avatar_request",
                "from" to myHandle,
            )
            try {
                ChatService.sbbs.sendOnce(walletId, payload)
                Log.d(TAG, "Requested avatar for @${member.handle} in group $groupId")
            } catch (_: Exception) {}
            delay(200)
        }
    }

    /**
     * Request group avatar + description from another member if missing locally.
     * Same pattern as user profile picture avatar_request/avatar_response.
     */
    suspend fun requestGroupInfoIfNeeded(groupId: String) {
        val group = db.groupDao().findByGroupId(groupId) ?: return
        val state = db.chatStateDao().get() ?: return
        val myHandle = state.myHandle ?: return

        // Check if we're missing avatar or description
        val filesDir = com.privimemobile.chat.transport.IpfsTransport.filesDir
        val avatarFile = if (filesDir != null) java.io.File(filesDir, "group_avatars/$groupId.webp") else null
        val hasAvatarLocally = avatarFile?.exists() == true
        val hasDescLocally = !group.description.isNullOrEmpty()

        if (hasAvatarLocally && hasDescLocally) return // have everything

        // Find a member to ask (prefer creator, then any member with a wallet ID)
        val members = db.groupDao().getMemberWalletIds(groupId, myHandle)
            .filterNotNull()
            .filter { it.isNotEmpty() }
        if (members.isEmpty()) return

        val targetWalletId = members.first()

        // Get my wallet ID for the response to come back to
        val myWalletId = state.myWalletId ?: return

        val request = mapOf(
            "v" to 1,
            "t" to "group_info_request",
            "ts" to System.currentTimeMillis() / 1000,
            "from" to myHandle,
            "group_id" to groupId,
            "requester_wallet_id" to myWalletId,
        )

        try {
            ChatService.sbbs.sendOnce(targetWalletId, request)
            Log.d(TAG, "Sent group_info_request for $groupId (hasAvatar=$hasAvatarLocally, hasDesc=$hasDescLocally)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send group_info_request: ${e.message}")
        }
    }

    /**
     * Send a group service notification to all members.
     */
    suspend fun sendGroupService(groupId: String, action: String, target: String? = null) {
        val state = db.chatStateDao().get() ?: return
        val myHandle = state.myHandle ?: return

        val ts = System.currentTimeMillis() / 1000

        // Insert service message locally for the sender (SBBS excludes self)
        val group = db.groupDao().findByGroupId(groupId)
        if (group != null) {
            val convId = getOrCreateGroupConversation(groupId, group.name)
            val serviceText = when (action) {
                "joined" -> if (target != null) "You invited @$target to the group" else "You joined the group"
                "left" -> "You left the group"
                "kicked" -> "You removed @$target"
                "banned" -> "You banned @$target"
                "unbanned" -> "You unbanned @$target"
                "promoted" -> "You promoted @$target to admin"
                "demoted" -> "You demoted @$target to member"
                "ownership_transferred" -> "You transferred ownership to @$target"
                "group_deleted" -> "You deleted the group"
                else -> "You $action" + if (target != null) " @$target" else ""
            }
            val dedupEpoch = ts / 120 // 2-minute window — prevents spam but allows repeated actions over time
            val dedupKey = "svc:$action:${target ?: myHandle}:$groupId:$dedupEpoch".hashCode().toString(16)
            val entity = com.privimemobile.chat.db.entities.MessageEntity(
                conversationId = convId,
                timestamp = ts,
                senderHandle = myHandle,
                text = serviceText,
                type = "group_service",
                sent = true,
                sbbsDedupKey = dedupKey,
            )
            val insertedId = db.messageDao().insert(entity)
            if (insertedId != -1L) {
                db.groupDao().updateLastMessage(groupId, ts, serviceText)
            }

        }

        // Get all active member wallet IDs (excludes banned)
        val memberWalletIds = db.groupDao().getMemberWalletIds(groupId, myHandle)
            .filterNotNull()
            .filter { it.isNotEmpty() }
            .toMutableList()

        // For kick/ban: explicitly include the target's wallet ID (they need to receive the notification)
        if ((action == "kicked" || action == "banned") && target != null) {
            val targetWalletId = db.groupDao().getMemberWalletId(groupId, target)
                ?: db.contactDao().findByHandle(target)?.walletId
            if (targetWalletId != null && targetWalletId !in memberWalletIds) {
                memberWalletIds.add(0, targetWalletId) // send to target first
            }
        }

        val payload = mutableMapOf<String, Any?>(
            "v" to 1,
            "t" to "group_service",
            "ts" to ts,
            "from" to myHandle,
            "group_id" to groupId,
            "action" to action,
        )
        if (target != null) payload["target"] = target

        for (walletId in memberWalletIds) {
            try {
                ChatService.sbbs.sendOnce(walletId, payload)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send group service to $walletId: ${e.message}")
            }
            delay(200)
        }

        // Update local member list AFTER SBBS sent (so kicked/banned user received the message)
        if (target != null && group != null) {
            when (action) {
                "kicked" -> db.groupDao().removeMember(groupId, target)
                "banned" -> db.groupDao().updateMemberRole(groupId, target, 3, 0) // role 3 = Banned
                "unbanned" -> db.groupDao().removeMember(groupId, target) // ban lifted, user can rejoin
                "promoted" -> db.groupDao().updateMemberRole(groupId, target, 1, 0)
                "demoted" -> db.groupDao().updateMemberRole(groupId, target, 0, 0)
            }
        }
    }

    /**
     * Search public groups by name prefix.
     */
    suspend fun searchGroups(prefix: String): List<Map<String, Any?>> {
        return try {
            val result = ShaderInvoker.invokeAsync("user", "search_groups",
                mapOf("prefix" to prefix))
            val results = result["results"] as? List<*> ?: return emptyList()
            results.mapNotNull { item ->
                val g = item as? Map<*, *> ?: return@mapNotNull null
                mapOf(
                    "group_id" to (g["group_id"] as? String),
                    "name" to Helpers.fixBvmUtf8(g["name"] as? String),
                    "creator" to (g["creator"] as? String),
                    "member_count" to (g["member_count"] as? Number)?.toInt(),
                    "require_approval" to (g["require_approval"] as? Number)?.toInt(),
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "searchGroups error: ${e.message}")
            emptyList()
        }
    }
}
