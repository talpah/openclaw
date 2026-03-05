package ai.openclaw.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ai.openclaw.android.MainViewModel
import ai.openclaw.android.chat.ChatSessionEntry
import ai.openclaw.android.ui.chat.friendlySessionName
import ai.openclaw.android.ui.chat.resolveSessionChoices

private enum class HomeTab(
  val label: String,
  val icon: ImageVector,
) {
  Connect(label = "Connect", icon = Icons.Default.CheckCircle),
  Chat(label = "Chat", icon = Icons.Default.ChatBubble),
  Voice(label = "Voice", icon = Icons.Default.RecordVoiceOver),
  Screen(label = "Canvas", icon = Icons.AutoMirrored.Filled.ScreenShare),
  Settings(label = "Settings", icon = Icons.Default.Settings),
}

private enum class StatusVisual {
  Connected,
  Connecting,
  Warning,
  Error,
  Offline,
}

@Composable
fun PostOnboardingTabs(viewModel: MainViewModel, modifier: Modifier = Modifier) {
  var activeTab by rememberSaveable { mutableStateOf(HomeTab.Chat) }

  LaunchedEffect(activeTab) {
    viewModel.setVoiceScreenActive(activeTab == HomeTab.Voice)
  }

  val statusText by viewModel.statusText.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()
  val thinkingLevel by viewModel.chatThinkingLevel.collectAsState()
  val sessions by viewModel.chatSessions.collectAsState()
  val sessionKey by viewModel.chatSessionKey.collectAsState()
  val mainSessionKey by viewModel.mainSessionKey.collectAsState()

  val statusVisual =
    remember(statusText, isConnected) {
      val lower = statusText.lowercase()
      when {
        isConnected -> StatusVisual.Connected
        lower.contains("connecting") || lower.contains("reconnecting") -> StatusVisual.Connecting
        lower.contains("pairing") || lower.contains("approval") || lower.contains("auth") -> StatusVisual.Warning
        lower.contains("error") || lower.contains("failed") -> StatusVisual.Error
        else -> StatusVisual.Offline
      }
    }

  Scaffold(
    modifier = modifier,
    containerColor = Color.Transparent,
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    topBar = {
      AppTopBar(
        activeTab = activeTab,
        onSelectTab = { activeTab = it },
        statusVisual = statusVisual,
        thinkingLevel = thinkingLevel,
        onSetThinkingLevel = { viewModel.setChatThinkingLevel(it) },
        sessionKey = sessionKey,
        sessions = sessions,
        mainSessionKey = mainSessionKey,
        onSelectSession = { viewModel.switchChatSession(it) },
      )
    },
  ) { innerPadding ->
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .consumeWindowInsets(innerPadding)
          .navigationBarsPadding()
          .background(mobileBackgroundGradient),
    ) {
      when (activeTab) {
        HomeTab.Connect -> ConnectTabScreen(viewModel = viewModel)
        HomeTab.Chat -> ChatSheet(viewModel = viewModel)
        HomeTab.Voice -> VoiceTabScreen(viewModel = viewModel)
        HomeTab.Screen -> ScreenTabScreen(viewModel = viewModel)
        HomeTab.Settings -> SettingsSheet(viewModel = viewModel)
      }
    }
  }
}

@Composable
private fun AppTopBar(
  activeTab: HomeTab,
  onSelectTab: (HomeTab) -> Unit,
  statusVisual: StatusVisual,
  thinkingLevel: String,
  onSetThinkingLevel: (String) -> Unit,
  sessionKey: String,
  sessions: List<ChatSessionEntry>,
  mainSessionKey: String,
  onSelectSession: (String) -> Unit,
) {
  val safeInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

  Surface(
    modifier = Modifier.fillMaxWidth().windowInsetsPadding(safeInsets),
    color = Color.White.copy(alpha = 0.97f),
    shadowElevation = 2.dp,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      TabDropdown(activeTab = activeTab, onSelect = onSelectTab)

      if (activeTab == HomeTab.Chat || activeTab == HomeTab.Voice) {
        ThinkingDropdown(thinkingLevel = thinkingLevel, onSet = onSetThinkingLevel)
      }

      if (activeTab == HomeTab.Chat) {
        SessionDropdown(
          sessionKey = sessionKey,
          sessions = sessions,
          mainSessionKey = mainSessionKey,
          onSelectSession = onSelectSession,
          modifier = Modifier.weight(1f),
        )
      } else {
        Spacer(modifier = Modifier.weight(1f))
      }

      StatusDot(statusVisual = statusVisual)
    }
  }
}

@Composable
private fun TabDropdown(activeTab: HomeTab, onSelect: (HomeTab) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    Surface(
      onClick = { expanded = true },
      shape = RoundedCornerShape(10.dp),
      color = mobileAccentSoft,
      border = BorderStroke(1.dp, Color(0xFFD5E2FA)),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Icon(activeTab.icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = mobileAccent)
        Text(activeTab.label, style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileAccent)
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp), tint = mobileAccent)
      }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Color.White) {
      HomeTab.entries.forEach { tab ->
        val active = tab == activeTab
        DropdownMenuItem(
          text = { Text(tab.label, style = mobileCallout, color = if (active) mobileAccent else mobileText) },
          leadingIcon = {
            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (active) mobileAccent else mobileTextSecondary)
          },
          trailingIcon = {
            if (active) Text("✓", style = mobileCallout, color = mobileAccent)
          },
          onClick = { onSelect(tab); expanded = false },
        )
      }
    }
  }
}

@Composable
private fun ThinkingDropdown(thinkingLevel: String, onSet: (String) -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  Box {
    Surface(
      onClick = { expanded = true },
      shape = RoundedCornerShape(10.dp),
      color = Color.White,
      border = BorderStroke(1.dp, mobileBorderStrong),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(thinkingLabel(thinkingLevel), style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold), color = mobileText)
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp), tint = mobileTextSecondary)
      }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Color.White) {
      listOf("off", "minimal", "low", "medium", "high", "adaptive").forEach { level ->
        val active = level == thinkingLevel.trim().lowercase()
        DropdownMenuItem(
          text = { Text(thinkingLabel(level), style = mobileCallout, color = if (active) mobileAccent else mobileText) },
          trailingIcon = { if (active) Text("✓", style = mobileCallout, color = mobileAccent) },
          onClick = { onSet(level); expanded = false },
        )
      }
    }
  }
}

@Composable
private fun SessionDropdown(
  sessionKey: String,
  sessions: List<ChatSessionEntry>,
  mainSessionKey: String,
  onSelectSession: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val sessionOptions = resolveSessionChoices(sessionKey, sessions, mainSessionKey = mainSessionKey)
  val currentLabel = friendlySessionName(sessionOptions.firstOrNull { it.key == sessionKey }?.displayName ?: sessionKey)

  Box(modifier = modifier) {
    Surface(
      onClick = { expanded = true },
      shape = RoundedCornerShape(10.dp),
      color = Color.White,
      border = BorderStroke(1.dp, mobileBorderStrong),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = currentLabel,
          style = mobileCaption1.copy(fontWeight = FontWeight.SemiBold),
          color = mobileText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp), tint = mobileTextSecondary)
      }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = Color.White) {
      sessionOptions.forEach { entry ->
        val active = entry.key == sessionKey
        DropdownMenuItem(
          text = {
            Text(
              friendlySessionName(entry.displayName ?: entry.key),
              style = mobileCallout,
              color = if (active) mobileAccent else mobileText,
            )
          },
          trailingIcon = { if (active) Text("✓", style = mobileCallout, color = mobileAccent) },
          onClick = { onSelectSession(entry.key); expanded = false },
        )
      }
    }
  }
}

@Composable
private fun StatusDot(statusVisual: StatusVisual) {
  val color =
    when (statusVisual) {
      StatusVisual.Connected -> mobileSuccess
      StatusVisual.Connecting -> mobileAccent
      StatusVisual.Warning -> mobileWarning
      StatusVisual.Error -> mobileDanger
      StatusVisual.Offline -> mobileTextTertiary
    }
  Surface(
    modifier = Modifier.size(10.dp),
    shape = RoundedCornerShape(999.dp),
    color = color,
  ) {}
}

@Composable
private fun ScreenTabScreen(viewModel: MainViewModel) {
  val isConnected by viewModel.isConnected.collectAsState()
  val isNodeConnected by viewModel.isNodeConnected.collectAsState()
  val canvasUrl by viewModel.canvasCurrentUrl.collectAsState()
  val canvasA2uiHydrated by viewModel.canvasA2uiHydrated.collectAsState()
  val canvasRehydratePending by viewModel.canvasRehydratePending.collectAsState()
  val canvasRehydrateErrorText by viewModel.canvasRehydrateErrorText.collectAsState()
  val isA2uiUrl = canvasUrl?.contains("/__openclaw__/a2ui/") == true
  val showRestoreCta = isConnected && isNodeConnected && (canvasUrl.isNullOrBlank() || (isA2uiUrl && !canvasA2uiHydrated))
  val restoreCtaText =
    when {
      canvasRehydratePending -> "Restore requested. Waiting for agent…"
      !canvasRehydrateErrorText.isNullOrBlank() -> canvasRehydrateErrorText!!
      else -> "Canvas reset. Tap to restore dashboard."
    }

  Box(modifier = Modifier.fillMaxSize()) {
    CanvasScreen(viewModel = viewModel, modifier = Modifier.fillMaxSize())

    if (showRestoreCta) {
      Surface(
        onClick = {
          if (canvasRehydratePending) return@Surface
          viewModel.requestCanvasRehydrate(source = "screen_tab_cta")
        },
        modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 16.dp),
        shape = RoundedCornerShape(12.dp),
        color = mobileSurface.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, mobileBorder),
        shadowElevation = 4.dp,
      ) {
        Text(
          text = restoreCtaText,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          style = mobileCallout.copy(fontWeight = FontWeight.Medium),
          color = mobileText,
        )
      }
    }
  }
}

private fun thinkingLabel(raw: String): String =
  when (raw.trim().lowercase()) {
    "minimal" -> "Minimal"
    "low" -> "Low"
    "medium" -> "Med"
    "high" -> "High"
    "adaptive" -> "Adaptive"
    else -> "Off"
  }
