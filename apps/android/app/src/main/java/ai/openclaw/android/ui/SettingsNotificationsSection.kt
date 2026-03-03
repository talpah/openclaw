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

/** System notification permission and notification listener access rows for Settings. */
@Composable
internal fun SettingsNotificationsSection(
  notificationsPermissionGranted: Boolean,
  notificationListenerEnabled: Boolean,
  listItemColors: ListItemColors,
  onSystemNotificationsClick: () -> Unit,
  onNotificationListenerClick: () -> Unit,
) {
  val rowMod = Modifier.settingsRowModifier()

  Text(
    "NOTIFICATIONS",
    style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    color = mobileAccent,
  )

  ListItem(
    modifier = rowMod,
    colors = listItemColors,
    headlineContent = { Text("System Notifications", style = mobileHeadline) },
    supportingContent = {
      Text(
        "Required for `system.notify` and Android foreground service alerts.",
        style = mobileCallout,
      )
    },
    trailingContent = {
      Button(onClick = onSystemNotificationsClick, colors = settingsPrimaryButtonColors(), shape = RoundedCornerShape(14.dp)) {
        Text(
          if (notificationsPermissionGranted) "Manage" else "Grant",
          style = mobileCallout.copy(fontWeight = FontWeight.Bold),
        )
      }
    },
  )

  ListItem(
    modifier = rowMod,
    colors = listItemColors,
    headlineContent = { Text("Notification Listener Access", style = mobileHeadline) },
    supportingContent = {
      Text("Required for `notifications.list` and `notifications.actions`.", style = mobileCallout)
    },
    trailingContent = {
      Button(onClick = onNotificationListenerClick, colors = settingsPrimaryButtonColors(), shape = RoundedCornerShape(14.dp)) {
        Text(
          if (notificationListenerEnabled) "Manage" else "Enable",
          style = mobileCallout.copy(fontWeight = FontWeight.Bold),
        )
      }
    },
  )
}
