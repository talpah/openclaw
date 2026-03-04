package ai.openclaw.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import ai.openclaw.android.chat.ChatController
import ai.openclaw.android.chat.ChatMessage
import ai.openclaw.android.chat.ChatPendingToolCall
import ai.openclaw.android.chat.ChatSessionEntry
import ai.openclaw.android.chat.OutgoingAttachment
import ai.openclaw.android.gateway.DeviceAuthStore
import ai.openclaw.android.gateway.DeviceIdentityStore
import ai.openclaw.android.gateway.GatewayDiscovery
import ai.openclaw.android.gateway.GatewayEndpoint
import ai.openclaw.android.node.*
import ai.openclaw.android.voice.VoiceConversationEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicLong

class NodeRuntime(context: Context) {
  private val appContext = context.applicationContext
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  val prefs = SecurePrefs(appContext)
  private val deviceAuthStore = DeviceAuthStore(prefs)
  val canvas = CanvasController()
  val camera = CameraCaptureManager(appContext)
  val location = LocationCaptureManager(appContext)
  val screenRecorder = ScreenRecordManager(appContext)
  val sms = SmsManager(appContext)
  private val json = Json { ignoreUnknownKeys = true }

  val externalAudioCaptureActive = MutableStateFlow(false)

  private val discovery = GatewayDiscovery(appContext, scope = scope)
  val gateways: StateFlow<List<GatewayEndpoint>> = discovery.gateways
  val discoveryStatusText: StateFlow<String> = discovery.statusText

  private val identityStore = DeviceIdentityStore(appContext)

  private val cameraHandler: CameraHandler = CameraHandler(
    appContext = appContext,
    camera = camera,
    externalAudioCaptureActive = externalAudioCaptureActive,
    showCameraHud = ::showCameraHud,
    triggerCameraFlash = ::triggerCameraFlash,
    invokeErrorFromThrowable = { invokeErrorFromThrowable(it) },
  )

  private val debugHandler: DebugHandler = DebugHandler(
    appContext = appContext,
    identityStore = identityStore,
  )

  private val appUpdateHandler: AppUpdateHandler = AppUpdateHandler(
    appContext = appContext,
    connectedEndpoint = { gw.connectedEndpoint },
  )

  private val locationHandler: LocationHandler = LocationHandler(
    appContext = appContext,
    location = location,
    json = json,
    isForeground = { _isForeground.value },
    locationMode = { locationMode.value },
    locationPreciseEnabled = { locationPreciseEnabled.value },
  )

  private val deviceHandler: DeviceHandler = DeviceHandler(
    appContext = appContext,
  )

  private val notificationsHandler: NotificationsHandler = NotificationsHandler(
    appContext = appContext,
  )

  private val systemHandler: SystemHandler = SystemHandler(
    appContext = appContext,
  )

  private val chatPushHandler: ChatPushHandler = ChatPushHandler(
    appContext = appContext,
  )

  private val photosHandler: PhotosHandler = PhotosHandler(
    appContext = appContext,
  )

  private val contactsHandler: ContactsHandler = ContactsHandler(
    appContext = appContext,
  )

  private val calendarHandler: CalendarHandler = CalendarHandler(
    appContext = appContext,
  )

  private val motionHandler: MotionHandler = MotionHandler(
    appContext = appContext,
  )

  private val screenHandler: ScreenHandler = ScreenHandler(
    screenRecorder = screenRecorder,
    setScreenRecordActive = { _screenRecordActive.value = it },
    invokeErrorFromThrowable = { invokeErrorFromThrowable(it) },
  )

  private val smsHandlerImpl: SmsHandler = SmsHandler(
    sms = sms,
  )

  private val a2uiHandler: A2UIHandler = A2UIHandler(
    canvas = canvas,
    json = json,
    getNodeCanvasHostUrl = { gw.nodeSession.currentCanvasHostUrl() },
    getOperatorCanvasHostUrl = { gw.operatorSession.currentCanvasHostUrl() },
  )

  private val connectionManager: ConnectionManager = ConnectionManager(
    prefs = prefs,
    cameraEnabled = { cameraEnabled.value },
    locationMode = { locationMode.value },
    voiceWakeMode = { VoiceWakeMode.Off },
    motionActivityAvailable = { motionHandler.isActivityAvailable() },
    motionPedometerAvailable = { motionHandler.isPedometerAvailable() },
    smsAvailable = { sms.canSendSms() },
    hasRecordAudioPermission = { hasRecordAudioPermission() },
    manualTls = { manualTls.value },
  )

  private val invokeDispatcher: InvokeDispatcher = InvokeDispatcher(
    canvas = canvas,
    cameraHandler = cameraHandler,
    locationHandler = locationHandler,
    deviceHandler = deviceHandler,
    notificationsHandler = notificationsHandler,
    systemHandler = systemHandler,
    chatPushHandler = chatPushHandler,
    photosHandler = photosHandler,
    contactsHandler = contactsHandler,
    calendarHandler = calendarHandler,
    motionHandler = motionHandler,
    screenHandler = screenHandler,
    smsHandler = smsHandlerImpl,
    a2uiHandler = a2uiHandler,
    debugHandler = debugHandler,
    appUpdateHandler = appUpdateHandler,
    isForeground = { _isForeground.value },
    cameraEnabled = { cameraEnabled.value },
    locationEnabled = { locationMode.value != LocationMode.Off },
    smsAvailable = { sms.canSendSms() },
    debugBuild = { BuildConfig.DEBUG },
    refreshNodeCanvasCapability = { gw.nodeSession.refreshNodeCanvasCapability() },
    onCanvasA2uiPush = { canvasManager.onA2uiPush() },
    onCanvasA2uiReset = { canvasManager.onA2uiReset() },
    motionActivityAvailable = { motionHandler.isActivityAvailable() },
    motionPedometerAvailable = { motionHandler.isPedometerAvailable() },
  )

  private val gw: GatewayConnectionManager =
    GatewayConnectionManager(
      scope = scope,
      identityStore = identityStore,
      deviceAuthStore = deviceAuthStore,
      connectionManager = connectionManager,
      prefs = prefs,
      gateways = gateways,
      onMainSessionKeyChanged = { key ->
        voice.talkMode.setMainSessionKey(key)
        chat.applyMainSessionKey(key)
      },
      onOperatorConnected = {
        voice.onGatewayConnectionChanged(true)
        scope.launch { voice.refreshTalkModeConfig() }
      },
      onOperatorDisconnected = { message, resolvedKey ->
        chat.applyMainSessionKey(resolvedKey)
        chat.onDisconnected(message)
        voice.onGatewayConnectionChanged(false)
      },
      onNodeConnected = { canvasManager.onNodeConnected() },
      onNodeDisconnected = { canvasManager.onNodeDisconnected() },
      onGatewayEvent = { event, payloadJson -> handleGatewayEvent(event, payloadJson) },
      handleInvoke = { req -> invokeDispatcher.handleInvoke(req.command, req.paramsJson) },
    )

  val isConnected: StateFlow<Boolean> get() = gw.isConnected
  val nodeConnected: StateFlow<Boolean> get() = gw.nodeConnected
  val statusText: StateFlow<String> get() = gw.statusText
  val pendingGatewayTrust: StateFlow<GatewayTrustPrompt?> get() = gw.pendingGatewayTrust
  val mainSessionKey: StateFlow<String> get() = gw.mainSessionKey
  val serverName: StateFlow<String?> get() = gw.serverName
  val remoteAddress: StateFlow<String?> get() = gw.remoteAddress
  val seamColorArgb: StateFlow<Long> get() = gw.seamColorArgb

  private val cameraHudSeq = AtomicLong(0)
  private val _cameraHud = MutableStateFlow<CameraHudState?>(null)
  val cameraHud: StateFlow<CameraHudState?> = _cameraHud.asStateFlow()

  private val _cameraFlashToken = MutableStateFlow(0L)
  val cameraFlashToken: StateFlow<Long> = _cameraFlashToken.asStateFlow()

  private val _screenRecordActive = MutableStateFlow(false)
  val screenRecordActive: StateFlow<Boolean> = _screenRecordActive.asStateFlow()

  val canvasA2uiHydrated: StateFlow<Boolean> get() = canvasManager.canvasA2uiHydrated
  val canvasRehydratePending: StateFlow<Boolean> get() = canvasManager.canvasRehydratePending
  val canvasRehydrateErrorText: StateFlow<String?> get() = canvasManager.canvasRehydrateErrorText

  private val _isForeground = MutableStateFlow(true)
  val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

  private val canvasManager: CanvasManager =
    CanvasManager(
      canvas = canvas,
      scope = scope,
      json = json,
      a2uiHandler = a2uiHandler,
      nodeSession = gw.nodeSession,
      isNodeConnected = { gw.nodeConnected.value },
      resolveMainSessionKey = gw::resolveMainSessionKey,
      displayName = { displayName.value },
      instanceId = { instanceId.value },
    )

  init {
    DeviceNotificationListenerService.setNodeEventSink { event, payloadJson ->
      scope.launch {
        gw.nodeSession.sendNodeEvent(event = event, payloadJson = payloadJson)
      }
    }
  }

  private val chat: ChatController =
    ChatController(
      scope = scope,
      session = gw.operatorSession,
      json = json,
      supportsChatSubscribe = false,
    )

  private val voice: VoiceCoordinator =
    VoiceCoordinator(
      appContext = appContext,
      scope = scope,
      prefs = prefs,
      session = gw.operatorSession,
      isConnected = { gw.operatorConnected },
      resolveMainSessionKey = gw::resolveMainSessionKey,
      chatThinkingLevel = { chatThinkingLevel.value },
      onAudioCaptureActiveChanged = { externalAudioCaptureActive.value = it },
    )

  val micStatusText: StateFlow<String> get() = voice.micStatusText
  val micLiveTranscript: StateFlow<String?> get() = voice.micLiveTranscript
  val micIsListening: StateFlow<Boolean> get() = voice.micIsListening
  val micEnabled: StateFlow<Boolean> get() = voice.micEnabled
  val micCooldown: StateFlow<Boolean> get() = voice.micCooldown
  val micQueuedMessages: StateFlow<List<String>> get() = voice.micQueuedMessages
  val micConversation: StateFlow<List<VoiceConversationEntry>> get() = voice.micConversation
  val micInputLevel: StateFlow<Float> get() = voice.micInputLevel
  val micIsSending: StateFlow<Boolean> get() = voice.micIsSending

  fun requestCanvasRehydrate(source: String = "manual", force: Boolean = true) {
    canvasManager.requestCanvasRehydrate(source, force)
  }

  val instanceId: StateFlow<String> = prefs.instanceId
  val displayName: StateFlow<String> = prefs.displayName
  val cameraEnabled: StateFlow<Boolean> = prefs.cameraEnabled
  val locationMode: StateFlow<LocationMode> = prefs.locationMode
  val locationPreciseEnabled: StateFlow<Boolean> = prefs.locationPreciseEnabled
  val preventSleep: StateFlow<Boolean> = prefs.preventSleep
  val manualEnabled: StateFlow<Boolean> = prefs.manualEnabled
  val manualHost: StateFlow<String> = prefs.manualHost
  val manualPort: StateFlow<Int> = prefs.manualPort
  val manualTls: StateFlow<Boolean> = prefs.manualTls
  val gatewayToken: StateFlow<String> = prefs.gatewayToken
  val onboardingCompleted: StateFlow<Boolean> = prefs.onboardingCompleted
  fun setGatewayToken(value: String) = prefs.setGatewayToken(value)
  fun setGatewayPassword(value: String) = prefs.setGatewayPassword(value)
  fun setOnboardingCompleted(value: Boolean) = prefs.setOnboardingCompleted(value)
  val lastDiscoveredStableId: StateFlow<String> = prefs.lastDiscoveredStableId
  val canvasDebugStatusEnabled: StateFlow<Boolean> = prefs.canvasDebugStatusEnabled

  val chatSessionKey: StateFlow<String> = chat.sessionKey
  val chatSessionId: StateFlow<String?> = chat.sessionId
  val chatMessages: StateFlow<List<ChatMessage>> = chat.messages
  val chatError: StateFlow<String?> = chat.errorText
  val chatHealthOk: StateFlow<Boolean> = chat.healthOk
  val chatThinkingLevel: StateFlow<String> = chat.thinkingLevel
  val chatStreamingAssistantText: StateFlow<String?> = chat.streamingAssistantText
  val chatPendingToolCalls: StateFlow<List<ChatPendingToolCall>> = chat.pendingToolCalls
  val chatSessions: StateFlow<List<ChatSessionEntry>> = chat.sessions
  val pendingRunCount: StateFlow<Int> = chat.pendingRunCount

  init {
    if (prefs.voiceWakeMode.value != VoiceWakeMode.Off) {
      prefs.setVoiceWakeMode(VoiceWakeMode.Off)
    }

    scope.launch {
      prefs.loadGatewayToken()
    }

    voice.startObservers()

    gw.startAutoConnect()

    scope.launch {
      combine(
        canvasDebugStatusEnabled,
        gw.statusText,
        gw.serverName,
        gw.remoteAddress,
      ) { debugEnabled, status, server, remote ->
        Quad(debugEnabled, status, server, remote)
      }.distinctUntilChanged()
        .collect { (debugEnabled, status, server, remote) ->
          canvas.setDebugStatusEnabled(debugEnabled)
          if (!debugEnabled) return@collect
          canvas.setDebugStatus(status, server ?: remote)
        }
    }
  }

  fun setForeground(value: Boolean) {
    _isForeground.value = value
  }

  fun setDisplayName(value: String) {
    prefs.setDisplayName(value)
  }

  fun setCameraEnabled(value: Boolean) {
    prefs.setCameraEnabled(value)
  }

  fun setLocationMode(mode: LocationMode) {
    prefs.setLocationMode(mode)
  }

  fun setLocationPreciseEnabled(value: Boolean) {
    prefs.setLocationPreciseEnabled(value)
  }

  fun setPreventSleep(value: Boolean) {
    prefs.setPreventSleep(value)
  }

  fun setManualEnabled(value: Boolean) {
    prefs.setManualEnabled(value)
  }

  fun setManualHost(value: String) {
    prefs.setManualHost(value)
  }

  fun setManualPort(value: Int) {
    prefs.setManualPort(value)
  }

  fun setManualTls(value: Boolean) {
    prefs.setManualTls(value)
  }

  fun setCanvasDebugStatusEnabled(value: Boolean) {
    prefs.setCanvasDebugStatusEnabled(value)
  }

  fun setVoiceScreenActive(active: Boolean) {
    voice.setVoiceScreenActive(active)
  }

  fun setMicEnabled(value: Boolean) {
    voice.setMicEnabled(value)
  }

  val speakerEnabled: StateFlow<Boolean>
    get() = voice.speakerEnabled

  fun setSpeakerEnabled(value: Boolean) {
    voice.setSpeakerEnabled(value)
  }

  fun connect(endpoint: GatewayEndpoint) = gw.connect(endpoint)

  fun connectManual() = gw.connectManual()

  fun disconnect() = gw.disconnect()

  fun refreshGatewayConnection() = gw.refreshGatewayConnection()

  fun acceptGatewayTrustPrompt() = gw.acceptGatewayTrustPrompt()

  fun declineGatewayTrustPrompt() = gw.declineGatewayTrustPrompt()

  private fun hasRecordAudioPermission(): Boolean =
    ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
      PackageManager.PERMISSION_GRANTED

  fun handleCanvasA2UIActionFromWebView(payloadJson: String) {
    canvasManager.handleCanvasA2UIActionFromWebView(payloadJson)
  }

  fun loadChat(sessionKey: String) {
    val key = sessionKey.trim().ifEmpty { gw.resolveMainSessionKey() }
    chat.load(key)
  }

  fun refreshChat() {
    chat.refresh()
  }

  fun refreshChatSessions(limit: Int? = null) {
    chat.refreshSessions(limit = limit)
  }

  fun setChatThinkingLevel(level: String) {
    chat.setThinkingLevel(level)
  }

  fun switchChatSession(sessionKey: String) {
    chat.switchSession(sessionKey)
  }

  fun abortChat() {
    chat.abort()
  }

  fun sendChat(message: String, thinking: String, attachments: List<OutgoingAttachment>) {
    chat.sendMessage(message = message, thinkingLevel = thinking, attachments = attachments)
  }

  private fun handleGatewayEvent(event: String, payloadJson: String?) {
    voice.handleGatewayEvent(event, payloadJson)
    chat.handleGatewayEvent(event, payloadJson)
  }

  private fun triggerCameraFlash() {
    // Token is used as a pulse trigger; value doesn't matter as long as it changes.
    _cameraFlashToken.value = SystemClock.elapsedRealtimeNanos()
  }

  private fun showCameraHud(message: String, kind: CameraHudKind, autoHideMs: Long? = null) {
    val token = cameraHudSeq.incrementAndGet()
    _cameraHud.value = CameraHudState(token = token, kind = kind, message = message)

    if (autoHideMs != null && autoHideMs > 0) {
      scope.launch {
        delay(autoHideMs)
        if (_cameraHud.value?.token == token) _cameraHud.value = null
      }
    }
  }

}
