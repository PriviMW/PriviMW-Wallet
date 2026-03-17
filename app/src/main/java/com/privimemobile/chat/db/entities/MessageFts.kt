package com.privimemobile.chat.db.entities

import androidx.room.*

@Fts4(contentEntity = MessageEntity::class)
@Entity(tableName = "messages_fts")
data class MessageFts(
    val text: String?,
)
