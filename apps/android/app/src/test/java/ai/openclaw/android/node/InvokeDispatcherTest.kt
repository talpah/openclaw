package ai.openclaw.android.node

import ai.openclaw.android.CameraHudKind
import ai.openclaw.android.gateway.DeviceIdentityStore
import ai.openclaw.android.protocol.OpenClawCameraCommand
import ai.openclaw.android.protocol.OpenClawDeviceCommand
import ai.openclaw.android.protocol.OpenClawLocationCommand
import ai.openclaw.android.protocol.OpenClawMotionCommand
import ai.openclaw.android.protocol.OpenClawSmsCommand
import ai.openclaw.android.protocol.OpenClawSystemCommand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InvokeDispatcherTest {
  private val ctx = RuntimeEnvironment.getApplication()

  private fun makeDispatcher(
    isForeground: Boolean = true,
    cameraEnabled: Boolean = true,
    locationEnabled: Boolean = true,
    smsAvailable: Boolean = true,
    debugBuild: Boolean = false,
    motionActivityAvailable: Boolean = true,
    motionPedometerAvailable: Boolean = true,
  ): InvokeDispatcher {
    val canvas = CanvasController()
    return InvokeDispatcher(
      canvas = canvas,
      cameraHandler = CameraHandler(
        ctx, CameraCaptureManager(ctx), MutableStateFlow(false),
        showCameraHud = { _, _, _ -> }, triggerCameraFlash = {},
        invokeErrorFromThrowable = { e -> Pair("ERROR", e.message ?: "error") },
      ),
      locationHandler = LocationHandler(
        ctx, LocationCaptureManager(ctx), Json { ignoreUnknownKeys = true },
        isForeground = { isForeground },
        locationMode = { ai.openclaw.android.LocationMode.WhileUsing },
        locationPreciseEnabled = { true },
      ),
      deviceHandler = DeviceHandler(ctx),
      notificationsHandler = NotificationsHandler(ctx),
      systemHandler = SystemHandler(ctx),
      chatPushHandler = ChatPushHandler(ctx),
      photosHandler = PhotosHandler(ctx),
      contactsHandler = ContactsHandler(ctx),
      calendarHandler = CalendarHandler(ctx),
      motionHandler = MotionHandler(ctx),
      screenHandler = ScreenHandler(
        ScreenRecordManager(ctx),
        setScreenRecordActive = {},
        invokeErrorFromThrowable = { e -> Pair("ERROR", e.message ?: "error") },
      ),
      smsHandler = SmsHandler(SmsManager(ctx)),
      a2uiHandler = A2UIHandler(canvas, Json { ignoreUnknownKeys = true }, getNodeCanvasHostUrl = { null }, getOperatorCanvasHostUrl = { null }),
      debugHandler = DebugHandler(ctx, DeviceIdentityStore(ctx)),
      appUpdateHandler = AppUpdateHandler(ctx) { null },
      isForeground = { isForeground },
      cameraEnabled = { cameraEnabled },
      locationEnabled = { locationEnabled },
      smsAvailable = { smsAvailable },
      debugBuild = { debugBuild },
      refreshNodeCanvasCapability = { false },
      onCanvasA2uiPush = {},
      onCanvasA2uiReset = {},
      motionActivityAvailable = { motionActivityAvailable },
      motionPedometerAvailable = { motionPedometerAvailable },
    )
  }

  // ── Unknown command ──────────────────────────────────────────────────────

  @Test
  fun unknownCommand_returnsInvalidRequest() = runBlocking {
    val result = makeDispatcher().handleInvoke("not.a.real.command", null)

    assertFalse(result.ok)
    assertEquals("INVALID_REQUEST", result.error?.code)
  }

  @Test
  fun emptyCommand_returnsInvalidRequest() = runBlocking {
    val result = makeDispatcher().handleInvoke("", null)

    assertFalse(result.ok)
    assertEquals("INVALID_REQUEST", result.error?.code)
  }

  // ── Foreground gate ──────────────────────────────────────────────────────

  @Test
  fun cameraSnap_whenBackground_returnsBackgroundUnavailable() = runBlocking {
    val result = makeDispatcher(isForeground = false)
      .handleInvoke(OpenClawCameraCommand.Snap.rawValue, null)

    assertFalse(result.ok)
    assertEquals("NODE_BACKGROUND_UNAVAILABLE", result.error?.code)
  }

  @Test
  fun canvasPresent_whenBackground_returnsBackgroundUnavailable() = runBlocking {
    val result = makeDispatcher(isForeground = false)
      .handleInvoke("canvas.present", """{"url":"https://example.com"}""")

    assertFalse(result.ok)
    assertEquals("NODE_BACKGROUND_UNAVAILABLE", result.error?.code)
  }

  // ── Camera availability gate ─────────────────────────────────────────────

  @Test
  fun cameraSnap_whenCameraDisabled_returnsCameraDisabled() = runBlocking {
    val result = makeDispatcher(cameraEnabled = false)
      .handleInvoke(OpenClawCameraCommand.Snap.rawValue, null)

    assertFalse(result.ok)
    assertEquals("CAMERA_DISABLED", result.error?.code)
  }

  @Test
  fun cameraList_whenCameraDisabled_returnsCameraDisabled() = runBlocking {
    val result = makeDispatcher(cameraEnabled = false)
      .handleInvoke(OpenClawCameraCommand.List.rawValue, null)

    assertFalse(result.ok)
    assertEquals("CAMERA_DISABLED", result.error?.code)
  }

  @Test
  fun cameraClip_whenCameraDisabled_returnsCameraDisabled() = runBlocking {
    val result = makeDispatcher(cameraEnabled = false)
      .handleInvoke(OpenClawCameraCommand.Clip.rawValue, null)

    assertFalse(result.ok)
    assertEquals("CAMERA_DISABLED", result.error?.code)
  }

  // ── Location availability gate ───────────────────────────────────────────

  @Test
  fun locationGet_whenLocationDisabled_returnsLocationDisabled() = runBlocking {
    val result = makeDispatcher(locationEnabled = false)
      .handleInvoke(OpenClawLocationCommand.Get.rawValue, null)

    assertFalse(result.ok)
    assertEquals("LOCATION_DISABLED", result.error?.code)
  }

  // ── SMS availability gate ────────────────────────────────────────────────

  @Test
  fun smsSend_whenSmsUnavailable_returnsSmsUnavailable() = runBlocking {
    val result = makeDispatcher(smsAvailable = false)
      .handleInvoke(OpenClawSmsCommand.Send.rawValue, null)

    assertFalse(result.ok)
    assertEquals("SMS_UNAVAILABLE", result.error?.code)
  }

  // ── Motion availability gates ────────────────────────────────────────────

  @Test
  fun motionActivity_whenNotAvailable_returnsMotionUnavailable() = runBlocking {
    val result = makeDispatcher(motionActivityAvailable = false)
      .handleInvoke(OpenClawMotionCommand.Activity.rawValue, null)

    assertFalse(result.ok)
    assertEquals("MOTION_UNAVAILABLE", result.error?.code)
  }

  @Test
  fun motionPedometer_whenNotAvailable_returnsPedometerUnavailable() = runBlocking {
    val result = makeDispatcher(motionPedometerAvailable = false)
      .handleInvoke(OpenClawMotionCommand.Pedometer.rawValue, null)

    assertFalse(result.ok)
    assertEquals("PEDOMETER_UNAVAILABLE", result.error?.code)
  }

  // ── Debug gate ───────────────────────────────────────────────────────────

  @Test
  fun debugEd25519_whenNotDebugBuild_returnsInvalidRequest() = runBlocking {
    val result = makeDispatcher(debugBuild = false)
      .handleInvoke("debug.ed25519", null)

    assertFalse(result.ok)
    assertEquals("INVALID_REQUEST", result.error?.code)
  }

  @Test
  fun debugLogs_whenNotDebugBuild_returnsInvalidRequest() = runBlocking {
    val result = makeDispatcher(debugBuild = false)
      .handleInvoke("debug.logs", null)

    assertFalse(result.ok)
    assertEquals("INVALID_REQUEST", result.error?.code)
  }

  // ── Known commands reach handlers ────────────────────────────────────────

  @Test
  fun deviceStatus_routesToHandler() = runBlocking {
    // DeviceHandler.handleDeviceStatus returns ok with a JSON payload
    val result = makeDispatcher()
      .handleInvoke(OpenClawDeviceCommand.Status.rawValue, null)

    // Result is ok (handler runs successfully) or an error from the handler itself —
    // either way the dispatcher should not return INVALID_REQUEST or a gate error.
    assertTrue(result.ok || (result.error?.code != "INVALID_REQUEST" && result.error?.code != "CAMERA_DISABLED"))
  }

  @Test
  fun systemNotify_withValidParams_routesToHandler() = runBlocking {
    val result = makeDispatcher()
      .handleInvoke(
        OpenClawSystemCommand.Notify.rawValue,
        """{"title":"Test","body":"Hello"}""",
      )

    // SystemHandler returns ok for valid params regardless of actual notification post
    assertTrue(result.ok || result.error?.code != "INVALID_REQUEST")
  }
}
