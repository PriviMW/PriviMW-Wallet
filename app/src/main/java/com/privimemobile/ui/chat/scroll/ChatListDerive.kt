package com.privimemobile.ui.chat.scroll

import com.privimemobile.protocol.ChatMessage
import com.privimemobile.protocol.Helpers

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

  data class AlbumGroups(
      val groups: Map<String, List<String>>,
      val skipIds: Set<String>,
  )

  /** Consecutive captionless image files from same sender within 60s → album grid (first id is anchor). */
  fun computeAlbumGroups(reversedMessages: List<ChatMessage>): AlbumGroups {
      val groups = mutableMapOf<String, List<String>>()
      val skipIds = mutableSetOf<String>()
      for (i in reversedMessages.indices) {
          if (reversedMessages[i].id in skipIds) continue
          val msg = reversedMessages[i]
          val file = msg.file
          if (msg.type != "file" || file == null || !Helpers.isImageMime(file.mime)) continue
          if (msg.text.isNotEmpty()) continue
          val albumIds = mutableListOf(msg.id)
          var j = i + 1
          while (j < reversedMessages.size) {
              val next = reversedMessages[j]
              val nextFile = next.file
              if (next.type == "file" && nextFile != null &&
                  Helpers.isImageMime(nextFile.mime) &&
                  next.sent == msg.sent &&
                  kotlin.math.abs(next.timestamp - msg.timestamp) < CLUSTER_MAX_GAP_SEC &&
                  next.text.isEmpty()
              ) {
                  albumIds.add(next.id)
                  skipIds.add(next.id)
                  j++
              } else {
                  break
              }
          }
          if (albumIds.size > 1) {
              groups[msg.id] = albumIds
          }
      }
      return AlbumGroups(groups, groups.values.flatMap { it.drop(1) }.toSet())
  }
}
