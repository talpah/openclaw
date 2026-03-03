package ai.openclaw.android

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import ai.openclaw.android.ui.RootScreen
import ai.openclaw.android.ui.OpenClawTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  private val viewModel: MainViewModel by viewModels()
  private lateinit var permissionRequester: PermissionRequester
  private lateinit var screenCaptureRequester: ScreenCaptureRequester

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    permissionRequester = PermissionRequester(this)
    screenCaptureRequester = ScreenCaptureRequester(this)
    viewModel.camera.attachLifecycleOwner(this)
    viewModel.camera.attachPermissionRequester(permissionRequester)
    viewModel.sms.attachPermissionRequester(permissionRequester)
    viewModel.screenRecorder.attachScreenCaptureRequester(screenCaptureRequester)
    viewModel.screenRecorder.attachPermissionRequester(permissionRequester)

    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.preventSleep.collect { enabled ->
          if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
          } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
          }
        }
      }
    }

    setContent {
      OpenClawTheme {
        Surface(modifier = Modifier) {
          RootScreen(viewModel = viewModel)
        }
      }
    }

    // Handle share intent received at app launch.
    handleShareIntent(intent)

    // Keep startup path lean: start foreground service after first frame.
    window.decorView.post { NodeForegroundService.start(this) }
  }

  /** Handles share intents when the activity is already running (singleTop re-launch). */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleShareIntent(intent)
  }

  override fun onStart() {
    super.onStart()
    viewModel.setForeground(true)
  }

  override fun onStop() {
    viewModel.setForeground(false)
    super.onStop()
  }

  private fun handleShareIntent(intent: Intent?) {
    if (intent?.action != Intent.ACTION_SEND) return
    val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim()
    val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()
    if (!text.isNullOrEmpty()) {
      // Combine subject + body when both are present (e.g., shared emails or articles).
      val payload = if (!subject.isNullOrEmpty()) "$subject\n\n$text" else text
      viewModel.setShareText(payload)
    }
  }
}
