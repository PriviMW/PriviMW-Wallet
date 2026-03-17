package com.privimemobile.chat.db.entities

import androidx.room.*

@Entity(tableName = "chat_state")
data class ChatStateEntity(
    @PrimaryKey val id: Int = 1,  // singleton row
    @ColumnInfo(name = "contract_start_ts") val contractStartTs: Long = 0,
    @ColumnInfo(name = "my_handle") val myHandle: String? = null,
    @ColumnInfo(name = "my_wallet_id") val myWalletId: String? = null,
    @ColumnInfo(name = "my_display_name") val myDisplayName: String? = null,
    @ColumnInfo(name = "my_avatar_cid") val myAvatarCid: String? = null,
    @ColumnInfo(name = "my_registered_height") val myRegisteredHeight: Long = 0,
    @ColumnInfo(name = "registration_fee") val registrationFee: Long = 0,
)
