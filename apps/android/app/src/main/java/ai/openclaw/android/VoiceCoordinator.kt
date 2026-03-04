package ai.openclaw.android

import android.content.Context
import ai.openclaw.android.node.asObjectOrNull
import ai.openclaw.android.node.asStringOrNull
import ai.openclaw.android.voice.MicCaptureManager
import ai.openclaw.android.voice.TalkModeManager
import ai.openclaw.android.voice.VoiceConversationEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/**
 * Owns and coordinates mic capture (STT→gateway) and TTS playback (TalkModeManager).
 * Extracted from NodeRuntime to keep voice-related state and lifecycle in one place.
 */
internal class VoiceCoordinator(
  private val appContext: Context,
  private val scope: CoroutineScope,
  private val prefs: SecurePrefs,
  private val session: ai.openclaw.android.gateway.GatewaySession,
  private val isConnected: () -> Boolean,
  private val resolveMainSessionKey: () -> String,
  private val chatThinkingLevel: () -> String,
  private val onAudioCaptureActiveChanged: (Boolean) -> Unit,
) {
  private val json = Json { ignoreUnknownKeys = true }

  private val voiceReplySpeakerLazy: Lazy<TalkModeManager> = lazy {
    TalkModeManager(
      context = appContext,
      scope = scope,
      session = session,
      supportsChatSubscribe = false,
      isConnected = isConnected,
    ).also { it.setPlaybackEnabled(prefs.speakerEnabled.value) }
  }
  private val voiceReplySpeaker: TalkModeManager get() = voiceReplySpeakerLazy.value

  val talkMode: TalkModeManager by lazy {
    TalkModeManager(
      context = appContext,
      scope = scope,
      session = session,
      supportsChatSubscribe = true,
      isConnected = isConnected,
    )
  }

  val micCapture: MicCaptureManager by lazy {
    MicCaptureManager(
      context = appContext,
      scope = scope,
      sendToGateway = { message, onRunIdKnown ->
        val idempotencyKey = UUID.randomUUID().toString()
        onRunIdKnown(idempotencyKey)
        val params =
          buildJsonObject {
            put("sessionKey", JsonPrimitive(resolveMainSessionKey()))
            put("message", JsonPrimitive(message))
            put("thinking", JsonPrimitive(chatThinkingLevel()))
            put("timeoutMs", JsonPrimitive(30_000))
            put("idempotencyKey", JsonPrimitive(idempotencyKey))
          }
        val response = session.request("chat.send", params.toString())
        parseChatSendRunId(response) ?: idempotencyKey
      },
      speakAssistantReply = { text ->
        // Skip if TalkModeManager is handling TTS (ttsOnAllResponses) to avoid
        // double-speaking the same assistant reply from both pipelines.
        if (!talkMode.ttsOnAllResponses) {
          voiceReplySpeaker.speakAssistantReply(text)
        }
      },
    )
  }

  // ── Delegating StateFlows ─────────────────────────────────────────────────

  val micStatusText: StateFlow<String> get() = micCapture.statusText
  val micLiveTranscript: StateFlow<String?> get() = micCapture.liveTranscript
  val micIsListening: StateFlow<Boolean> get() = micCapture.isListening
  val micEnabled: StateFlow<Boolean> get() = micCapture.micEnabled
  val micCooldown: StateFlow<Boolean> get() = micCapture.micCooldown
  val micQueuedMessages: StateFlow<List<String>> get() = micCapture.queuedMessages
  val micConversation: StateFlow<List<VoiceConversationEntry>> get() = micCapture.conversation
  val micInputLevel: StateFlow<Float> get() = micCapture.inputLevel
  val micIsSending: StateFlow<Boolean> get() = micCapture.isSending
  val speakerEnabled: StateFlow<Boolean> get() = prefs.speakerEnabled

  // ── Lifecycle ─────────────────────────────────────────────────────────────

  /** Start the talkEnabled pref observer; call once from NodeRuntime init. */
  fun startObservers() {
    scope.launch {
      prefs.talkEnabled.collect { enabled ->
        micCapture.setMicEnabled(enabled)
        if (enabled) {
          talkMode.ttsOnAllResponses = true
          scope.launch { talkMode.ensureChatSubscribed() }
        }
        onAudioCaptureActiveChanged(enabled)
      }
    }
  }

  fun onGatewayConnectionChanged(connected: Boolean) {
    micCapture.onGatewayConnectionChanged(connected)
  }

  // ── Event fan-out ─────────────────────────────────────────────────────────

  fun handleGatewayEvent(event: String, payloadJson: String?) {
    micCapture.handleGatewayEvent(event, payloadJson)
    talkMode.handleGatewayEvent(event, payloadJson)
  }

  // ── Setters ───────────────────────────────────────────────────────────────

  fun setVoiceScreenActive(active: Boolean) {
    if (!active) {
      talkMode.ttsOnAllResponses = false
      talkMode.stopTts()
      micCapture.setMicEnabled(false)
      prefs.setTalkEnabled(false)
    }
  }

  fun setMicEnabled(value: Boolean) {
    prefs.setTalkEnabled(value)
    if (value) {
      talkMode.stopTts()
      talkMode.ttsOnAllResponses = true
      scope.launch { talkMode.ensureChatSubscribed() }
    }
    micCapture.setMicEnabled(value)
    onAudioCaptureActiveChanged(value)
  }

  fun setSpeakerEnabled(value: Boolean) {
    prefs.setSpeakerEnabled(value)
    if (voiceReplySpeakerLazy.isInitialized()) {
      voiceReplySpeaker.setPlaybackEnabled(value)
    }
    talkMode.setPlaybackEnabled(value)
  }

  fun refreshTalkModeConfig() {
    if (voiceReplySpeakerLazy.isInitialized()) {
      scope.launch { voiceReplySpeaker.refreshConfig() }
    }
  }

  // ── Private ───────────────────────────────────────────────────────────────

  private fun parseChatSendRunId(response: String): String? =
    try {
      json.parseToJsonElement(response).asObjectOrNull()?.get("runId").asStringOrNull()
    } catch (_: Throwable) {
      null
    }
}
