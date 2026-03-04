package ai.openclaw.android

import android.util.Log
import ai.openclaw.android.gateway.DeviceAuthStore
import ai.openclaw.android.gateway.DeviceIdentityStore
import ai.openclaw.android.gateway.GatewayEndpoint
import ai.openclaw.android.gateway.GatewayHealthMonitor
import ai.openclaw.android.gateway.GatewaySession
import ai.openclaw.android.gateway.probeGatewayTlsFingerprint
import ai.openclaw.android.node.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class GatewayTrustPrompt(
  val endpoint: GatewayEndpoint,
  val fingerprintSha256: String,
)

/**
 * Owns the gateway WebSocket sessions (operator + node), health monitoring, connection lifecycle,
 * and session key management. Extracted from NodeRuntime.
 */
internal class GatewayConnectionManager(
  private val scope: CoroutineScope,
  private val identityStore: DeviceIdentityStore,
  private val deviceAuthStore: DeviceAuthStore,
  private val connectionManager: ConnectionManager,
  private val prefs: SecurePrefs,
  private val gateways: StateFlow<List<GatewayEndpoint>>,
  /** Called when the resolved main session key changes (voice + chat must update). */
  private val onMainSessionKeyChanged: (String) -> Unit,
  /** Called after operator connects (voice coordination). */
  private val onOperatorConnected: () -> Unit,
  /** Called after operator disconnects; receives message and resolved session key for chat. */
  private val onOperatorDisconnected: (message: String, resolvedKey: String) -> Unit,
  /** Called after node session connects. */
  private val onNodeConnected: () -> Unit,
  /** Called after node session disconnects. */
  private val onNodeDisconnected: (String) -> Unit,
  /** Fan-out for gateway push events. */
  private val onGatewayEvent: (event: String, payloadJson: String?) -> Unit,
  /** Invoke handler (InvokeDispatcher). */
  private val handleInvoke: suspend (GatewaySession.InvokeRequest) -> GatewaySession.InvokeResult,
) {
  private val json = Json { ignoreUnknownKeys = true }

  private val _isConnected = MutableStateFlow(false)
  val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

  private val _nodeConnected = MutableStateFlow(false)
  val nodeConnected: StateFlow<Boolean> = _nodeConnected.asStateFlow()

  private val _statusText = MutableStateFlow("Offline")
  val statusText: StateFlow<String> = _statusText.asStateFlow()

  private val _pendingGatewayTrust = MutableStateFlow<GatewayTrustPrompt?>(null)
  val pendingGatewayTrust: StateFlow<GatewayTrustPrompt?> = _pendingGatewayTrust.asStateFlow()

  private val _mainSessionKey = MutableStateFlow("main")
  val mainSessionKey: StateFlow<String> = _mainSessionKey.asStateFlow()

  private val _serverName = MutableStateFlow<String?>(null)
  val serverName: StateFlow<String?> = _serverName.asStateFlow()

  private val _remoteAddress = MutableStateFlow<String?>(null)
  val remoteAddress: StateFlow<String?> = _remoteAddress.asStateFlow()

  private val _seamColorArgb = MutableStateFlow(DEFAULT_SEAM_COLOR_ARGB)
  val seamColorArgb: StateFlow<Long> = _seamColorArgb.asStateFlow()

  var connectedEndpoint: GatewayEndpoint? = null
    private set

  var operatorConnected = false
    private set
  private var operatorStatusText: String = "Offline"
  private var nodeStatusText: String = "Offline"

  private val healthMonitor = GatewayHealthMonitor(scope = scope)

  val operatorSession: GatewaySession =
    GatewaySession(
      scope = scope,
      identityStore = identityStore,
      deviceAuthStore = deviceAuthStore,
      onConnected = { name, remote, mainSessionKey ->
        operatorConnected = true
        operatorStatusText = "Connected"
        _serverName.value = name
        _remoteAddress.value = remote
        _seamColorArgb.value = DEFAULT_SEAM_COLOR_ARGB
        applyMainSessionKey(mainSessionKey)
        updateStatus()
        onOperatorConnected()
        healthMonitor.start(
          check = {
            try {
              val res = operatorSession.request("health", null, timeoutMs = 5_000)
              res.contains("\"ok\":true") || res.isEmpty() || res == "{}"
            } catch (err: Throwable) {
              val msg = err.message?.lowercase().orEmpty()
              // Authorization errors mean the endpoint is reachable; don't thrash the connection.
              msg.contains("unauthorized role") || msg.contains("missing scope")
            }
          },
          onFailure = {
            Log.w("GatewayConnectionManager", "Health check failed $it times — reconnecting")
            operatorSession.reconnect()
            nodeSession.reconnect()
          },
        )
        scope.launch { refreshBrandingFromGateway() }
      },
      onDisconnected = { message ->
        healthMonitor.stop()
        operatorConnected = false
        operatorStatusText = message
        _serverName.value = null
        _remoteAddress.value = null
        _seamColorArgb.value = DEFAULT_SEAM_COLOR_ARGB
        if (!isCanonicalMainSessionKey(_mainSessionKey.value)) {
          _mainSessionKey.value = "main"
        }
        updateStatus()
        onOperatorDisconnected(message, resolveMainSessionKey())
      },
      onEvent = { event, payloadJson -> onGatewayEvent(event, payloadJson) },
    )

  val nodeSession: GatewaySession =
    GatewaySession(
      scope = scope,
      identityStore = identityStore,
      deviceAuthStore = deviceAuthStore,
      onConnected = { _, _, _ ->
        _nodeConnected.value = true
        nodeStatusText = "Connected"
        updateStatus()
        onNodeConnected()
      },
      onDisconnected = { message ->
        _nodeConnected.value = false
        nodeStatusText = message
        updateStatus()
        onNodeDisconnected(message)
      },
      onEvent = { _, _ -> },
      onInvoke = { req -> handleInvoke(req) },
      onTlsFingerprint = { stableId, fingerprint ->
        prefs.saveGatewayTlsFingerprint(stableId, fingerprint)
      },
    )

  // ── Auto-connect observer ─────────────────────────────────────────────────

  /** Start observing discovered gateways and auto-connect when conditions are met. */
  fun startAutoConnect() {
    var didAutoConnect = false
    scope.launch(Dispatchers.Default) {
      gateways.collect { list ->
        if (list.isNotEmpty() && prefs.lastDiscoveredStableId.value.trim().isEmpty()) {
          prefs.setLastDiscoveredStableId(list.first().stableId)
        }
        if (didAutoConnect) return@collect
        if (_isConnected.value) return@collect

        if (prefs.manualEnabled.value) {
          val host = prefs.manualHost.value.trim()
          val port = prefs.manualPort.value
          if (host.isNotEmpty() && port in 1..65535) {
            if (!prefs.manualTls.value) return@collect
            val stableId = GatewayEndpoint.manual(host = host, port = port).stableId
            if (prefs.loadGatewayTlsFingerprint(stableId)?.trim().orEmpty().isEmpty()) return@collect
            didAutoConnect = true
            connect(GatewayEndpoint.manual(host = host, port = port))
          }
          return@collect
        }

        val targetStableId = prefs.lastDiscoveredStableId.value.trim()
        if (targetStableId.isEmpty()) return@collect
        val target = list.firstOrNull { it.stableId == targetStableId } ?: return@collect
        if (prefs.loadGatewayTlsFingerprint(target.stableId)?.trim().orEmpty().isEmpty()) return@collect
        didAutoConnect = true
        connect(target)
      }
    }
  }

  // ── Connection methods ────────────────────────────────────────────────────

  fun connect(endpoint: GatewayEndpoint) {
    val tls = connectionManager.resolveTlsParams(endpoint)
    if (tls?.required == true && tls.expectedFingerprint.isNullOrBlank()) {
      _statusText.value = "Verify gateway TLS fingerprint…"
      scope.launch {
        val fp = probeGatewayTlsFingerprint(endpoint.host, endpoint.port) ?: run {
          _statusText.value = "Connection failed: couldn't verify TLS certificate"
          return@launch
        }
        _pendingGatewayTrust.value = GatewayTrustPrompt(endpoint = endpoint, fingerprintSha256 = fp)
      }
      return
    }
    connectedEndpoint = endpoint
    operatorStatusText = "Connecting…"
    nodeStatusText = "Connecting…"
    updateStatus()
    val token = prefs.loadGatewayToken()
    val password = prefs.loadGatewayPassword()
    operatorSession.connect(endpoint, token, password, connectionManager.buildOperatorConnectOptions(), tls)
    nodeSession.connect(endpoint, token, password, connectionManager.buildNodeConnectOptions(), tls)
  }

  fun connectManual() {
    val host = prefs.manualHost.value.trim()
    val port = prefs.manualPort.value
    if (host.isEmpty() || port <= 0 || port > 65535) {
      _statusText.value = "Invalid address — check host and port"
      return
    }
    connect(GatewayEndpoint.manual(host = host, port = port))
  }

  fun disconnect() {
    connectedEndpoint = null
    _pendingGatewayTrust.value = null
    operatorSession.disconnect()
    nodeSession.disconnect()
  }

  fun refreshGatewayConnection() {
    val endpoint = connectedEndpoint ?: run {
      _statusText.value = "Not connected to any gateway"
      return
    }
    operatorStatusText = "Connecting…"
    updateStatus()
    val token = prefs.loadGatewayToken()
    val password = prefs.loadGatewayPassword()
    val tls = connectionManager.resolveTlsParams(endpoint)
    operatorSession.connect(endpoint, token, password, connectionManager.buildOperatorConnectOptions(), tls)
    nodeSession.connect(endpoint, token, password, connectionManager.buildNodeConnectOptions(), tls)
    operatorSession.reconnect()
    nodeSession.reconnect()
  }

  fun acceptGatewayTrustPrompt() {
    val prompt = _pendingGatewayTrust.value ?: return
    _pendingGatewayTrust.value = null
    prefs.saveGatewayTlsFingerprint(prompt.endpoint.stableId, prompt.fingerprintSha256)
    connect(prompt.endpoint)
  }

  fun declineGatewayTrustPrompt() {
    _pendingGatewayTrust.value = null
    _statusText.value = "Offline"
  }

  fun resolveMainSessionKey(): String {
    val trimmed = _mainSessionKey.value.trim()
    return if (trimmed.isEmpty()) "main" else trimmed
  }

  // ── Private ───────────────────────────────────────────────────────────────

  private fun applyMainSessionKey(candidate: String?) {
    val trimmed = normalizeMainKey(candidate) ?: return
    if (isCanonicalMainSessionKey(_mainSessionKey.value)) return
    if (_mainSessionKey.value == trimmed) return
    _mainSessionKey.value = trimmed
    onMainSessionKeyChanged(trimmed)
  }

  private fun updateStatus() {
    _isConnected.value = operatorConnected
    val operator = operatorStatusText.trim()
    val node = nodeStatusText.trim()
    _statusText.value =
      when {
        operatorConnected && _nodeConnected.value -> "Connected"
        operatorConnected && !_nodeConnected.value -> "Connected (limited)"
        !operatorConnected && _nodeConnected.value ->
          if (operator.isNotEmpty() && operator != "Offline") {
            "Connected ($operator)"
          } else {
            "Connected (reconnecting…)"
          }
        operator.isNotBlank() && operator != "Offline" -> operator
        else -> node
      }
  }

  private suspend fun refreshBrandingFromGateway() {
    if (!_isConnected.value) return
    try {
      val res = operatorSession.request("config.get", "{}")
      val root = json.parseToJsonElement(res).asObjectOrNull()
      val config = root?.get("config").asObjectOrNull()
      val ui = config?.get("ui").asObjectOrNull()
      val raw = ui?.get("seamColor").asStringOrNull()?.trim()
      val sessionCfg = config?.get("session").asObjectOrNull()
      val mainKey = normalizeMainKey(sessionCfg?.get("mainKey").asStringOrNull())
      applyMainSessionKey(mainKey)
      _seamColorArgb.value = parseHexColorArgb(raw) ?: DEFAULT_SEAM_COLOR_ARGB
    } catch (_: Throwable) {
      // ignore
    }
  }
}
