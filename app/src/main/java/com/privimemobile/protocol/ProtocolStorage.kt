package com.privimemobile.protocol

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persistent storage for protocol state — scoped per user handle.
 *
 * Ports storage.ts to Kotlin.
 * Uses SharedPreferences (replacing AsyncStorage).
 * Scoped by handle to prevent cross-wallet data leaks in multi-user scenarios.
 */
object ProtocolStorage {
    private const val TAG = "ProtocolStorage"
    private const val CONV_VERSION = 2

    private var userScope = "privime_default"
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences("privimw_protocol", Context.MODE_PRIVATE)
    }

    /** Set storage scope for current user. Call when identity changes. */
    fun setUserScope(handle: String) {
        userScope = "privime_${handle.lowercase()}"
    }

    private fun key(suffix: String) = "${userScope}_$suffix"

    // === Save methods ===

    fun saveConversations(conversations: Map<String, List<ChatMessage>>) {
        val obj = JSONObject()
        conversations.forEach { (k, msgs) ->
            val arr = JSONArray()
            msgs.forEach { msg ->
                arr.put(JSONObject().apply {
                    put("id", msg.id)
                    put("from", msg.from)
                    put("to", msg.to)
                    put("text", msg.text)
                    put("timestamp", msg.timestamp)
                    put("sent", msg.sent)
                    put("displayName", msg.displayName)
                    put("fileHash", msg.fileHash)
                    put("fileName", msg.fileName)
                    put("fileSize", msg.fileSize)
                    put("type", msg.type)
                })
            }
            obj.put(k, arr)
        }
        prefs?.edit()?.putString(key("conv"), obj.toString())?.apply()
    }

    fun saveContacts(contacts: Map<String, Contact>) {
        val obj = JSONObject()
        contacts.forEach { (k, c) ->
            obj.put(k, JSONObject().apply {
                put("handle", c.handle)
                put("displayName", c.displayName)
                put("walletId", c.walletId)
            })
        }
        prefs?.edit()?.putString(key("contacts"), obj.toString())?.apply()
    }

    fun saveDeletedConvs(deleted: Map<String, Long>) {
        val obj = JSONObject()
        deleted.forEach { (k, v) -> obj.put(k, v) }
        prefs?.edit()?.putString(key("deleted"), obj.toString())?.apply()
    }

    fun saveUnreadCounts(unread: Map<String, Int>) {
        val obj = JSONObject()
        unread.forEach { (k, v) -> obj.put(k, v) }
        prefs?.edit()?.putString(key("unread"), obj.toString())?.apply()
    }

    fun saveBlockedUsers(blocked: Set<String>) {
        val arr = JSONArray()
        blocked.forEach { arr.put(it) }
        prefs?.edit()?.putString(key("blocked"), arr.toString())?.apply()
    }

    // === Load methods ===

    fun loadConversations(): Map<String, List<ChatMessage>> {
        val raw = prefs?.getString(key("conv"), null) ?: return emptyMap()
        return try {
            // Check version
            val version = prefs?.getString(key("convver"), "1")?.toIntOrNull() ?: 1
            if (version < CONV_VERSION) {
                prefs?.edit()
                    ?.remove(key("conv"))?.remove(key("contacts"))
                    ?.putString(key("convver"), CONV_VERSION.toString())
                    ?.apply()
                return emptyMap()
            }
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, List<ChatMessage>>()
            obj.keys().forEach { k ->
                val arr = obj.optJSONArray(k) ?: return@forEach
                val msgs = (0 until arr.length()).mapNotNull { i ->
                    val m = arr.optJSONObject(i) ?: return@mapNotNull null
                    ChatMessage(
                        id = m.optString("id"),
                        from = m.optString("from"),
                        to = m.optString("to"),
                        text = m.optString("text"),
                        timestamp = m.optLong("timestamp"),
                        sent = m.optBoolean("sent"),
                        displayName = m.optString("displayName"),
                        fileHash = m.optString("fileHash"),
                        fileName = m.optString("fileName"),
                        fileSize = m.optLong("fileSize"),
                        type = m.optString("type", "dm"),
                    )
                }
                result[k] = msgs
            }
            result
        } catch (_: Exception) { emptyMap() }
    }

    fun loadContacts(): Map<String, Contact> {
        val raw = prefs?.getString(key("contacts"), null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, Contact>()
            obj.keys().forEach { k ->
                val c = obj.optJSONObject(k) ?: return@forEach
                result[k] = Contact(
                    handle = c.optString("handle"),
                    displayName = c.optString("displayName"),
                    walletId = c.optString("walletId"),
                )
            }
            result
        } catch (_: Exception) { emptyMap() }
    }

    fun loadDeletedConvs(): Map<String, Long> {
        val raw = prefs?.getString(key("deleted"), null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, Long>()
            obj.keys().forEach { k -> result[k] = obj.optLong(k) }
            result
        } catch (_: Exception) { emptyMap() }
    }

    fun loadUnreadCounts(): Map<String, Int> {
        val raw = prefs?.getString(key("unread"), null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = mutableMapOf<String, Int>()
            obj.keys().forEach { k -> result[k] = obj.optInt(k) }
            result
        } catch (_: Exception) { emptyMap() }
    }

    fun loadBlockedUsers(): Set<String> {
        val raw = prefs?.getString(key("blocked"), null) ?: return emptySet()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.optString(it) }.toSet()
        } catch (_: Exception) { emptySet() }
    }

    // === Contract start timestamp ===

    fun getContractStartTs(): Long {
        return prefs?.getString(key("contractStartTs"), "0")?.toLongOrNull() ?: 0
    }

    fun setContractStartTs(ts: Long) {
        prefs?.edit()?.putString(key("contractStartTs"), ts.toString())?.apply()
    }

    /** Save all protocol state at once. */
    fun saveAll(
        conversations: Map<String, List<ChatMessage>>,
        contacts: Map<String, Contact>,
        deletedConvs: Map<String, Long>,
        unreadCounts: Map<String, Int>,
    ) {
        saveConversations(conversations)
        saveContacts(contacts)
        saveDeletedConvs(deletedConvs)
        saveUnreadCounts(unreadCounts)
    }
}
