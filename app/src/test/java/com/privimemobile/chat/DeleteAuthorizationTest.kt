package com.privimemobile.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteAuthorizationTest {

    @Test
    fun member_canDeleteOwnMessage() {
        assertTrue(DeleteAuthorization.canDeleteGroupMessage(0, "alice", "alice"))
        assertTrue(DeleteAuthorization.canDeleteGroupMessage(0, "@alice", "@alice"))
    }

    @Test
    fun member_cannotDeleteOthersMessage() {
        assertFalse(DeleteAuthorization.canDeleteGroupMessage(0, "bob", "alice"))
    }

    @Test
    fun admin_canDeleteOthersMessage() {
        assertTrue(DeleteAuthorization.canDeleteGroupMessage(1, "admin", "alice"))
        assertTrue(DeleteAuthorization.canDeleteGroupMessage(2, "owner", "alice"))
    }

    @Test
    fun banned_cannotDeleteAnything() {
        assertFalse(DeleteAuthorization.canDeleteGroupMessage(3, "banned", "banned"))
        assertFalse(DeleteAuthorization.canDeleteGroupMessage(3, "banned", "alice"))
    }

    @Test
    fun emptyHandles_rejected() {
        assertFalse(DeleteAuthorization.canDeleteGroupMessage(1, "", "alice"))
        assertFalse(DeleteAuthorization.canDeleteGroupMessage(1, "alice", ""))
    }

    @Test
    fun offerDelete_ownMessage_always() {
        assertTrue(DeleteAuthorization.canOfferDeleteForEveryone(false, 0, true))
        assertTrue(DeleteAuthorization.canOfferDeleteForEveryone(true, 0, true))
    }

    @Test
    fun offerDelete_others_onlyForGroupModerator() {
        assertFalse(DeleteAuthorization.canOfferDeleteForEveryone(false, 0, false))
        assertFalse(DeleteAuthorization.canOfferDeleteForEveryone(true, 0, false))
        assertTrue(DeleteAuthorization.canOfferDeleteForEveryone(true, 1, false))
        assertTrue(DeleteAuthorization.canOfferDeleteForEveryone(true, 2, false))
        assertFalse(DeleteAuthorization.canOfferDeleteForEveryone(true, 3, false))
    }

    @Test
    fun broadcastFilter_adminGetsAllSelected() {
        data class Row(val sent: Boolean)
        val selected = listOf(Row(sent = true), Row(sent = false))
        val member = DeleteAuthorization.filterForDeleteForEveryoneBroadcast(true, 0, selected) { it.sent }
        assertEquals(1, member.size)
        val admin = DeleteAuthorization.filterForDeleteForEveryoneBroadcast(true, 1, selected) { it.sent }
        assertEquals(2, admin.size)
    }
}
