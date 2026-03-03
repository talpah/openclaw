package ai.openclaw.android.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Microphone permission row and note for the Settings sheet. */
@Composable
internal fun SettingsVoiceSection(
  micPermissionGranted: Boolean,
  listItemColors: ListItemColors,
  onMicClick: () -> Unit,
) {
  Text(
    "VOICE",
    style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    color = mobileAccent,
  )
  ListItem(
    modifier = Modifier.settingsRowModifier(),
    colors = listItemColors,
    headlineContent = { Text("Microphone permission", style = mobileHeadline) },
    supportingContent = {
      Text(
        if (micPermissionGranted) {
          "Granted. Use the Voice tab mic button to capture transcript."
        } else {
          "Required for Voice tab transcription."
        },
        style = mobileCallout,
      )
    },
    trailingContent = {
      Button(
        onClick = onMicClick,
        colors = settingsPrimaryButtonColors(),
        shape = RoundedCornerShape(14.dp),
      ) {
        Text(
          if (micPermissionGranted) "Manage" else "Grant",
          style = mobileCallout.copy(fontWeight = FontWeight.Bold),
        )
      }
    },
  )
  Text(
    "Voice wake and talk modes were removed. Voice now uses one mic on/off flow in the Voice tab.",
    style = mobileCallout,
    color = mobileTextSecondary,
  )
}
