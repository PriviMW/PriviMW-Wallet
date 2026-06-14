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
   * Album siblings are skipped when finding cluster neighbors so album anchors show avatar/name.
   */
  fun computeClusterFlags(
      index: Int,
      reversedMessages: List<ChatMessage>,
      curDateLabel: String,
      albumGroups: Map<String, List<String>> = emptyMap(),
      dateLabelFor: (ChatMessage) -> String,
  ): ClusterFlags {
      val msg = reversedMessages[index]
      val albumMateIds = albumMemberIds(msg.id, albumGroups)

      val prevMsg = neighborForCluster(index, reversedMessages, albumMateIds, older = true)
      val nextMsg = neighborForCluster(index, reversedMessages, albumMateIds, older = false)

      val prevDateLabel = prevMsg?.let(dateLabelFor)
      val nextDateLabel = nextMsg?.let(dateLabelFor)

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

  private fun albumMemberIds(messageId: String, albumGroups: Map<String, List<String>>): Set<String>? {
      albumGroups[messageId]?.let { return it.toSet() }
      for ((_, ids) in albumGroups) {
          if (messageId in ids) return ids.toSet()
      }
      return null
  }

  /** Centered/system rows are not bubble-cluster peers (join pills, invites, etc.). */
  private fun breaksCluster(msg: ChatMessage): Boolean =
      msg.type == "group_service" || msg.type == "group_invite"

  /** Walk toward older/newer skipping album siblings and non-clusterable rows. */
  private fun neighborForCluster(
      index: Int,
      reversedMessages: List<ChatMessage>,
      albumMateIds: Set<String>?,
      older: Boolean,
  ): ChatMessage? {
      var i = if (older) index + 1 else index - 1
      while (i in reversedMessages.indices) {
          val candidate = reversedMessages[i]
          if (albumMateIds != null && candidate.id in albumMateIds) {
              i += if (older) 1 else -1
              continue
          }
          if (breaksCluster(candidate)) {
              i += if (older) 1 else -1
              continue
          }
          return candidate
      }
      return null
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
