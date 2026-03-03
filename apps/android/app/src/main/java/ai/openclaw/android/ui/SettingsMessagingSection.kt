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

/** SMS permission row for the Settings sheet. */
@Composable
internal fun SettingsMessagingSection(
  smsPermissionAvailable: Boolean,
  smsPermissionGranted: Boolean,
  listItemColors: ListItemColors,
  onSmsClick: () -> Unit,
) {
  val buttonLabel =
    when {
      !smsPermissionAvailable -> "Unavailable"
      smsPermissionGranted -> "Manage"
      else -> "Grant"
    }

  Text(
    "MESSAGING",
    style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    color = mobileAccent,
  )
  ListItem(
    modifier = Modifier.settingsRowModifier(),
    colors = listItemColors,
    headlineContent = { Text("SMS Permission", style = mobileHeadline) },
    supportingContent = {
      Text(
        if (smsPermissionAvailable) {
          "Allow the gateway to send SMS from this device."
        } else {
          "SMS requires a device with telephony hardware."
        },
        style = mobileCallout,
      )
    },
    trailingContent = {
      Button(
        onClick = onSmsClick,
        enabled = smsPermissionAvailable,
        colors = settingsPrimaryButtonColors(),
        shape = RoundedCornerShape(14.dp),
      ) {
        Text(buttonLabel, style = mobileCallout.copy(fontWeight = FontWeight.Bold))
      }
    },
  )
}
