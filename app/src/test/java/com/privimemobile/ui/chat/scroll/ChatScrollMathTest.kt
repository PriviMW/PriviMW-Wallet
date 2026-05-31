package com.privimemobile.ui.chat.scroll

import com.privimemobile.protocol.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollMathTest {

    private fun msg(
        id: String,
        sent: Boolean,
        timestamp: Long = 1000L,
        from: String = if (sent) "me" else "them",
    ) = ChatMessage(
        id = id,
        from = from,
        to = if (sent) "them" else "me",
        text = "hi",
        timestamp = timestamp,
        sent = sent,
    )

    @Test
    fun unreadBoundary_noneWhenZeroUnread() {
        val list = listOf(msg("1", sent = false), msg("2", sent = true))
        assertEquals(
            ChatScrollMath.UNREAD_BOUNDARY_NOT_FOUND,
            ChatScrollMath.computeUnreadBoundaryIndex(list, 0),
        )
        assertEquals(
            ChatScrollMath.UNREAD_BOUNDARY_NOT_FOUND,
            ChatScrollMath.computeUnreadBoundaryIndex(list, null),
        )
    }

    @Test
    fun unreadBoundary_skipsSentMessagesBetweenReceived() {
        // ASC: oldest first — m0 recv, m1 sent, m2 recv (newest)
        val list = listOf(
            msg("a", sent = false, timestamp = 1),
            msg("b", sent = true, timestamp = 2),
            msg("c", sent = false, timestamp = 3),
        )
        // 2 unread: oldest of the pair is m0 → reversed index 2
        assertEquals(2, ChatScrollMath.computeUnreadBoundaryIndex(list, 2))
        // 1 unread: boundary at newest received only (m2 → reversed index 0)
        assertEquals(0, ChatScrollMath.computeUnreadBoundaryIndex(list, 1))
    }

    @Test
    fun unreadBoundary_oldestUnreadInBatch() {
        val list = listOf(
            msg("a", sent = false, timestamp = 1),
            msg("b", sent = false, timestamp = 2),
            msg("c", sent = true, timestamp = 3),
        )
        // 2 unread from newest: 2nd received is m0 → reversed index = size-1-0 = 2
        assertEquals(2, ChatScrollMath.computeUnreadBoundaryIndex(list, 2))
    }

    @Test
    fun isAtBottom_and_clearUnreadGate() {
        assertTrue(ChatScrollMath.isAtBottom(0))
        assertTrue(ChatScrollMath.isAtBottom(2))
        assertFalse(ChatScrollMath.isAtBottom(3))

        assertTrue(
            ChatScrollMath.shouldClearUnreadWhenAtBottom(0, true, true, 5),
        )
        assertTrue(
            ChatScrollMath.shouldClearUnreadWhenAtBottom(1, true, true, 3),
        )
        assertFalse(
            ChatScrollMath.shouldClearUnreadWhenAtBottom(1, false, true, 3),
        )
        assertFalse(
            ChatScrollMath.shouldClearUnreadWhenAtBottom(1, true, true, 0),
        )
    }

    @Test
    fun unreadBelowRaw_initialUnreadVsTimestamp() {
        // reversed: newest first
        val reversed = listOf(
            msg("n", sent = false, timestamp = 40),
            msg("n2", sent = false, timestamp = 30),
            msg("s", sent = true, timestamp = 20),
            msg("o", sent = false, timestamp = 10),
        )
        assertEquals(0, ChatScrollMath.computeUnreadBelowRaw(reversed, 2, 5, 0L))
        assertEquals(3, ChatScrollMath.computeUnreadBelowRaw(reversed, 4, 5, 0L))
        assertEquals(1, ChatScrollMath.computeUnreadBelowRaw(reversed, 4, 1, 0L))

        assertEquals(
            2,
            ChatScrollMath.computeUnreadBelowRaw(reversed, 4, null, lastBottomTimestamp = 15),
        )
        assertEquals(
            0,
            ChatScrollMath.computeUnreadBelowRaw(reversed, 4, null, lastBottomTimestamp = 0L),
        )
    }

    @Test
    fun badgeFloor_resetsOnNewVersion_andOnlyMovesDown() {
        var floorState = ChatScrollMath.BadgeFloorState.INITIAL
        var result = ChatScrollMath.applyBadgeFloor(5, floorState, newMsgVersion = 1)
        assertEquals(5, result.second)
        floorState = result.first

        result = ChatScrollMath.applyBadgeFloor(8, floorState, newMsgVersion = 1)
        assertEquals(5, result.second) // floor 5, raw 8 → display 5
        floorState = result.first

        result = ChatScrollMath.applyBadgeFloor(3, floorState, newMsgVersion = 1)
        assertEquals(3, result.second) // floor drops with scroll-up raw
        floorState = result.first

        result = ChatScrollMath.applyBadgeFloor(10, floorState, newMsgVersion = 2)
        assertEquals(10, result.second) // new messages → floor reset to raw
    }

    @Test
    fun initialScrollAction() {
        assertEquals(
            ChatScrollMath.InitialScrollAction.ScrollToBottomOnFirstOpen,
            ChatScrollMath.resolveInitialScrollAction(0, -1, wasAtBottom = true, hasScrolledInitially = false),
        )
        assertEquals(
            ChatScrollMath.InitialScrollAction.ScrollToUnreadBoundary,
            ChatScrollMath.resolveInitialScrollAction(3, 2, wasAtBottom = true, hasScrolledInitially = false),
        )
        assertEquals(
            ChatScrollMath.InitialScrollAction.StayAtSavedPosition,
            ChatScrollMath.resolveInitialScrollAction(3, 2, wasAtBottom = false, hasScrolledInitially = false),
        )
        assertEquals(
            ChatScrollMath.InitialScrollAction.WaitForMessages,
            ChatScrollMath.resolveInitialScrollAction(3, -1, wasAtBottom = true, hasScrolledInitially = false),
        )
    }
}
