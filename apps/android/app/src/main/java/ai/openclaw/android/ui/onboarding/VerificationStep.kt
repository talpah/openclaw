package ai.openclaw.android.ui.onboarding

import ai.openclaw.android.ui.GatewayEndpointConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun FinalStep(
  parsedGateway: GatewayEndpointConfig?,
  statusText: String,
  isConnected: Boolean,
  serverName: String?,
  remoteAddress: String?,
  attemptedConnect: Boolean,
  enabledPermissions: String,
  methodLabel: String,
) {
  StepShell(title = "Review") {
    SummaryField(label = "Method", value = methodLabel)
    SummaryField(label = "Gateway", value = parsedGateway?.displayUrl ?: "Invalid gateway URL")
    SummaryField(label = "Enabled Permissions", value = enabledPermissions)
    if (!attemptedConnect) {
      Text(
        "Press Connect to verify gateway reachability and auth.",
        style = onboardingCalloutStyle,
        color = onboardingTextSecondary,
      )
    } else {
      Text(
        "Status: $statusText",
        style = onboardingCalloutStyle,
        color = if (isConnected) onboardingSuccess else onboardingTextSecondary,
      )
      if (isConnected) {
        Text(
          "Connected to ${serverName ?: remoteAddress ?: "gateway"}",
          style = onboardingCalloutStyle,
          color = onboardingSuccess,
        )
      } else {
        GuideBlock(title = "Pairing Required") {
          Text(
            "Run these on the gateway host:",
            style = onboardingCalloutStyle,
            color = onboardingTextSecondary,
          )
          CommandBlock("openclaw devices list")
          CommandBlock("openclaw devices approve <requestId>")
          Text(
            "Then tap Connect again.",
            style = onboardingCalloutStyle,
            color = onboardingTextSecondary,
          )
        }
      }
    }
  }
}

@Composable
private fun SummaryField(label: String, value: String) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      label,
      style =
        onboardingCaption2Style.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
      color = onboardingTextSecondary,
    )
    Text(value, style = onboardingHeadlineStyle, color = onboardingText)
    HorizontalDivider(color = onboardingBorder)
  }
}
