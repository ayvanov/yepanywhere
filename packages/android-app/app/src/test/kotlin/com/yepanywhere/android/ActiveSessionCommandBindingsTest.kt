package com.yepanywhere.android

import kotlin.test.Test
import kotlin.test.assertEquals

class ActiveSessionCommandBindingsTest {
    @Test
    fun createActiveSessionCallbacksDelegatesAllCommandsToHandler() {
        val handler = FakeActiveSessionCommandHandler()
        var attachClicks = 0

        val callbacks = createActiveSessionCallbacks(handler) {
            attachClicks += 1
        }

        callbacks.onSendReply("Reply from UI")
        callbacks.onApproveRequest("request-1")
        callbacks.onDenyRequest("request-2", "Need more context")
        callbacks.onAnswerQuestion("request-3", "Use cache-first.")
        callbacks.onAttachClicked()

        assertEquals(listOf("Reply from UI"), handler.sentReplies)
        assertEquals(listOf("request-1"), handler.approvedRequestIds)
        assertEquals(listOf("request-2|Need more context"), handler.deniedRequests)
        assertEquals(listOf("request-3|Use cache-first."), handler.answeredRequests)
        assertEquals(1, attachClicks)
    }

    private class FakeActiveSessionCommandHandler : ActiveSessionCommandHandler {
        val sentReplies = mutableListOf<String>()
        val approvedRequestIds = mutableListOf<String>()
        val deniedRequests = mutableListOf<String>()
        val answeredRequests = mutableListOf<String>()

        override fun sendReply(text: String) {
            sentReplies += text
        }

        override fun approve(requestId: String) {
            approvedRequestIds += requestId
        }

        override fun deny(requestId: String, feedback: String?) {
            deniedRequests += "$requestId|$feedback"
        }

        override fun answerQuestion(requestId: String, answer: String) {
            answeredRequests += "$requestId|$answer"
        }
    }
}
