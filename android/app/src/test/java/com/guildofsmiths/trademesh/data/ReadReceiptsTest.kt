package com.guildofsmiths.trademesh.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Covers MessageRepository's read-receipt accumulator:
 * markReadLocal(messageId, userId) folds into readByMessage (messageId ->
 * set of reader userIds), deduping repeats and excluding self-reads, with
 * the StateFlow emitting only on an actual change.
 */
class ReadReceiptsTest {

    @After
    fun tearDown() {
        // MessageRepository is a singleton — reset to avoid test bleed.
        MessageRepository.clear()
    }

    @Test
    fun `markReadLocal accumulates readers per message`() {
        MessageRepository.markReadLocal("msg-1", "user-a")
        MessageRepository.markReadLocal("msg-1", "user-b")

        assertEquals(
            setOf("user-a", "user-b"),
            MessageRepository.readByMessage.value["msg-1"]
        )
    }

    @Test
    fun `markReadLocal dedupes repeated reads from the same user`() {
        MessageRepository.markReadLocal("msg-1", "user-a")
        MessageRepository.markReadLocal("msg-1", "user-a")

        assertEquals(
            setOf("user-a"),
            MessageRepository.readByMessage.value["msg-1"]
        )
    }

    @Test
    fun `markReadLocal keeps per-message state separate`() {
        MessageRepository.markReadLocal("msg-1", "user-a")
        MessageRepository.markReadLocal("msg-2", "user-b")

        assertEquals(setOf("user-a"), MessageRepository.readByMessage.value["msg-1"])
        assertEquals(setOf("user-b"), MessageRepository.readByMessage.value["msg-2"])
    }

    @Test
    fun `markReadLocal excludes self-reads`() {
        val selfId = UserPreferences.getUserId()

        MessageRepository.markReadLocal("msg-1", selfId)

        assertFalse(MessageRepository.readByMessage.value.containsKey("msg-1"))
    }

    @Test
    fun `readByMessage flow emits on change`() = runTest {
        MessageRepository.readByMessage.test {
            assertEquals(emptyMap<String, Set<String>>(), awaitItem())

            MessageRepository.markReadLocal("msg-1", "user-a")
            assertEquals(setOf("user-a"), awaitItem()["msg-1"])

            MessageRepository.markReadLocal("msg-1", "user-b")
            assertEquals(setOf("user-a", "user-b"), awaitItem()["msg-1"])
        }
    }

    @Test
    fun `readByMessage does not re-emit for a duplicate read`() = runTest {
        MessageRepository.readByMessage.test {
            assertEquals(emptyMap<String, Set<String>>(), awaitItem())

            MessageRepository.markReadLocal("msg-1", "user-a")
            assertEquals(setOf("user-a"), awaitItem()["msg-1"])

            // Duplicate — map is structurally unchanged, no new emission.
            MessageRepository.markReadLocal("msg-1", "user-a")

            MessageRepository.markReadLocal("msg-2", "user-c")
            assertEquals(setOf("user-c"), awaitItem()["msg-2"])
        }
    }
}
