package ai.openclaw.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.openclaw.android.GatewayTrustPrompt
import ai.openclaw.android.LocationMode
import ai.openclaw.android.MainViewModel
import ai.openclaw.android.ui.onboarding.FinalStep
import ai.openclaw.android.ui.onboarding.GatewayInputMode
import ai.openclaw.android.ui.onboarding.GatewayStep
import ai.openclaw.android.ui.onboarding.OnboardingNavBar
import ai.openclaw.android.ui.onboarding.OnboardingStep
import ai.openclaw.android.ui.onboarding.PermissionsStep
import ai.openclaw.android.ui.onboarding.StepRailWrap
import ai.openclaw.android.ui.onboarding.WelcomeStep
import ai.openclaw.android.ui.onboarding.onboardingAccent
import ai.openclaw.android.ui.onboarding.onboardingBackgroundGradient
import ai.openclaw.android.ui.onboarding.onboardingCaption1Style
import ai.openclaw.android.ui.onboarding.onboardingDisplayStyle
import ai.openclaw.android.ui.onboarding.onboardingText
import ai.openclaw.android.ui.onboarding.rememberOnboardingPermissionState
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

@Composable
fun OnboardingFlow(viewModel: MainViewModel, modifier: Modifier = Modifier) {
  val context = androidx.compose.ui.platform.LocalContext.current
  val statusText by viewModel.statusText.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()
  val serverName by viewModel.serverName.collectAsState()
  val remoteAddress by viewModel.remoteAddress.collectAsState()
  val persistedGatewayToken by viewModel.gatewayToken.collectAsState()
  val pendingTrust by viewModel.pendingGatewayTrust.collectAsState()

  var step by rememberSaveable { mutableStateOf(OnboardingStep.Welcome) }
  var setupCode by rememberSaveable { mutableStateOf("") }
  var gatewayUrl by rememberSaveable { mutableStateOf("") }
  var gatewayPassword by rememberSaveable { mutableStateOf("") }
  var gatewayInputMode by rememberSaveable { mutableStateOf(GatewayInputMode.SetupCode) }
  var gatewayAdvancedOpen by rememberSaveable { mutableStateOf(false) }
  var manualHost by rememberSaveable { mutableStateOf("10.0.2.2") }
  var manualPort by rememberSaveable { mutableStateOf("18789") }
  var manualTls by rememberSaveable { mutableStateOf(false) }
  var gatewayError by rememberSaveable { mutableStateOf<String?>(null) }
  var attemptedConnect by rememberSaveable { mutableStateOf(false) }

  val perm = rememberOnboardingPermissionState(context)

  val qrScanLauncher =
    rememberLauncherForActivityResult(ScanContract()) { result ->
      val contents = result.contents?.trim().orEmpty()
      if (contents.isEmpty()) return@rememberLauncherForActivityResult
      val scannedSetupCode = resolveScannedSetupCode(contents)
      if (scannedSetupCode == null) {
        gatewayError = "QR code did not contain a valid setup code."
        return@rememberLauncherForActivityResult
      }
      setupCode = scannedSetupCode
      gatewayInputMode = GatewayInputMode.SetupCode
      gatewayError = null
      attemptedConnect = false
    }

  if (pendingTrust != null) {
    val prompt = pendingTrust!!
    AlertDialog(
      onDismissRequest = { viewModel.declineGatewayTrustPrompt() },
      title = { Text("Trust this gateway?") },
      text = {
        Text(
          "First-time TLS connection.\n\nVerify this SHA-256 fingerprint before trusting:\n${prompt.fingerprintSha256}",
        )
      },
      confirmButton = {
        TextButton(onClick = { viewModel.acceptGatewayTrustPrompt() }) {
          Text("Trust and continue")
        }
      },
      dismissButton = {
        TextButton(onClick = { viewModel.declineGatewayTrustPrompt() }) { Text("Cancel") }
      },
    )
  }

  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(Brush.verticalGradient(onboardingBackgroundGradient)),
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .imePadding()
          .windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
          )
          .navigationBarsPadding()
          .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        Column(
          modifier = Modifier.padding(top = 12.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            "FIRST RUN",
            style =
              onboardingCaption1Style.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp),
            color = onboardingAccent,
          )
          Text(
            "OpenClaw\nMobile Setup",
            style = onboardingDisplayStyle.copy(lineHeight = 38.sp),
            color = onboardingText,
          )
          Text("Step ${step.index} of 4", style = onboardingCaption1Style, color = onboardingAccent)
        }
        StepRailWrap(current = step)

        when (step) {
          OnboardingStep.Welcome -> WelcomeStep()
          OnboardingStep.Gateway ->
            GatewayStep(
              inputMode = gatewayInputMode,
              advancedOpen = gatewayAdvancedOpen,
              setupCode = setupCode,
              manualHost = manualHost,
              manualPort = manualPort,
              manualTls = manualTls,
              gatewayToken = persistedGatewayToken,
              gatewayPassword = gatewayPassword,
              gatewayError = gatewayError,
              onScanQrClick = {
                gatewayError = null
                qrScanLauncher.launch(
                  ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt("Scan OpenClaw onboarding QR")
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                  },
                )
              },
              onAdvancedOpenChange = { gatewayAdvancedOpen = it },
              onInputModeChange = { gatewayInputMode = it; gatewayError = null },
              onSetupCodeChange = { setupCode = it; gatewayError = null },
              onManualHostChange = { manualHost = it; gatewayError = null },
              onManualPortChange = { manualPort = it; gatewayError = null },
              onManualTlsChange = { manualTls = it },
              onTokenChange = viewModel::setGatewayToken,
              onPasswordChange = { gatewayPassword = it },
            )
          OnboardingStep.Permissions -> PermissionsStep(state = perm)
          OnboardingStep.FinalCheck ->
            FinalStep(
              parsedGateway = parseGatewayEndpoint(gatewayUrl),
              statusText = statusText,
              isConnected = isConnected,
              serverName = serverName,
              remoteAddress = remoteAddress,
              attemptedConnect = attemptedConnect,
              enabledPermissions = perm.enabledSummary,
              methodLabel =
                if (gatewayInputMode == GatewayInputMode.SetupCode) "QR / Setup Code" else "Manual",
            )
        }
      }

      Spacer(Modifier.height(12.dp))

      OnboardingNavBar(
        step = step,
        isConnected = isConnected,
        onBack = {
          step =
            when (step) {
              OnboardingStep.Welcome -> OnboardingStep.Welcome
              OnboardingStep.Gateway -> OnboardingStep.Welcome
              OnboardingStep.Permissions -> OnboardingStep.Gateway
              OnboardingStep.FinalCheck -> OnboardingStep.Permissions
            }
        },
        onNext = {
          when (step) {
            OnboardingStep.Welcome -> step = OnboardingStep.Gateway
            OnboardingStep.Gateway -> {
              if (gatewayInputMode == GatewayInputMode.SetupCode) {
                val parsedSetup = decodeGatewaySetupCode(setupCode)
                if (parsedSetup == null) {
                  gatewayError = "Scan QR code first, or use Advanced setup."
                  return@OnboardingNavBar
                }
                val parsedGateway = parseGatewayEndpoint(parsedSetup.url)
                if (parsedGateway == null) {
                  gatewayError = "Setup code has invalid gateway URL."
                  return@OnboardingNavBar
                }
                gatewayUrl = parsedSetup.url
                parsedSetup.token?.let { viewModel.setGatewayToken(it) }
                gatewayPassword = parsedSetup.password.orEmpty()
              } else {
                val manualUrl = composeGatewayManualUrl(manualHost, manualPort, manualTls)
                val parsedGateway = manualUrl?.let(::parseGatewayEndpoint)
                if (parsedGateway == null) {
                  gatewayError = "Manual endpoint is invalid."
                  return@OnboardingNavBar
                }
                gatewayUrl = parsedGateway.displayUrl
              }
              step = OnboardingStep.Permissions
            }
            OnboardingStep.Permissions -> {
              viewModel.setCameraEnabled(perm.enableCamera)
              viewModel.setLocationMode(if (perm.enableLocation) LocationMode.WhileUsing else LocationMode.Off)
              perm.proceedFromPermissions { step = OnboardingStep.FinalCheck }
            }
            OnboardingStep.FinalCheck -> {
              if (isConnected) {
                viewModel.setOnboardingCompleted(true)
              } else {
                val parsed = parseGatewayEndpoint(gatewayUrl)
                if (parsed == null) {
                  step = OnboardingStep.Gateway
                  gatewayError = "Invalid gateway URL."
                  return@OnboardingNavBar
                }
                val token = persistedGatewayToken.trim()
                val password = gatewayPassword.trim()
                attemptedConnect = true
                viewModel.setManualEnabled(true)
                viewModel.setManualHost(parsed.host)
                viewModel.setManualPort(parsed.port)
                viewModel.setManualTls(parsed.tls)
                if (token.isNotEmpty()) viewModel.setGatewayToken(token)
                viewModel.setGatewayPassword(password)
                viewModel.connectManual()
              }
            }
          }
        },
      )
    }
  }
}
