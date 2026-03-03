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

/** Photos, Contacts, Calendar, and Motion permission rows for the Settings screen. */
@Composable
internal fun SettingsDataAccessSection(
  photosPermissionGranted: Boolean,
  contactsPermissionGranted: Boolean,
  calendarPermissionGranted: Boolean,
  motionPermissionGranted: Boolean,
  motionAvailable: Boolean,
  listItemColors: ListItemColors,
  onPhotosClick: () -> Unit,
  onContactsClick: () -> Unit,
  onCalendarClick: () -> Unit,
  onMotionClick: () -> Unit,
) {
  val rowMod = Modifier.settingsRowModifier()

  Text(
    "DATA ACCESS",
    style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    color = mobileAccent,
  )

  ListItem(
    modifier = rowMod,
    colors = listItemColors,
    headlineContent = { Text("Photos Permission", style = mobileHeadline) },
    supportingContent = { Text("Required for `photos.latest`.", style = mobileCallout) },
    trailingContent = {
      Button(onClick = onPhotosClick, colors = settingsPrimaryButtonColors(), shape = RoundedCornerShape(14.dp)) {
        Text(if (photosPermissionGranted) "Manage" else "Grant", style = mobileCallout.copy(fontWeight = FontWeight.Bold))
      }
    },
  )

  ListItem(
    modifier = rowMod,
    colors = listItemColors,
    headlineContent = { Text("Contacts Permission", style = mobileHeadline) },
    supportingContent = { Text("Required for `contacts.search` and `contacts.add`.", style = mobileCallout) },
    trailingContent = {
      Button(onClick = onContactsClick, colors = settingsPrimaryButtonColors(), shape = RoundedCornerShape(14.dp)) {
        Text(if (contactsPermissionGranted) "Manage" else "Grant", style = mobileCallout.copy(fontWeight = FontWeight.Bold))
      }
    },
  )

  ListItem(
    modifier = rowMod,
    colors = listItemColors,
    headlineContent = { Text("Calendar Permission", style = mobileHeadline) },
    supportingContent = { Text("Required for `calendar.events` and `calendar.add`.", style = mobileCallout) },
    trailingContent = {
      Button(onClick = onCalendarClick, colors = settingsPrimaryButtonColors(), shape = RoundedCornerShape(14.dp)) {
        Text(if (calendarPermissionGranted) "Manage" else "Grant", style = mobileCallout.copy(fontWeight = FontWeight.Bold))
      }
    },
  )

  val motionLabel =
    when {
      !motionAvailable -> "Unavailable"
      motionPermissionGranted -> "Manage"
      else -> "Grant"
    }
  ListItem(
    modifier = rowMod,
    colors = listItemColors,
    headlineContent = { Text("Motion Permission", style = mobileHeadline) },
    supportingContent = {
      Text(
        if (!motionAvailable) "This device does not expose accelerometer or step-counter motion sensors."
        else "Required for `motion.activity` and `motion.pedometer`.",
        style = mobileCallout,
      )
    },
    trailingContent = {
      Button(onClick = onMotionClick, enabled = motionAvailable, colors = settingsPrimaryButtonColors(), shape = RoundedCornerShape(14.dp)) {
        Text(motionLabel, style = mobileCallout.copy(fontWeight = FontWeight.Bold))
      }
    },
  )
}
