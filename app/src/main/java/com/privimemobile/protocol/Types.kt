package com.privimemobile.protocol

/**
 * Domain types for the PriviMe protocol.
 * Fully ports beam/types.ts to Kotlin data classes.
 */

/** A registered PriviMe user (resolved from handle or wallet_id). */
data class Contact(
    val handle: String,
    val displayName: String,
    val walletId: String,
    val avatarHash: String = "",
    val lastSeen: Long = 0,
    val resolving: Boolean = false,
)

/** File attachment embedded in a chat message. */
data class FileAttachment(
    val cid: String = "",      // IPFS content ID
    val key: String = "",      // AES key (hex)
    val iv: String = "",       // AES IV (hex)
    val name: String = "",     // filename
    val size: Long = 0,        // file size in bytes
    val mime: String = "",     // MIME type
    val data: String? = null,  // inline base64 data (for small files)
)

/** A chat message (sent or received via SBBS). */
data class ChatMessage(
    val id: String,            // unique: "$ts-$from-$to"
    val from: String,          // sender handle or wallet_id
    val to: String,            // recipient handle or wallet_id
    val text: String,          // message text
    val timestamp: Long,       // unix seconds
    val sent: Boolean,         // true if we sent it
    val displayName: String = "",
    val read: Boolean = false,       // read receipt received (blue ✓✓)
    val delivered: Boolean = false,  // delivery ack received (grey ✓✓)
    val acked: Boolean = false,      // we sent ack for this (for received messages)
    val isTip: Boolean = false,      // is a tip message
    val tipAmount: Long = 0,         // tip amount in groth
    val reply: String? = null,       // quoted message text (for replies)
    val file: FileAttachment? = null, // file attachment
    val type: String = "dm",         // "dm", "file", "tip", "system"
)

/** A conversation (aggregated from messages). */
data class Conversation(
    val handle: String,          // other party's handle
    val displayName: String,
    val lastMessage: String,
    val lastTimestamp: Long,
    val unreadCount: Int = 0,
    val walletId: String = "",
)

/** User's own PriviMe identity. */
data class Identity(
    val handle: String,
    val displayName: String,
    val walletId: String,
    val registered: Boolean = false,
    val registeredHeight: Long = 0,
)

/** Raw SBBS message payload (before parsing). */
data class MessagePayload(
    val v: Int = 0,             // version
    val t: String = "",         // type: "dm", "ack", "file", "tip"
    val msg: String? = null,    // message text
    val ts: Long = 0,           // timestamp
    val from: String? = null,   // sender handle
    val to: String? = null,     // recipient handle
    val dn: String? = null,     // display name
    val amount: Long? = null,   // tip amount
    val reply: String? = null,  // reply text
    val read: Long? = null,     // read receipt timestamp
    val file: Map<String, Any>? = null, // file info
)

/** Handle resolution result from shader. */
data class HandleResult(
    val registered: Boolean = false,
    val handle: String = "",
    val walletId: String = "",
    val displayName: String = "",
    val registeredHeight: Long = 0,
    val error: String? = null,
)

/** Pool info result (registration fee, etc). */
data class PoolResult(
    val registrationFee: Long = 0,
    val error: String? = null,
)
