package ai.openclaw.android.ui

import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Camera enable toggle and tip text for the Settings sheet. */
@Composable
internal fun SettingsCameraSection(
  cameraEnabled: Boolean,
  listItemColors: ListItemColors,
  onCameraChange: (Boolean) -> Unit,
) {
  Text(
    "CAMERA",
    style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    color = mobileAccent,
  )
  ListItem(
    modifier = Modifier.settingsRowModifier(),
    colors = listItemColors,
    headlineContent = { Text("Allow Camera", style = mobileHeadline) },
    supportingContent = {
      Text(
        "Allows the gateway to request photos or short video clips (foreground only).",
        style = mobileCallout,
      )
    },
    trailingContent = { Switch(checked = cameraEnabled, onCheckedChange = onCameraChange) },
  )
  Text(
    "Tip: grant Microphone permission for video clips with audio.",
    style = mobileCallout,
    color = mobileTextSecondary,
  )
}
