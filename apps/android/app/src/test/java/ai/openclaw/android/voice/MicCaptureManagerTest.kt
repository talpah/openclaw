package ai.openclaw.android.voice

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MicCaptureManagerTest {

  private fun makeManager(
    scope: TestScope,
    sendToGateway: suspend (String, (String) -> Unit) -> String? = { _, _ -> null },
    speakAssistantReply: suspend (String) -> Unit = {},
  ): MicCaptureManager {
    return MicCaptureManager(
      context = RuntimeEnvironment.getApplication(),
      scope = scope,
      sendToGateway = sendToGateway,
      speakAssistantReply = speakAssistantReply,
    )
  }

  // ─── Initial state ────────────────────────────────────────────────────────

  @Test
  fun initialStateHasMicDisabled() = runTest {
    val mgr = makeManager(this)
    assertFalse(mgr.micEnabled.value)
    assertFalse(mgr.isListening.value)
    assertFalse(mgr.micCooldown.value)
    assertFalse(mgr.isSending.value)
    assertEquals("Mic off", mgr.statusText.value)
    assertNull(mgr.liveTranscript.value)
    assertTrue(mgr.queuedMessages.value.isEmpty())
    assertTrue(mgr.conversation.value.isEmpty())
    assertEquals(0f, mgr.inputLevel.value)
  }

  // ─── handleGatewayEvent — early-return filtering ─────────────────────────

  @Test
  fun handleGatewayEventIgnoresNonChatEvents() = runTest {
    val mgr = makeManager(this)
    // Should silently ignore non-chat events without side effects
    mgr.handleGatewayEvent("health", null)
    mgr.handleGatewayEvent("tick", null)
    mgr.handleGatewayEvent("agent", """{"stream":"assistant","data":{"text":"hi"}}""")
    assertTrue(mgr.conversation.value.isEmpty())
  }

  @Test
  fun handleGatewayEventIgnoresNullPayload() = runTest {
    val mgr = makeManager(this)
    mgr.handleGatewayEvent("chat", null)
    assertTrue(mgr.conversation.value.isEmpty())
  }

  @Test
  fun handleGatewayEventIgnoresBlankPayload() = runTest {
    val mgr = makeManager(this)
    mgr.handleGatewayEvent("chat", "   ")
    assertTrue(mgr.conversation.value.isEmpty())
  }

  @Test
  fun handleGatewayEventDropsWhenNoPendingRunId() = runTest {
    val mgr = makeManager(this)
    // No pending run → event should be silently dropped
    val payload = """{"state":"delta","runId":"some-run","message":{"role":"assistant","content":[{"type":"text","text":"hi"}]}}"""
    mgr.handleGatewayEvent("chat", payload)
    assertTrue(mgr.conversation.value.isEmpty())
    assertFalse(mgr.isSending.value)
  }

  @Test
  fun handleGatewayEventDropsWhenPayloadMissingRunId() = runTest {
    val mgr = makeManager(this)
    // Payload has no "runId" field → asStringOrNull returns null → return
    val payload = """{"state":"delta","message":{"role":"assistant","content":[{"type":"text","text":"hi"}]}}"""
    mgr.handleGatewayEvent("chat", payload)
    assertTrue(mgr.conversation.value.isEmpty())
  }

  @Test
  fun handleGatewayEventIgnoresInvalidJsonPayload() = runTest {
    val mgr = makeManager(this)
    mgr.handleGatewayEvent("chat", "not-valid-json")
    assertTrue(mgr.conversation.value.isEmpty())
  }

  // ─── onGatewayConnectionChanged ──────────────────────────────────────────

  @Test
  fun onGatewayConnectionChangedToTrueWithEmptyQueueIsNoOp() = runTest {
    val mgr = makeManager(this)
    mgr.onGatewayConnectionChanged(true)
    advanceUntilIdle()
    // No messages queued → sendQueuedIfIdle does nothing meaningful
    assertFalse(mgr.isSending.value)
    assertTrue(mgr.queuedMessages.value.isEmpty())
  }

  @Test
  fun onGatewayConnectionChangedToFalseWithEmptyQueueIsNoOp() = runTest {
    val mgr = makeManager(this)
    mgr.onGatewayConnectionChanged(false)
    assertFalse(mgr.isSending.value)
    assertTrue(mgr.queuedMessages.value.isEmpty())
  }

  // ─── setMicEnabled ────────────────────────────────────────────────────────

  @Test
  fun setMicEnabledTrueWithUnavailableRecognizerDisablesMicAndSetsStatus() = runTest {
    val mgr = makeManager(this)
    // SpeechRecognizer.isRecognitionAvailable returns false in Robolectric → mic stays off
    mgr.setMicEnabled(true)
    assertFalse(mgr.micEnabled.value)
    assertEquals("Speech recognizer unavailable", mgr.statusText.value)
  }

  @Test
  fun setMicEnabledSameValueIsNoOp() = runTest {
    val mgr = makeManager(this)
    assertEquals("Mic off", mgr.statusText.value)
    // Already false, setting to false again is a no-op
    mgr.setMicEnabled(false)
    assertEquals("Mic off", mgr.statusText.value)
  }

  @Test
  fun setMicEnabledFalseWhenAlreadyFalseIsNoOp() = runTest {
    val mgr = makeManager(this)
    assertFalse(mgr.micEnabled.value)
    mgr.setMicEnabled(false)
    assertFalse(mgr.micCooldown.value)
  }

  // ─── Queue status text ────────────────────────────────────────────────────

  @Test
  fun disconnectedWithNonEmptyQueueShowsQueuedWaitingStatus() = runTest {
    // Indirectly test the queuedWaitingStatus message format by observing statusText
    // when gateway disconnects while there are queued messages.
    // We prime the queue via reflection since flushSessionToQueue is private.
    val mgr = makeManager(this)
    val queueField = mgr::class.java.getDeclaredField("messageQueue")
    queueField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val queue = queueField.get(mgr) as ArrayDeque<String>
    queue.addLast("hello world")

    mgr.onGatewayConnectionChanged(false)

    assertEquals("1 queued · waiting for gateway", mgr.statusText.value)
  }

  @Test
  fun disconnectedWithTwoQueuedItemsShowsCorrectCount() = runTest {
    val mgr = makeManager(this)
    val queueField = mgr::class.java.getDeclaredField("messageQueue")
    queueField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val queue = queueField.get(mgr) as ArrayDeque<String>
    queue.addLast("first")
    queue.addLast("second")

    mgr.onGatewayConnectionChanged(false)

    assertEquals("2 queued · waiting for gateway", mgr.statusText.value)
  }

  // ─── handleGatewayEvent — full event cycle via reflection ─────────────────

  @Test
  fun handleGatewayEventDeltaUpdatesConversationEntry() = runTest {
    val mgr = primeMgrWithPendingRun(this, runId = "run-abc")

    val deltaPayload = """
      {
        "state": "delta",
        "runId": "run-abc",
        "message": {
          "role": "assistant",
          "content": [{"type": "text", "text": "thinking..."}]
        }
      }
    """.trimIndent()
    mgr.handleGatewayEvent("chat", deltaPayload)

    val conversation = mgr.conversation.value
    assertEquals(1, conversation.size)
    assertEquals("thinking...", conversation.first().text)
    assertEquals(VoiceConversationRole.Assistant, conversation.first().role)
    assertTrue(conversation.first().isStreaming)
  }

  @Test
  fun handleGatewayEventFinalCompletesConversationEntry() = runTest {
    val mgr = primeMgrWithPendingRun(this, runId = "run-final")

    val finalPayload = """
      {
        "state": "final",
        "runId": "run-final",
        "message": {
          "role": "assistant",
          "content": [{"type": "text", "text": "Here is the answer."}]
        }
      }
    """.trimIndent()
    mgr.handleGatewayEvent("chat", finalPayload)

    val conversation = mgr.conversation.value
    assertEquals(1, conversation.size)
    assertEquals("Here is the answer.", conversation.first().text)
    assertFalse(conversation.first().isStreaming)
  }

  @Test
  fun handleGatewayEventErrorAddsErrorConversationEntry() = runTest {
    val mgr = primeMgrWithPendingRun(this, runId = "run-err")

    val errorPayload = """{"state":"error","runId":"run-err","errorMessage":"LLM failed"}"""
    mgr.handleGatewayEvent("chat", errorPayload)

    val conversation = mgr.conversation.value
    assertEquals(1, conversation.size)
    assertEquals("LLM failed", conversation.first().text)
    assertFalse(conversation.first().isStreaming)
  }

  @Test
  fun handleGatewayEventErrorWithNoMessageUsesDefault() = runTest {
    val mgr = primeMgrWithPendingRun(this, runId = "run-err2")

    val errorPayload = """{"state":"error","runId":"run-err2"}"""
    mgr.handleGatewayEvent("chat", errorPayload)

    val conversation = mgr.conversation.value
    assertEquals(1, conversation.size)
    assertEquals("Voice request failed", conversation.first().text)
  }

  @Test
  fun handleGatewayEventAbortedAddsAbortedEntry() = runTest {
    val mgr = primeMgrWithPendingRun(this, runId = "run-abort")

    val abortPayload = """{"state":"aborted","runId":"run-abort"}"""
    mgr.handleGatewayEvent("chat", abortPayload)

    val conversation = mgr.conversation.value
    assertEquals(1, conversation.size)
    assertEquals("Response aborted", conversation.first().text)
  }

  @Test
  fun handleGatewayEventFinalCompletesTheTurn() = runTest {
    val mgr = primeMgrWithPendingRun(this, runId = "run-done")

    val finalPayload = """
      {
        "state": "final",
        "runId": "run-done",
        "message": {"role":"assistant","content":[{"type":"text","text":"Done!"}]}
      }
    """.trimIndent()
    mgr.handleGatewayEvent("chat", finalPayload)

    // After completePendingTurn(), isSending is false and queue is drained
    assertFalse(mgr.isSending.value)
  }

  @Test
  fun handleGatewayEventDropsWhenRunIdMismatch() = runTest {
    val mgr = primeMgrWithPendingRun(this, runId = "run-correct")

    val payload = """
      {
        "state": "delta",
        "runId": "run-wrong",
        "message": {"role":"assistant","content":[{"type":"text","text":"sneaky"}]}
      }
    """.trimIndent()
    mgr.handleGatewayEvent("chat", payload)

    assertTrue(mgr.conversation.value.isEmpty())
  }
}

/**
 * Primes a [MicCaptureManager] with a pending run via reflection so that
 * [MicCaptureManager.handleGatewayEvent] will process events for [runId].
 * Also adds a queued message so the turn is structurally valid.
 */
private fun primeMgrWithPendingRun(scope: TestScope, runId: String): MicCaptureManager {
  val mgr =
    MicCaptureManager(
      context = RuntimeEnvironment.getApplication(),
      scope = scope,
      sendToGateway = { _, _ -> runId },
    )

  // Inject pendingRunId and a queue entry via reflection
  val pendingRunIdField = mgr::class.java.getDeclaredField("pendingRunId")
  pendingRunIdField.isAccessible = true
  pendingRunIdField.set(mgr, runId)

  val queueField = mgr::class.java.getDeclaredField("messageQueue")
  queueField.isAccessible = true
  @Suppress("UNCHECKED_CAST")
  val queue = queueField.get(mgr) as ArrayDeque<String>
  queue.addLast("test message")

  val isSendingField = mgr::class.java.getDeclaredField("_isSending")
  isSendingField.isAccessible = true
  @Suppress("UNCHECKED_CAST")
  (isSendingField.get(mgr) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>).value = true

  return mgr
}
