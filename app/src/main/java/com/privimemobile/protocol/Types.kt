package com.privimemobile.protocol

/**
 * Domain types for the PriviMe protocol.
 * Ports beam/types.ts to Kotlin data classes.
 */

/** A registered PriviMe user (resolved from handle or wallet_id). */
data class Contact(
    val handle: String,
    val displayName: String,
    val walletId: String,
    val avatarHash: String = "",
    val lastSeen: Long = 0,
)

/** A chat message (sent or received via SBBS). */
data class ChatMessage(
    val id: String,         // unique: "$ts-$from-$to"
    val from: String,       // sender handle
    val to: String,         // recipient handle
    val text: String,       // message text
    val timestamp: Long,    // unix seconds
    val sent: Boolean,      // true if we sent it
    val displayName: String = "",
    val fileHash: String = "",   // IPFS hash for file attachments
    val fileName: String = "",
    val fileSize: Long = 0,
    val type: String = "dm",     // "dm" or "system"
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
)
