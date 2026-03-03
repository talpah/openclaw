package ai.openclaw.android.chat

import ai.openclaw.android.gateway.DeviceAuthTokenStore
import ai.openclaw.android.gateway.DeviceIdentityStore
import ai.openclaw.android.gateway.GatewaySession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

private class InMemoryTokenStore : DeviceAuthTokenStore {
  override fun loadToken(deviceId: String, role: String): String? = null
  override fun saveToken(deviceId: String, role: String, token: String) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatControllerTest {
  private val json = Json { ignoreUnknownKeys = true }

  private fun makeController(
    scope: TestScope,
    supportsChatSubscribe: Boolean = false,
  ): ChatController {
    val identityStore = DeviceIdentityStore(RuntimeEnvironment.getApplication())
    val session =
      GatewaySession(
        scope = scope,
        identityStore = identityStore,
        deviceAuthStore = InMemoryTokenStore(),
        onConnected = { _, _, _ -> },
        onDisconnected = {},
        onEvent = { _, _ -> },
      )
    return ChatController(
      scope = scope,
      session = session,
      json = json,
      supportsChatSubscribe = supportsChatSubscribe,
    )
  }

  // ─── handleGatewayEvent("health") ────────────────────────────────────────

  @Test
  fun healthEventSetsHealthOk() = runTest {
    val ctrl = makeController(this)
    assertFalse(ctrl.healthOk.value)
    ctrl.handleGatewayEvent("health", null)
    assertTrue(ctrl.healthOk.value)
  }

  // ─── handleGatewayEvent("seqGap") ────────────────────────────────────────

  @Test
  fun seqGapEventSetsErrorText() = runTest {
    val ctrl = makeController(this)
    ctrl.handleGatewayEvent("seqGap", null)
    assertEquals("Event stream interrupted; try refreshing.", ctrl.errorText.value)
  }

  @Test
  fun seqGapEventClearsPendingRuns() = runTest {
    val ctrl = makeController(this)
    ctrl.handleGatewayEvent("health", null)
    ctrl.sendMessage("hello", "off", emptyList())
    assertEquals(1, ctrl.pendingRunCount.value)
    ctrl.handleGatewayEvent("seqGap", null)
    assertEquals(0, ctrl.pendingRunCount.value)
  }

  // ─── onDisconnected ───────────────────────────────────────────────────────

  @Test
  fun onDisconnectedClearsVolatileState() = runTest {
    val ctrl = makeController(this)
    ctrl.handleGatewayEvent("health", null)
    ctrl.sendMessage("hello", "off", emptyList())
    ctrl.onDisconnected("network loss")

    assertFalse(ctrl.healthOk.value)
    assertNull(ctrl.errorText.value)
    assertNull(ctrl.streamingAssistantText.value)
    assertNull(ctrl.sessionId.value)
    assertEquals(0, ctrl.pendingRunCount.value)
    assertTrue(ctrl.pendingToolCalls.value.isEmpty())
  }

  // ─── setThinkingLevel ────────────────────────────────────────────────────

  @Test
  fun setThinkingLevelNormalizesKnownValues() = runTest {
    val ctrl = makeController(this)
    ctrl.setThinkingLevel("high")
    assertEquals("high", ctrl.thinkingLevel.value)
    ctrl.setThinkingLevel("MEDIUM")
    assertEquals("medium", ctrl.thinkingLevel.value)
    ctrl.setThinkingLevel("Low")
    assertEquals("low", ctrl.thinkingLevel.value)
    ctrl.setThinkingLevel("off")
    assertEquals("off", ctrl.thinkingLevel.value)
  }

  @Test
  fun setThinkingLevelUnknownFallsBackToOff() = runTest {
    val ctrl = makeController(this)
    ctrl.setThinkingLevel("turbo")
    assertEquals("off", ctrl.thinkingLevel.value)
  }

  @Test
  fun setThinkingLevelSameValueIsNoOp() = runTest {
    val ctrl = makeController(this)
    ctrl.setThinkingLevel("high")
    ctrl.setThinkingLevel("high")
    assertEquals("high", ctrl.thinkingLevel.value)
  }

  // ─── sendMessage ─────────────────────────────────────────────────────────

  @Test
  fun sendMessageWhenNotHealthyReturnsError() = runTest {
    val ctrl = makeController(this)
    assertFalse(ctrl.healthOk.value)
    ctrl.sendMessage("hello", "off", emptyList())
    assertEquals("Gateway health not OK; cannot send", ctrl.errorText.value)
  }

  @Test
  fun sendMessageEmptyTextAndNoAttachmentsIsNoOp() = runTest {
    val ctrl = makeController(this)
    ctrl.handleGatewayEvent("health", null)
    ctrl.sendMessage("  ", "off", emptyList())
    assertTrue(ctrl.messages.value.isEmpty())
    assertNull(ctrl.errorText.value)
  }

  @Test
  fun sendMessageAddsOptimisticUserMessage() = runTest {
    val ctrl = makeController(this)
    ctrl.handleGatewayEvent("health", null)
    ctrl.sendMessage("Hello world", "off", emptyList())

    val messages = ctrl.messages.value
    assertEquals(1, messages.size)
    assertEquals("user", messages.first().role)
    assertEquals("Hello world", messages.first().content.firstOrNull()?.text)
  }

  @Test
  fun sendMessageIncrementsPendingRunCount() = runTest {
    val ctrl = makeController(this)
    ctrl.handleGatewayEvent("health", null)
    ctrl.sendMessage("hello", "off", emptyList())
    assertEquals(1, ctrl.pendingRunCount.value)
  }

  @Test
  fun sendMessageClearsErrorAndStreamingTextFirst() = runTest {
    val ctrl = makeController(this)
    // Prime some existing state via a chat delta
    ctrl.handleGatewayEvent(
      "chat",
      """{"state":"delta","message":{"role":"assistant","content":[{"type":"text","text":"Hi"}]}}""",
    )
    ctrl.handleGatewayEvent("seqGap", null)
    assertEquals("Event stream interrupted; try refreshing.", ctrl.errorText.value)

    ctrl.handleGatewayEvent("health", null)
    ctrl.sendMessage("new message", "off", emptyList())

    assertNull(ctrl.errorText.value)
    assertNull(ctrl.streamingAssistantText.value)
  }

  // ─── handleGatewayEvent("chat", ...) ─────────────────────────────────────

  @Test
  fun chatDeltaEventSetsStreamingText() = runTest {
    val ctrl = makeController(this)
    val payload =
      """{"state":"delta","message":{"role":"assistant","content":[{"type":"text","text":"Thinking..."}]}}"""
    ctrl.handleGatewayEvent("chat", payload)
    assertEquals("Thinking...", ctrl.streamingAssistantText.value)
  }

  @Test
  fun chatDeltaIgnoredForNonPendingRunId() = runTest {
    val ctrl = makeController(this)
    val payload =
      """{"state":"delta","runId":"unknown-run-id","message":{"role":"assistant","content":[{"type":"text","text":"Sneaky"}]}}"""
    ctrl.handleGatewayEvent("chat", payload)
    assertNull(ctrl.streamingAssistantText.value)
  }

  @Test
  fun chatDeltaIgnoredForWrongSessionKey() = runTest {
    val ctrl = makeController(this)
    val payload =
      """{"sessionKey":"other","state":"delta","message":{"role":"assistant","content":[{"type":"text","text":"Hi"}]}}"""
    ctrl.handleGatewayEvent("chat", payload)
    assertNull(ctrl.streamingAssistantText.value)
  }

  @Test
  fun chatFinalEventClearsStreamingText() = runTest {
    val ctrl = makeController(this)
    ctrl.handleGatewayEvent(
      "chat",
      """{"state":"delta","message":{"role":"assistant","content":[{"type":"text","text":"Hello"}]}}""",
    )
    assertEquals("Hello", ctrl.streamingAssistantText.value)

    ctrl.handleGatewayEvent("chat", """{"state":"final"}""")
    assertNull(ctrl.streamingAssistantText.value)
  }

  @Test
  fun chatErrorEventSetsErrorText() = runTest {
    val ctrl = makeController(this)
    ctrl.handleGatewayEvent("chat", """{"state":"error","errorMessage":"LLM timeout"}""")
    assertEquals("LLM timeout", ctrl.errorText.value)
  }

  @Test
  fun chatErrorEventFallsBackToDefaultMessage() = runTest {
    val ctrl = makeController(this)
    ctrl.handleGatewayEvent("chat", """{"state":"error"}""")
    assertEquals("Chat failed", ctrl.errorText.value)
  }

  @Test
  fun chatAbortedEventClearsStreamingText() = runTest {
    val ctrl = makeController(this)
    ctrl.handleGatewayEvent(
      "chat",
      """{"state":"delta","message":{"role":"assistant","content":[{"type":"text","text":"partial"}]}}""",
    )
    ctrl.handleGatewayEvent("chat", """{"state":"aborted"}""")
    assertNull(ctrl.streamingAssistantText.value)
  }

  // ─── handleGatewayEvent("agent", ...) ────────────────────────────────────

  @Test
  fun agentAssistantStreamSetsStreamingText() = runTest {
    val ctrl = makeController(this)
    ctrl.handleGatewayEvent("agent", """{"stream":"assistant","data":{"text":"processing..."}}""")
    assertEquals("processing...", ctrl.streamingAssistantText.value)
  }

  @Test
  fun agentToolStartAddsPendingToolCall() = runTest {
    val ctrl = makeController(this)
    val payload =
      """{"stream":"tool","data":{"phase":"start","name":"read_file","toolCallId":"call_xyz"},"ts":1700000000000}"""
    ctrl.handleGatewayEvent("agent", payload)

    val calls = ctrl.pendingToolCalls.value
    assertEquals(1, calls.size)
    assertEquals("read_file", calls.first().name)
    assertEquals("call_xyz", calls.first().toolCallId)
  }

  @Test
  fun agentToolResultRemovesPendingToolCall() = runTest {
    val ctrl = makeController(this)
    val start =
      """{"stream":"tool","data":{"phase":"start","name":"read_file","toolCallId":"call_xyz"},"ts":1700000000000}"""
    ctrl.handleGatewayEvent("agent", start)
    assertEquals(1, ctrl.pendingToolCalls.value.size)

    val result =
      """{"stream":"tool","data":{"phase":"result","name":"read_file","toolCallId":"call_xyz"}}"""
    ctrl.handleGatewayEvent("agent", result)
    assertTrue(ctrl.pendingToolCalls.value.isEmpty())
  }

  @Test
  fun agentErrorStreamSetsErrorAndClearsState() = runTest {
    val ctrl = makeController(this)
    ctrl.handleGatewayEvent(
      "agent",
      """{"stream":"tool","data":{"phase":"start","name":"write_file","toolCallId":"call_abc"},"ts":1700000000000}""",
    )
    assertEquals(1, ctrl.pendingToolCalls.value.size)

    ctrl.handleGatewayEvent("agent", """{"stream":"error"}""")

    assertEquals("Event stream interrupted; try refreshing.", ctrl.errorText.value)
    assertEquals(0, ctrl.pendingRunCount.value)
    assertTrue(ctrl.pendingToolCalls.value.isEmpty())
    assertNull(ctrl.streamingAssistantText.value)
  }

  @Test
  fun agentToolStartIgnoredForMissingFields() = runTest {
    val ctrl = makeController(this)
    // Missing toolCallId → should be ignored
    ctrl.handleGatewayEvent(
      "agent",
      """{"stream":"tool","data":{"phase":"start","name":"read_file"}}""",
    )
    assertTrue(ctrl.pendingToolCalls.value.isEmpty())
  }

  // ─── switchSession ────────────────────────────────────────────────────────

  @Test
  fun switchSessionChangesSessionKey() = runTest {
    val ctrl = makeController(this)
    assertEquals("main", ctrl.sessionKey.value)
    ctrl.switchSession("project-alpha")
    assertEquals("project-alpha", ctrl.sessionKey.value)
  }

  @Test
  fun switchSessionNoOpWhenKeyAlreadyActive() = runTest {
    val ctrl = makeController(this)
    ctrl.switchSession("main")
    assertEquals("main", ctrl.sessionKey.value)
  }

  @Test
  fun switchSessionTrimsKey() = runTest {
    val ctrl = makeController(this)
    ctrl.switchSession("  trimmed  ")
    assertEquals("trimmed", ctrl.sessionKey.value)
  }

  // ─── applyMainSessionKey ─────────────────────────────────────────────────

  @Test
  fun applyMainSessionKeyUpdatesWhenOnMain() = runTest {
    val ctrl = makeController(this)
    assertEquals("main", ctrl.sessionKey.value)
    ctrl.applyMainSessionKey("user-workspace")
    assertEquals("user-workspace", ctrl.sessionKey.value)
  }

  @Test
  fun applyMainSessionKeyNoOpWhenNotOnMain() = runTest {
    val ctrl = makeController(this)
    ctrl.switchSession("other")
    ctrl.applyMainSessionKey("user-workspace")
    assertEquals("other", ctrl.sessionKey.value)
  }

  @Test
  fun applyMainSessionKeyNoOpForBlankValue() = runTest {
    val ctrl = makeController(this)
    ctrl.applyMainSessionKey("  ")
    assertEquals("main", ctrl.sessionKey.value)
  }

  // ─── sendMessage → session failure ───────────────────────────────────────

  @Test
  fun sendMessageFailsGracefullyWhenSessionNotConnected() = runTest {
    val ctrl = makeController(this)
    ctrl.handleGatewayEvent("health", null)
    ctrl.sendMessage("hello", "off", emptyList())

    // The optimistic message is present synchronously
    assertEquals(1, ctrl.messages.value.size)

    // Advance time to run the send coroutine (which will throw "not connected")
    advanceUntilIdle()

    // Error is surfaced, pending run is cleared
    assertEquals("not connected", ctrl.errorText.value)
    assertEquals(0, ctrl.pendingRunCount.value)
  }
}
