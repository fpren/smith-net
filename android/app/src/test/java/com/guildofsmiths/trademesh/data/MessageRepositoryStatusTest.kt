package com.guildofsmiths.trademesh.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the message delivery status lifecycle (PENDING/SENT/FAILED):
 * MessageRepository.updateDeliveryStatus must update the in-memory
 * _allMessages StateFlow entry for a known id, and be a no-op for an
 * unknown id.
 */
class MessageRepositoryStatusTest {

    @After
    fun tearDown() {
        // MessageRepository is a singleton — reset to avoid test bleed.
        MessageRepository.clear()
    }

    @Test
    fun `updateDeliveryStatus transitions PENDING to SENT`() = runTest {
        val message = Message(
            id = "msg-pending-to-sent",
            senderId = "user-1",
            senderName = "Tester",
            content = "hello",
            deliveryStatus = DeliveryStatus.PENDING
        )
        MessageRepository.addMessage(message)

        MessageRepository.allMessages.test {
            assertEquals(
                DeliveryStatus.PENDING,
                awaitItem().first { it.id == message.id }.deliveryStatus
            )

            MessageRepository.updateDeliveryStatus(message.id, DeliveryStatus.SENT)

            assertEquals(
                DeliveryStatus.SENT,
                awaitItem().first { it.id == message.id }.deliveryStatus
            )
        }
    }

    @Test
    fun `updateDeliveryStatus transitions PENDING to FAILED`() = runTest {
        val message = Message(
            id = "msg-pending-to-failed",
            senderId = "user-1",
            senderName = "Tester",
            content = "hello",
            deliveryStatus = DeliveryStatus.PENDING
        )
        MessageRepository.addMessage(message)

        MessageRepository.allMessages.test {
            assertEquals(
                DeliveryStatus.PENDING,
                awaitItem().first { it.id == message.id }.deliveryStatus
            )

            MessageRepository.updateDeliveryStatus(message.id, DeliveryStatus.FAILED)

            assertEquals(
                DeliveryStatus.FAILED,
                awaitItem().first { it.id == message.id }.deliveryStatus
            )
        }
    }

    @Test
    fun `updateDeliveryStatus with unknown id is a no-op`() {
        val message = Message(
            id = "msg-untouched",
            senderId = "user-1",
            senderName = "Tester",
            content = "hello",
            deliveryStatus = DeliveryStatus.SENT
        )
        MessageRepository.addMessage(message)

        val before = MessageRepository.allMessages.value

        MessageRepository.updateDeliveryStatus("does-not-exist", DeliveryStatus.FAILED)

        val after = MessageRepository.allMessages.value
        assertEquals(before, after)
        assertEquals(DeliveryStatus.SENT, after.first { it.id == message.id }.deliveryStatus)
    }
}
