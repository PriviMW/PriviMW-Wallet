package com.privimemobile.ui.chat.chats

import android.content.Context
import android.widget.Toast
import com.privimemobile.R
import com.privimemobile.chat.ChatService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Orchestrator-side delete/leave after swipe confirmation. */
internal fun performChatsSwipeDelete(
    context: Context,
    scope: CoroutineScope,
    item: ChatListItem,
) {
    if (item.isGroup) {
        val gid = item.group!!.groupId
        ChatService.groups.leaveGroup(gid) { success, error ->
            scope.launch {
                if (!success) {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.chats_leave_failed,
                            error ?: context.getString(R.string.register_transaction_failed),
                        ),
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    val conv = ChatService.db?.conversationDao()?.findByKey("g_${gid.take(16)}")
                    conv?.let {
                        ChatService.db?.messageDao()?.softDeleteByConversation(it.id)
                        ChatService.db?.conversationDao()?.softDelete(it.id)
                    }
                }
            }
        }
    } else {
        scope.launch {
            val conv = item.conv!!
            val cid = conv.id
            val handle = conv.convKey.removePrefix("@")
            ChatService.db?.messageDao()?.softDeleteByConversation(cid)
            ChatService.db?.conversationDao()?.softDelete(cid)
            ChatService.db?.contactDao()?.deleteByHandle(handle)
        }
    }
}

/** Orchestrator-side archive/unarchive after swipe confirmation. */
internal fun performChatsSwipeArchive(scope: CoroutineScope, item: ChatListItem) {
    scope.launch {
        if (item.isGroup) {
            ChatService.db?.groupDao()?.setArchived(item.group!!.groupId, !item.group!!.archived)
        } else {
            ChatService.db?.conversationDao()?.setArchived(item.conv!!.id, !item.conv!!.archived)
        }
    }
}
