package com.privimemobile.ui.chat.scroll

import com.privimemobile.protocol.ChatMessage

/**
 * Pure scroll / unread math extracted from [com.privimemobile.ui.chat.ChatScreen].
 * Unit-tested in Phase 0.5 — keep behavior identical when wiring callers.
 */
object ChatScrollMath {

  /** Bottom of reverse-layout list (newest messages). */
  const val BOTTOM_VISIBLE_INDEX_THRESHOLD = 2

  const val UNREAD_BOUNDARY_NOT_FOUND = -1

  /**
   * Index in **reversed** LazyColumn (newest at 0) for the "new messages" divider.
   * [messagesAsc] is oldest-first (Room order). Counts received (!sent) from newest until [initialUnreadCount].
   */
  fun computeUnreadBoundaryIndex(
      messagesAsc: List<ChatMessage>,
      initialUnreadCount: Int?,
  ): Int {
      val unread = initialUnreadCount ?: 0
      if (unread <= 0) return UNREAD_BOUNDARY_NOT_FOUND

      var receivedCount = 0
      for (i in messagesAsc.indices.reversed()) {
          if (!messagesAsc[i].sent) {
              receivedCount++
              if (receivedCount == unread) {
                  return messagesAsc.size - 1 - i
              }
          }
      }
      return UNREAD_BOUNDARY_NOT_FOUND
  }

  fun isAtBottom(firstVisibleItemIndex: Int): Boolean =
      firstVisibleItemIndex <= BOTTOM_VISIBLE_INDEX_THRESHOLD

  /**
   * Whether opening unread state should be cleared when user is at bottom after initial scroll settled.
   */
  fun shouldClearUnreadWhenAtBottom(
      firstVisibleItemIndex: Int,
      hasScrolledInitially: Boolean,
      messagesNotEmpty: Boolean,
      initialUnreadCount: Int?,
  ): Boolean {
      if (!messagesNotEmpty || !hasScrolledInitially) return false
      if (!isAtBottom(firstVisibleItemIndex)) return false
      val unread = initialUnreadCount ?: 0
      return unread > 0
  }

  /** Raw badge count before floor smoothing (reversed list, index 0 = newest). */
  fun computeUnreadBelowRaw(
      reversedMessages: List<ChatMessage>,
      firstVisibleItemIndex: Int,
      initialUnreadCount: Int?,
      lastBottomTimestamp: Long,
  ): Int {
      if (firstVisibleItemIndex <= BOTTOM_VISIBLE_INDEX_THRESHOLD) return 0
      val unread = initialUnreadCount ?: 0
      return if (unread > 0) {
          reversedMessages
              .take(firstVisibleItemIndex)
              .count { !it.sent }
              .coerceAtMost(unread)
      } else {
          if (lastBottomTimestamp == 0L) {
              0
          } else {
              reversedMessages
                  .take(firstVisibleItemIndex)
                  .count { !it.sent && it.timestamp > lastBottomTimestamp }
          }
      }
  }

  data class BadgeFloorState(
      val floor: Int,
      val floorVersion: Int,
  ) {
      companion object {
          val INITIAL = BadgeFloorState(floor = Int.MAX_VALUE, floorVersion = -1)
      }
  }

  /**
   * Badge floor update: reset on new message version; floor only moves down when scrolling up.
   * Returns updated state and value to display on the scroll-to-bottom FAB.
   */
  fun applyBadgeFloor(
      raw: Int,
      state: BadgeFloorState,
      newMsgVersion: Int,
  ): Pair<BadgeFloorState, Int> {
      var floor = state.floor
      var floorVersion = state.floorVersion
      if (newMsgVersion != floorVersion) {
          floor = raw
          floorVersion = newMsgVersion
      }
      if (raw <= floor) floor = raw
      val display = floor.coerceAtMost(raw)
      return BadgeFloorState(floor, floorVersion) to display
  }

  enum class InitialScrollAction {
      /** First open, no unread — animate to item 0. */
      ScrollToBottomOnFirstOpen,
      /** First open or re-entry at bottom with unread — scroll to divider. */
      ScrollToUnreadBoundary,
      /** Re-entry scrolled up, or boundary not ready — keep saved position. */
      StayAtSavedPosition,
      /** Boundary not found yet — wait for more messages. */
      WaitForMessages,
  }

  fun resolveInitialScrollAction(
      unread: Int,
      unreadBoundaryIndex: Int,
      wasAtBottom: Boolean,
      hasScrolledInitially: Boolean,
  ): InitialScrollAction {
      if (hasScrolledInitially) return InitialScrollAction.StayAtSavedPosition
      if (unread == 0) return InitialScrollAction.ScrollToBottomOnFirstOpen
      if (unreadBoundaryIndex >= 0 && wasAtBottom) return InitialScrollAction.ScrollToUnreadBoundary
      if (unreadBoundaryIndex >= 0) return InitialScrollAction.StayAtSavedPosition
      return InitialScrollAction.WaitForMessages
  }
}
