package ai.openclaw.android.ui

import ai.openclaw.android.LocationMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Location mode radio group, precise-location toggle, and note for the Settings sheet. */
@Composable
internal fun SettingsLocationSection(
  locationMode: LocationMode,
  locationPreciseEnabled: Boolean,
  listItemColors: ListItemColors,
  onLocationModeChange: (LocationMode) -> Unit,
  onPreciseChange: (Boolean) -> Unit,
) {
  Text(
    "LOCATION",
    style = mobileCaption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    color = mobileAccent,
  )
  Column(
    modifier = Modifier.settingsRowModifier(),
    verticalArrangement = Arrangement.spacedBy(0.dp),
  ) {
    ListItem(
      modifier = Modifier.fillMaxWidth(),
      colors = listItemColors,
      headlineContent = { Text("Off", style = mobileHeadline) },
      supportingContent = { Text("Disable location sharing.", style = mobileCallout) },
      trailingContent = {
        RadioButton(
          selected = locationMode == LocationMode.Off,
          onClick = { onLocationModeChange(LocationMode.Off) },
        )
      },
    )
    HorizontalDivider(color = mobileBorder)
    ListItem(
      modifier = Modifier.fillMaxWidth(),
      colors = listItemColors,
      headlineContent = { Text("While Using", style = mobileHeadline) },
      supportingContent = { Text("Only while OpenClaw is open.", style = mobileCallout) },
      trailingContent = {
        RadioButton(
          selected = locationMode == LocationMode.WhileUsing,
          onClick = { onLocationModeChange(LocationMode.WhileUsing) },
        )
      },
    )
    HorizontalDivider(color = mobileBorder)
    ListItem(
      modifier = Modifier.fillMaxWidth(),
      colors = listItemColors,
      headlineContent = { Text("Always", style = mobileHeadline) },
      supportingContent = {
        Text("Allow background location (requires system permission).", style = mobileCallout)
      },
      trailingContent = {
        RadioButton(
          selected = locationMode == LocationMode.Always,
          onClick = { onLocationModeChange(LocationMode.Always) },
        )
      },
    )
    HorizontalDivider(color = mobileBorder)
    ListItem(
      modifier = Modifier.fillMaxWidth(),
      colors = listItemColors,
      headlineContent = { Text("Precise Location", style = mobileHeadline) },
      supportingContent = { Text("Use precise GPS when available.", style = mobileCallout) },
      trailingContent = {
        Switch(
          checked = locationPreciseEnabled,
          onCheckedChange = onPreciseChange,
          enabled = locationMode != LocationMode.Off,
        )
      },
    )
  }
  Text(
    "Always may require Android Settings to allow background location.",
    style = mobileCallout,
    color = mobileTextSecondary,
  )
}
