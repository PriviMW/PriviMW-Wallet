package com.privimemobile.chat.group

import android.util.Log
import com.privimemobile.chat.ChatService
import com.privimemobile.chat.db.ChatDatabase
import com.privimemobile.chat.db.entities.GroupEntity
import com.privimemobile.chat.db.entities.GroupMemberEntity
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
    // TX methods (invoke contract, generate kernel)
    // ========================================================================

    fun createGroup(
        name: String,
        isPublic: Boolean = false,
        requireApproval: Boolean = false,
        maxMembers: Int = 0,
        defaultPermissions: Int = 0,
        onResult: ((success: Boolean, groupId: String?, error: String?) -> Unit)? = null,
    ) {
        ShaderInvoker.tx("user", "create_group",
            mapOf(
                "name" to name,
                "is_public" to if (isPublic) 1 else 0,
                "require_approval" to if (requireApproval) 1 else 0,
                "max_members" to maxMembers,
                "default_permissions" to defaultPermissions,
            ),
            callback = { result ->
                if (result.containsKey("error")) {
                    onResult?.invoke(false, null, result["error"]?.toString())
                } else {
                    Log.d(TAG, "Create group TX submitted: $name")
                    // Refresh groups after TX confirms
                    scope.launch {
                        delay(5000)
                        refreshMyGroups()
                    }
                    onResult?.invoke(true, null, null)
                }
            }
        )
    }

    fun joinGroup(
        groupId: String,
        inviteSecret: String? = null,
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        val args = mutableMapOf<String, Any?>("group_id" to groupId)
        if (!inviteSecret.isNullOrEmpty()) {
            args["invite_secret"] = inviteSecret
        }

        ShaderInvoker.tx("user", "join_group", args,
            callback = { result ->
                if (result.containsKey("error")) {
                    onResult?.invoke(false, result["error"]?.toString())
                } else {
                    scope.launch {
                        delay(5000)
                        refreshMyGroups()
                    }
                    onResult?.invoke(true, null)
                }
            }
        )
    }

    fun leaveGroup(
        groupId: String,
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        ShaderInvoker.tx("user", "leave_group",
            mapOf("group_id" to groupId),
            callback = { result ->
                if (result.containsKey("error")) {
                    onResult?.invoke(false, result["error"]?.toString())
                } else {
                    scope.launch {
                        delay(5000)
                        db.groupDao().deleteByGroupId(groupId)
                        db.groupDao().removeAllMembers(groupId)
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
                    onResult?.invoke(false, result["error"]?.toString())
                } else {
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
                    onResult?.invoke(false, result["error"]?.toString())
                } else {
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
        isPublic: Int? = null,       // null = no change, 0 = private, 1 = public
        requireApproval: Int? = null, // null = no change
        defaultPermissions: Int = 0,  // 0 = no change
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        val args = mutableMapOf<String, Any?>("group_id" to groupId)
        if (name != null) args["name"] = name
        if (isPublic != null) args["is_public"] = isPublic
        if (requireApproval != null) args["require_approval"] = requireApproval
        if (defaultPermissions > 0) args["default_permissions"] = defaultPermissions

        ShaderInvoker.tx("user", "update_group_info", args,
            callback = { result ->
                if (result.containsKey("error")) {
                    onResult?.invoke(false, result["error"]?.toString())
                } else {
                    scope.launch {
                        delay(3000)
                        refreshGroupInfo(groupId)
                    }
                    onResult?.invoke(true, null)
                }
            }
        )
    }

    fun approveJoinRequest(
        groupId: String,
        targetHandle: String,
        approve: Boolean = true,
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        ShaderInvoker.tx("user", "approve_join_request",
            mapOf(
                "group_id" to groupId,
                "target_handle" to targetHandle,
                "approve" to if (approve) 1 else 0,
            ),
            callback = { result ->
                if (result.containsKey("error")) {
                    onResult?.invoke(false, result["error"]?.toString())
                } else {
                    scope.launch {
                        delay(3000)
                        refreshGroupMembers(groupId)
                    }
                    onResult?.invoke(true, null)
                }
            }
        )
    }

    fun setInviteLink(
        groupId: String,
        inviteHash: String,  // SHA256(secret), 64 hex
        expiryHeight: Int = 0,
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        ShaderInvoker.tx("user", "set_invite_link",
            mapOf(
                "group_id" to groupId,
                "invite_hash" to inviteHash,
                "expiry_height" to expiryHeight,
            ),
            callback = { result ->
                if (result.containsKey("error")) {
                    onResult?.invoke(false, result["error"]?.toString())
                } else {
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
                    onResult?.invoke(false, result["error"]?.toString())
                } else {
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

    fun reportMember(
        groupId: String,
        targetHandle: String,
        reason: Int,  // 0=spam, 1=harassment, 2=inappropriate, 3=other
        onResult: ((success: Boolean, error: String?) -> Unit)? = null,
    ) {
        ShaderInvoker.tx("user", "report_member",
            mapOf(
                "group_id" to groupId,
                "target_handle" to targetHandle,
                "reason" to reason,
            ),
            callback = { result ->
                if (result.containsKey("error")) {
                    onResult?.invoke(false, result["error"]?.toString())
                } else {
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
                    onResult?.invoke(false, result["error"]?.toString())
                } else {
                    scope.launch {
                        delay(3000)
                        db.groupDao().deleteByGroupId(groupId)
                        db.groupDao().removeAllMembers(groupId)
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

                val existing = db.groupDao().findByGroupId(groupId)
                if (existing != null) {
                    db.groupDao().updateGroup(existing.copy(
                        name = name,
                        memberCount = memberCount,
                        myRole = role,
                        isPublic = isPublic == 1,
                    ))
                } else {
                    db.groupDao().insertGroup(GroupEntity(
                        groupId = groupId,
                        name = name,
                        creatorHandle = "",
                        memberCount = memberCount,
                        myRole = role,
                        isPublic = isPublic == 1,
                    ))
                    // Fetch full details for new group
                    refreshGroupInfo(groupId)
                    refreshGroupMembers(groupId)
                }
            }

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
                    avatarHash = avatarHash,
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

                // Skip banned members
                if (role == 3) continue

                // Resolve wallet_id from contact DB or contract
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

            // Resolve wallet_ids for members without one
            scope.launch {
                val membersNeedingResolve = db.groupDao().getActiveMembers(groupId)
                    .filter { it.walletId.isNullOrEmpty() }
                for (member in membersNeedingResolve) {
                    try {
                        val resolved = ChatService.contacts.resolveHandle(member.handle)
                        if (resolved?.walletId != null) {
                            db.groupDao().updateMemberWalletId(groupId, member.handle, resolved.walletId)
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
