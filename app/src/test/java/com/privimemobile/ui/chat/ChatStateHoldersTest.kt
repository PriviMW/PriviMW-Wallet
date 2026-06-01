package com.privimemobile.ui.chat

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.privimemobile.protocol.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for Phase 3 state-holder helpers (no Compose UI).
 */
class ChatStateHoldersTest {

    private fun msg(id: String = "1") = ChatMessage(
        id = id,
        from = "alice",
        to = "bob",
        text = "hi",
        timestamp = 100L,
        sent = true,
    )

    @Test
    fun selection_enterToggleExit() {
        val s = ChatSelectionState()
        assertFalse(s.selectionMode)
        s.enterSelectionWith("a")
        assertTrue(s.selectionMode)
        assertEquals(listOf("a"), s.selectedIds)
        s.toggleSelected("b")
        assertEquals(setOf("a", "b"), s.selectedIds.toSet())
        s.toggleSelected("a")
        assertEquals(listOf("b"), s.selectedIds)
        s.openBulkDeleteConfirm()
        assertTrue(s.showDeleteConfirmDialog)
        assertEquals(listOf("b"), s.pendingDeleteIds)
        s.clearAfterBulkAction()
        assertFalse(s.selectionMode)
        assertTrue(s.selectedIds.isEmpty())
        assertFalse(s.showDeleteConfirmDialog)
    }

    @Test
    fun forward_singleAndMultiple() {
        val f = ChatForwardState()
        assertFalse(f.isPickerOpen)
        val m1 = msg("1")
        val m2 = msg("2")
        f.openSingle(m1)
        assertTrue(f.isPickerOpen)
        assertEquals(listOf(m1), f.messagesToForward())
        f.dismiss()
        assertFalse(f.isPickerOpen)
        f.openMultiple(listOf(m1, m2))
        assertEquals(listOf(m1, m2), f.messagesToForward())
        f.openMultiple(emptyList())
        assertTrue(f.isPickerOpen) // unchanged when empty
    }

    @Test
    fun search_toggleCloseAndQuery() {
        val s = ChatSearchState()
        s.searchHighlightTs = 42L
        s.toggle()
        assertTrue(s.showSearch)
        s.searchQuery = "x"
        s.searchResults = listOf()
        s.onQueryChanged("")
        assertEquals("", s.searchQuery)
        assertTrue(s.searchResults.isEmpty())
        assertNull(s.searchHighlightTs)
        s.closeAfterResultPick()
        assertFalse(s.showSearch)
        assertNull(s.searchJob)
    }

    @Test
    fun pinState_manualOverride() {
        val p = ChatPinState()
        p.applyBarTapOverride(safeIndex = 2, landingListIndex = 10)
        assertEquals(1, p.manualOverrideIndex)
        assertEquals(10, p.scrollPosAtOverride)
        p.clearManualOverride()
        assertEquals(-1, p.manualOverrideIndex)
        assertEquals(-1, p.scrollPosAtOverride)
    }

    @Test
    fun scrollBadge_forConvAndPersist() {
        ChatSessionStore.chatBadgeFloors.clear()
        ChatSessionStore.chatBadgeFloors["@alice"] = 3 to 7
        val s = ChatScrollBadgeState.forConv("@alice")
        assertEquals(3, s.badgeFloor)
        assertEquals(7, s.badgeFloorVersion)
        s.badgeFloor = 1
        s.badgeFloorVersion = 2
        s.persistBadgeFloor("@alice")
        assertEquals(1 to 2, ChatSessionStore.chatBadgeFloors["@alice"])
        ChatSessionStore.chatBadgeFloors.clear()
    }

    @Test
    fun emojiStickerState_flags() {
        val e = ChatEmojiStickerState()
        assertFalse(e.showEmojiPicker)
        e.showEmojiPicker = true
        e.emojiMainTab = 1
        e.viewPackId = "abc"
        e.showCreateStickerPack = true
        assertTrue(e.showEmojiPicker)
        assertEquals(1, e.emojiMainTab)
        assertEquals("abc", e.viewPackId)
        assertTrue(e.showCreateStickerPack)
    }

    @Test
    fun input_insertAtCursor_matchesEmojiPickerBehavior() {
        val i = ChatInputState()
        i.inputText = TextFieldValue("hello", TextRange(2))
        i.insertAtCursor("X")
        assertEquals("heXllo", i.inputText.text)
        assertEquals(6, i.inputText.selection.start)
    }

    @Test
    fun input_setInputTextAndClearReply() {
        val i = ChatInputState("draft")
        assertEquals("draft", i.inputText.text)
        i.setInputText("edited")
        assertEquals("edited", i.inputText.text)
        assertEquals(6, i.inputText.selection.start)
        i.replyingTo = msg()
        i.editingMsg = msg("2")
        i.clearReplyAndEdit()
        assertNull(i.replyingTo)
        assertNull(i.editingMsg)
    }

    @Test
    fun contextMenu_andMediaDismiss() {
        val menu = ChatContextMenuState()
        val m = msg()
        menu.contextMenuMsg = m
        menu.showReactionDetail(m, "👍")
        assertEquals(m, menu.reactionDetailMsg)
        assertEquals("👍", menu.reactionDetailEmoji)
        menu.dismissReactionDetail()
        assertNull(menu.reactionDetailMsg)
        menu.dismissContextMenu()
        assertNull(menu.contextMenuMsg)

        val media = ChatImagePreviewState()
        media.previewCaption = "cap"
        media.sendingFromPreview = true
        media.dismissImagePreview()
        assertNull(media.imagePreview)
        assertEquals("", media.previewCaption)
        assertFalse(media.sendingFromPreview)
    }

    @Test
    fun voice_resetRecordingUi() {
        val v = ChatVoiceState()
        v.voiceRecording = true
        v.voiceLocked = true
        v.micSlideOffset = 5f
        v.resetRecordingUi()
        assertFalse(v.voiceRecording)
        assertFalse(v.voiceLocked)
        assertEquals(0f, v.micSlideOffset, 0f)
    }
}
