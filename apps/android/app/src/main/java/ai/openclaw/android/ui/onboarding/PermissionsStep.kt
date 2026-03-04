package ai.openclaw.android.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PermissionsStep(state: OnboardingPermissionState) {
  val context = state.context
  val discoveryPermission =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES
    else Manifest.permission.ACCESS_FINE_LOCATION
  val photosPermission =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE
  val locationGranted =
    isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
      isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
  val contactsGranted =
    isPermissionGranted(context, Manifest.permission.READ_CONTACTS) &&
      isPermissionGranted(context, Manifest.permission.WRITE_CONTACTS)
  val calendarGranted =
    isPermissionGranted(context, Manifest.permission.READ_CALENDAR) &&
      isPermissionGranted(context, Manifest.permission.WRITE_CALENDAR)
  val motionGranted =
    when {
      !state.motionAvailable -> false
      !state.motionPermissionRequired -> true
      else -> isPermissionGranted(context, Manifest.permission.ACTIVITY_RECOGNITION)
    }
  val notificationListenerGranted = isNotificationListenerEnabled(context)
  val appUpdatesGranted = canInstallUnknownApps(context)

  StepShell(title = "Permissions") {
    Text(
      "Enable only what you need now. You can change everything later in Settings.",
      style = onboardingCalloutStyle,
      color = onboardingTextSecondary,
    )
    PermissionToggleRow(
      title = "Gateway discovery",
      subtitle = if (Build.VERSION.SDK_INT >= 33) "Nearby devices" else "Location (for NSD)",
      checked = state.enableDiscovery,
      granted = isPermissionGranted(context, discoveryPermission),
      onCheckedChange = state.onDiscoveryChange,
    )
    InlineDivider()
    PermissionToggleRow(
      title = "Location",
      subtitle = "location.get (while app is open unless set to Always later)",
      checked = state.enableLocation,
      granted = locationGranted,
      onCheckedChange = state.onLocationChange,
    )
    InlineDivider()
    if (Build.VERSION.SDK_INT >= 33) {
      PermissionToggleRow(
        title = "Notifications",
        subtitle = "system.notify and foreground alerts",
        checked = state.enableNotifications,
        granted = isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS),
        onCheckedChange = state.onNotificationsChange,
      )
      InlineDivider()
    }
    PermissionToggleRow(
      title = "Notification listener",
      subtitle = "notifications.list and notifications.actions (opens Android Settings)",
      checked = state.enableNotificationListener,
      granted = notificationListenerGranted,
      onCheckedChange = state.onNotificationListenerChange,
    )
    InlineDivider()
    PermissionToggleRow(
      title = "App updates",
      subtitle = "app.update install confirmation (opens Android Settings)",
      checked = state.enableAppUpdates,
      granted = appUpdatesGranted,
      onCheckedChange = state.onAppUpdatesChange,
    )
    InlineDivider()
    PermissionToggleRow(
      title = "Microphone",
      subtitle = "Voice tab transcription",
      checked = state.enableMicrophone,
      granted = isPermissionGranted(context, Manifest.permission.RECORD_AUDIO),
      onCheckedChange = state.onMicrophoneChange,
    )
    InlineDivider()
    PermissionToggleRow(
      title = "Camera",
      subtitle = "camera.snap and camera.clip",
      checked = state.enableCamera,
      granted = isPermissionGranted(context, Manifest.permission.CAMERA),
      onCheckedChange = state.onCameraChange,
    )
    InlineDivider()
    PermissionToggleRow(
      title = "Photos",
      subtitle = "photos.latest",
      checked = state.enablePhotos,
      granted = isPermissionGranted(context, photosPermission),
      onCheckedChange = state.onPhotosChange,
    )
    InlineDivider()
    PermissionToggleRow(
      title = "Contacts",
      subtitle = "contacts.search and contacts.add",
      checked = state.enableContacts,
      granted = contactsGranted,
      onCheckedChange = state.onContactsChange,
    )
    InlineDivider()
    PermissionToggleRow(
      title = "Calendar",
      subtitle = "calendar.events and calendar.add",
      checked = state.enableCalendar,
      granted = calendarGranted,
      onCheckedChange = state.onCalendarChange,
    )
    InlineDivider()
    PermissionToggleRow(
      title = "Motion",
      subtitle = "motion.activity and motion.pedometer",
      checked = state.enableMotion,
      granted = motionGranted,
      onCheckedChange = state.onMotionChange,
      enabled = state.motionAvailable,
      statusOverride = if (!state.motionAvailable) "Unavailable on this device" else null,
    )
    if (state.smsAvailable) {
      InlineDivider()
      PermissionToggleRow(
        title = "SMS",
        subtitle = "Allow gateway-triggered SMS sending",
        checked = state.enableSms,
        granted = isPermissionGranted(context, Manifest.permission.SEND_SMS),
        onCheckedChange = state.onSmsChange,
      )
    }
    Text(
      "All settings can be changed later in Settings.",
      style = onboardingCalloutStyle,
      color = onboardingTextSecondary,
    )
  }
}

@Composable
private fun InlineDivider() {
  HorizontalDivider(color = onboardingBorder)
}

@Composable
private fun PermissionToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  granted: Boolean,
  enabled: Boolean = true,
  statusOverride: String? = null,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(title, style = onboardingHeadlineStyle, color = onboardingText)
      Text(
        subtitle,
        style = onboardingCalloutStyle.copy(lineHeight = 18.sp),
        color = onboardingTextSecondary,
      )
      Text(
        statusOverride ?: if (granted) "Granted" else "Not granted",
        style = onboardingCaption1Style,
        color = if (granted) onboardingSuccess else onboardingTextSecondary,
      )
    }
    Switch(
      checked = checked,
      onCheckedChange = onCheckedChange,
      enabled = enabled,
      colors =
        SwitchDefaults.colors(
          checkedTrackColor = onboardingAccent,
          uncheckedTrackColor = onboardingBorderStrong,
          checkedThumbColor = Color.White,
          uncheckedThumbColor = Color.White,
        ),
    )
  }
}
