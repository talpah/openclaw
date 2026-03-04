package ai.openclaw.android.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun OnboardingNavBar(
  step: OnboardingStep,
  isConnected: Boolean,
  onBack: () -> Unit,
  onNext: () -> Unit,
) {
  val backEnabled = step != OnboardingStep.Welcome
  val ctaLabel =
    when (step) {
      OnboardingStep.Welcome,
      OnboardingStep.Gateway,
      OnboardingStep.Permissions -> "Next"
      OnboardingStep.FinalCheck -> if (isConnected) "Finish" else "Connect"
    }

  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
      modifier = Modifier.size(52.dp),
      shape = RoundedCornerShape(14.dp),
      color = onboardingSurface,
      border = BorderStroke(1.dp, if (backEnabled) onboardingBorderStrong else onboardingBorder),
    ) {
      IconButton(onClick = onBack, enabled = backEnabled) {
        Icon(
          Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = "Back",
          tint = if (backEnabled) onboardingTextSecondary else onboardingTextTertiary,
        )
      }
    }

    Button(
      onClick = onNext,
      modifier = Modifier.weight(1f).height(52.dp),
      shape = RoundedCornerShape(14.dp),
      colors =
        ButtonDefaults.buttonColors(
          containerColor = onboardingAccent,
          contentColor = Color.White,
          disabledContainerColor = onboardingAccent.copy(alpha = 0.45f),
          disabledContentColor = Color.White,
        ),
    ) {
      Text(ctaLabel, style = onboardingHeadlineStyle.copy(fontWeight = FontWeight.Bold))
    }
  }
}
