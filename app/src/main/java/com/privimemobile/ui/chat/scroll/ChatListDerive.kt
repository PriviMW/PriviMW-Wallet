package com.privimemobile.ui.chat.scroll

import com.privimemobile.protocol.ChatMessage

/** Pure list / LazyColumn helpers extracted from ChatScreen. */
object ChatListDerive {

  private const val CLUSTER_MAX_GAP_SEC = 60L

  /** LazyColumn item key — poll items must include [pollData] so vote updates recompose. */
  fun messageItemKey(messageId: String, type: String, pollData: String?): String =
      if (type == "poll") "$messageId:$pollData" else messageId

  fun messageItemKey(message: ChatMessage): String =
      messageItemKey(message.id, message.type, message.pollData)

  /** Date separator between [prev] (older) and [current] using pre-formatted labels from UI. */
  fun shouldShowDateSeparator(previousDateLabel: String?, currentDateLabel: String): Boolean =
      previousDateLabel == null || previousDateLabel != currentDateLabel

  data class ClusterFlags(
      val showDateSep: Boolean,
      val sameAsPrev: Boolean,
      val sameAsNext: Boolean,
      val isFirstInCluster: Boolean,
      val isLastInCluster: Boolean,
      val showTimestamp: Boolean,
  )

  /**
   * Telegram-style bubble grouping for one message in [reversedMessages] (newest at index 0).
   */
  fun computeClusterFlags(
      index: Int,
      reversedMessages: List<ChatMessage>,
      prevDateLabel: String?,
      curDateLabel: String,
      nextDateLabel: String?,
  ): ClusterFlags {
      val msg = reversedMessages[index]
      val prevMsg = if (index < reversedMessages.size - 1) reversedMessages[index + 1] else null
      val nextMsg = if (index > 0) reversedMessages[index - 1] else null

      val showDateSep = shouldShowDateSeparator(prevDateLabel, curDateLabel)

      val sameAsPrev = prevMsg != null &&
          prevMsg.sent == msg.sent &&
          prevMsg.from == msg.from &&
          !showDateSep &&
          kotlin.math.abs(msg.timestamp - prevMsg.timestamp) < CLUSTER_MAX_GAP_SEC

      val sameAsNext = nextMsg != null &&
          nextMsg.sent == msg.sent &&
          nextMsg.from == msg.from &&
          nextDateLabel != null &&
          curDateLabel == nextDateLabel &&
          kotlin.math.abs(msg.timestamp - nextMsg.timestamp) < CLUSTER_MAX_GAP_SEC

      val isFirstInCluster = !sameAsPrev
      val isLastInCluster = !sameAsNext

      return ClusterFlags(
          showDateSep = showDateSep,
          sameAsPrev = sameAsPrev,
          sameAsNext = sameAsNext,
          isFirstInCluster = isFirstInCluster,
          isLastInCluster = isLastInCluster,
          showTimestamp = isLastInCluster,
      )
  }

  fun indexInReversedList(reversedMessages: List<ChatMessage>, message: ChatMessage): Int =
      reversedMessages.indexOf(message)
}
