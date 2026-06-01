package com.privimemobile.chat

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
}
