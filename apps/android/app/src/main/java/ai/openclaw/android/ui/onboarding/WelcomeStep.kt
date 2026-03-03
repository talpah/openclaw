package ai.openclaw.android.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun WelcomeStep() {
  StepShell(title = "What You Get") {
    Bullet("Control the gateway and operator chat from one mobile surface.")
    Bullet("Connect with setup code and recover pairing with CLI commands.")
    Bullet("Enable only the permissions and capabilities you want.")
    Bullet("Finish with a real connection check before entering the app.")
  }
}

@Composable
private fun Bullet(text: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
    Box(
      modifier =
        Modifier
          .padding(top = 7.dp)
          .size(8.dp)
          .background(onboardingAccentSoft, CircleShape),
    )
    Box(
      modifier =
        Modifier
          .padding(top = 9.dp)
          .size(4.dp)
          .background(onboardingAccent, CircleShape),
    )
    Text(
      text,
      style = onboardingBodyStyle,
      color = onboardingTextSecondary,
      modifier = Modifier.weight(1f),
    )
  }
}
