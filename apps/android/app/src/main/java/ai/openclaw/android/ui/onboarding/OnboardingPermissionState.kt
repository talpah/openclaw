package ai.openclaw.android.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal enum class PermissionToggle {
  Discovery,
  Location,
  Notifications,
  Microphone,
  Camera,
  Photos,
  Contacts,
  Calendar,
  Motion,
  Sms,
}

internal enum class SpecialAccessToggle {
  NotificationListener,
  AppUpdates,
}

internal class OnboardingPermissionState(
  val context: Context,
  val enableDiscovery: Boolean,
  val enableLocation: Boolean,
  val enableNotifications: Boolean,
  val enableNotificationListener: Boolean,
  val enableAppUpdates: Boolean,
  val enableMicrophone: Boolean,
  val enableCamera: Boolean,
  val enablePhotos: Boolean,
  val enableContacts: Boolean,
  val enableCalendar: Boolean,
  val enableMotion: Boolean,
  val enableSms: Boolean,
  val motionAvailable: Boolean,
  val motionPermissionRequired: Boolean,
  val smsAvailable: Boolean,
  val enabledSummary: String,
  val onDiscoveryChange: (Boolean) -> Unit,
  val onLocationChange: (Boolean) -> Unit,
  val onNotificationsChange: (Boolean) -> Unit,
  val onNotificationListenerChange: (Boolean) -> Unit,
  val onAppUpdatesChange: (Boolean) -> Unit,
  val onMicrophoneChange: (Boolean) -> Unit,
  val onCameraChange: (Boolean) -> Unit,
  val onPhotosChange: (Boolean) -> Unit,
  val onContactsChange: (Boolean) -> Unit,
  val onCalendarChange: (Boolean) -> Unit,
  val onMotionChange: (Boolean) -> Unit,
  val onSmsChange: (Boolean) -> Unit,
  /** Calls [onProceed] only after any required special-access settings screens are opened. */
  val proceedFromPermissions: (onProceed: () -> Unit) -> Unit,
)

@Composable
internal fun rememberOnboardingPermissionState(context: Context): OnboardingPermissionState {
  val smsAvailable = remember(context) {
    context.packageManager?.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) == true
  }
  val motionAvailable = remember(context) { hasMotionCapabilities(context) }
  val motionPermissionRequired = true
  val notificationsPermissionRequired = Build.VERSION.SDK_INT >= 33
  val discoveryPermission =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES
    else Manifest.permission.ACCESS_FINE_LOCATION
  val photosPermission =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
    else Manifest.permission.READ_EXTERNAL_STORAGE

  var enableDiscovery by rememberSaveable { mutableStateOf(isPermissionGranted(context, discoveryPermission)) }
  var enableLocation by rememberSaveable { mutableStateOf(false) }
  var enableNotifications by rememberSaveable {
    mutableStateOf(
      !notificationsPermissionRequired ||
        isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS),
    )
  }
  var enableNotificationListener by rememberSaveable { mutableStateOf(isNotificationListenerEnabled(context)) }
  var enableAppUpdates by rememberSaveable { mutableStateOf(canInstallUnknownApps(context)) }
  var enableMicrophone by rememberSaveable { mutableStateOf(false) }
  var enableCamera by rememberSaveable { mutableStateOf(false) }
  var enablePhotos by rememberSaveable { mutableStateOf(false) }
  var enableContacts by rememberSaveable { mutableStateOf(false) }
  var enableCalendar by rememberSaveable { mutableStateOf(false) }
  var enableMotion by rememberSaveable {
    mutableStateOf(
      motionAvailable &&
        (!motionPermissionRequired ||
          isPermissionGranted(context, Manifest.permission.ACTIVITY_RECOGNITION)),
    )
  }
  var enableSms by rememberSaveable {
    mutableStateOf(smsAvailable && isPermissionGranted(context, Manifest.permission.SEND_SMS))
  }

  var pendingPermissionToggle by remember { mutableStateOf<PermissionToggle?>(null) }
  var pendingSpecialAccessToggle by remember { mutableStateOf<SpecialAccessToggle?>(null) }

  fun setPermissionToggleEnabled(toggle: PermissionToggle, enabled: Boolean) {
    when (toggle) {
      PermissionToggle.Discovery -> enableDiscovery = enabled
      PermissionToggle.Location -> enableLocation = enabled
      PermissionToggle.Notifications -> enableNotifications = enabled
      PermissionToggle.Microphone -> enableMicrophone = enabled
      PermissionToggle.Camera -> enableCamera = enabled
      PermissionToggle.Photos -> enablePhotos = enabled
      PermissionToggle.Contacts -> enableContacts = enabled
      PermissionToggle.Calendar -> enableCalendar = enabled
      PermissionToggle.Motion -> enableMotion = enabled && motionAvailable
      PermissionToggle.Sms -> enableSms = enabled && smsAvailable
    }
  }

  fun isPermissionToggleGranted(toggle: PermissionToggle): Boolean =
    when (toggle) {
      PermissionToggle.Discovery -> isPermissionGranted(context, discoveryPermission)
      PermissionToggle.Location ->
        isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
          isPermissionGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
      PermissionToggle.Notifications ->
        !notificationsPermissionRequired ||
          isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
      PermissionToggle.Microphone -> isPermissionGranted(context, Manifest.permission.RECORD_AUDIO)
      PermissionToggle.Camera -> isPermissionGranted(context, Manifest.permission.CAMERA)
      PermissionToggle.Photos -> isPermissionGranted(context, photosPermission)
      PermissionToggle.Contacts ->
        isPermissionGranted(context, Manifest.permission.READ_CONTACTS) &&
          isPermissionGranted(context, Manifest.permission.WRITE_CONTACTS)
      PermissionToggle.Calendar ->
        isPermissionGranted(context, Manifest.permission.READ_CALENDAR) &&
          isPermissionGranted(context, Manifest.permission.WRITE_CALENDAR)
      PermissionToggle.Motion ->
        !motionAvailable ||
          !motionPermissionRequired ||
          isPermissionGranted(context, Manifest.permission.ACTIVITY_RECOGNITION)
      PermissionToggle.Sms ->
        !smsAvailable || isPermissionGranted(context, Manifest.permission.SEND_SMS)
    }

  fun setSpecialAccessToggleEnabled(toggle: SpecialAccessToggle, enabled: Boolean) {
    when (toggle) {
      SpecialAccessToggle.NotificationListener -> enableNotificationListener = enabled
      SpecialAccessToggle.AppUpdates -> enableAppUpdates = enabled
    }
  }

  val enabledSummary =
    remember(
      enableDiscovery, enableLocation, enableNotifications, enableNotificationListener,
      enableAppUpdates, enableMicrophone, enableCamera, enablePhotos, enableContacts,
      enableCalendar, enableMotion, enableSms, smsAvailable, motionAvailable,
    ) {
      val enabled = mutableListOf<String>()
      if (enableDiscovery) enabled += "Gateway discovery"
      if (enableLocation) enabled += "Location"
      if (enableNotifications) enabled += "Notifications"
      if (enableNotificationListener) enabled += "Notification listener"
      if (enableAppUpdates) enabled += "App updates"
      if (enableMicrophone) enabled += "Microphone"
      if (enableCamera) enabled += "Camera"
      if (enablePhotos) enabled += "Photos"
      if (enableContacts) enabled += "Contacts"
      if (enableCalendar) enabled += "Calendar"
      if (enableMotion && motionAvailable) enabled += "Motion"
      if (smsAvailable && enableSms) enabled += "SMS"
      if (enabled.isEmpty()) "None selected" else enabled.joinToString(", ")
    }

  val togglePermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      val pending = pendingPermissionToggle ?: return@rememberLauncherForActivityResult
      setPermissionToggleEnabled(pending, isPermissionToggleGranted(pending))
      pendingPermissionToggle = null
    }

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, context, pendingSpecialAccessToggle) {
    val observer =
      LifecycleEventObserver { _, event ->
        if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
        when (pendingSpecialAccessToggle) {
          SpecialAccessToggle.NotificationListener -> {
            setSpecialAccessToggleEnabled(
              SpecialAccessToggle.NotificationListener,
              isNotificationListenerEnabled(context),
            )
            pendingSpecialAccessToggle = null
          }
          SpecialAccessToggle.AppUpdates -> {
            setSpecialAccessToggleEnabled(
              SpecialAccessToggle.AppUpdates,
              canInstallUnknownApps(context),
            )
            pendingSpecialAccessToggle = null
          }
          null -> Unit
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  fun requestPermissionToggle(toggle: PermissionToggle, enabled: Boolean, permissions: List<String>) {
    if (!enabled) { setPermissionToggleEnabled(toggle, false); return }
    if (isPermissionToggleGranted(toggle)) { setPermissionToggleEnabled(toggle, true); return }
    val missing = permissions.distinct().filterNot { isPermissionGranted(context, it) }
    if (missing.isEmpty()) { setPermissionToggleEnabled(toggle, isPermissionToggleGranted(toggle)); return }
    pendingPermissionToggle = toggle
    togglePermissionLauncher.launch(missing.toTypedArray())
  }

  fun requestSpecialAccessToggle(toggle: SpecialAccessToggle, enabled: Boolean) {
    if (!enabled) { setSpecialAccessToggleEnabled(toggle, false); pendingSpecialAccessToggle = null; return }
    val granted = when (toggle) {
      SpecialAccessToggle.NotificationListener -> isNotificationListenerEnabled(context)
      SpecialAccessToggle.AppUpdates -> canInstallUnknownApps(context)
    }
    if (granted) { setSpecialAccessToggleEnabled(toggle, true); pendingSpecialAccessToggle = null; return }
    pendingSpecialAccessToggle = toggle
    when (toggle) {
      SpecialAccessToggle.NotificationListener -> openNotificationListenerSettings(context)
      SpecialAccessToggle.AppUpdates -> openUnknownAppSourcesSettings(context)
    }
  }

  return OnboardingPermissionState(
    context = context,
    enableDiscovery = enableDiscovery,
    enableLocation = enableLocation,
    enableNotifications = enableNotifications,
    enableNotificationListener = enableNotificationListener,
    enableAppUpdates = enableAppUpdates,
    enableMicrophone = enableMicrophone,
    enableCamera = enableCamera,
    enablePhotos = enablePhotos,
    enableContacts = enableContacts,
    enableCalendar = enableCalendar,
    enableMotion = enableMotion,
    enableSms = enableSms,
    motionAvailable = motionAvailable,
    motionPermissionRequired = motionPermissionRequired,
    smsAvailable = smsAvailable,
    enabledSummary = enabledSummary,
    onDiscoveryChange = { checked ->
      requestPermissionToggle(PermissionToggle.Discovery, checked, listOf(discoveryPermission))
    },
    onLocationChange = { checked ->
      requestPermissionToggle(
        PermissionToggle.Location,
        checked,
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
      )
    },
    onNotificationsChange = { checked ->
      if (!notificationsPermissionRequired) setPermissionToggleEnabled(PermissionToggle.Notifications, checked)
      else requestPermissionToggle(PermissionToggle.Notifications, checked, listOf(Manifest.permission.POST_NOTIFICATIONS))
    },
    onNotificationListenerChange = { checked ->
      requestSpecialAccessToggle(SpecialAccessToggle.NotificationListener, checked)
    },
    onAppUpdatesChange = { checked ->
      requestSpecialAccessToggle(SpecialAccessToggle.AppUpdates, checked)
    },
    onMicrophoneChange = { checked ->
      requestPermissionToggle(PermissionToggle.Microphone, checked, listOf(Manifest.permission.RECORD_AUDIO))
    },
    onCameraChange = { checked ->
      requestPermissionToggle(PermissionToggle.Camera, checked, listOf(Manifest.permission.CAMERA))
    },
    onPhotosChange = { checked ->
      requestPermissionToggle(PermissionToggle.Photos, checked, listOf(photosPermission))
    },
    onContactsChange = { checked ->
      requestPermissionToggle(
        PermissionToggle.Contacts,
        checked,
        listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
      )
    },
    onCalendarChange = { checked ->
      requestPermissionToggle(
        PermissionToggle.Calendar,
        checked,
        listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
      )
    },
    onMotionChange = { checked ->
      when {
        !motionAvailable -> setPermissionToggleEnabled(PermissionToggle.Motion, false)
        !motionPermissionRequired -> setPermissionToggleEnabled(PermissionToggle.Motion, checked)
        else -> requestPermissionToggle(
          PermissionToggle.Motion,
          checked,
          listOf(Manifest.permission.ACTIVITY_RECOGNITION),
        )
      }
    },
    onSmsChange = { checked ->
      if (!smsAvailable) setPermissionToggleEnabled(PermissionToggle.Sms, false)
      else requestPermissionToggle(PermissionToggle.Sms, checked, listOf(Manifest.permission.SEND_SMS))
    },
    proceedFromPermissions = { onProceed ->
      var openedSpecialSetup = false
      if (enableNotificationListener && !isNotificationListenerEnabled(context)) {
        openNotificationListenerSettings(context)
        openedSpecialSetup = true
      }
      if (enableAppUpdates && !canInstallUnknownApps(context)) {
        openUnknownAppSourcesSettings(context)
        openedSpecialSetup = true
      }
      if (!openedSpecialSetup) onProceed()
    },
  )
}
