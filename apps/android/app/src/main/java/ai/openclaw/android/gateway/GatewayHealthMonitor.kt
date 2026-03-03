package ai.openclaw.android.gateway

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Periodically pings the gateway and triggers a reconnect after [maxFailures]
 * consecutive failures. Mirrors GatewayHealthMonitor.swift in the iOS app.
 */
class GatewayHealthMonitor(
  private val scope: CoroutineScope,
  private val config: Config = Config(),
) {
  data class Config(
    val intervalMs: Long = 15_000L,
    val timeoutMs: Long = 5_000L,
    val maxFailures: Int = 3,
  )

  private companion object {
    private const val TAG = "GatewayHealthMonitor"
  }

  private var job: Job? = null

  /** Start the monitor. [check] returns true when the gateway is reachable. */
  fun start(
    check: suspend () -> Boolean,
    onFailure: (failureCount: Int) -> Unit,
  ) {
    stop()
    job = scope.launch {
      var failures = 0
      while (isActive) {
        delay(config.intervalMs)
        val ok = runCheck(check)
        if (ok) {
          failures = 0
        } else {
          failures++
          Log.d(TAG, "health check failed ($failures/${config.maxFailures})")
          if (failures >= config.maxFailures.coerceAtLeast(1)) {
            onFailure(failures)
            failures = 0
          }
        }
      }
    }
  }

  fun stop() {
    job?.cancel()
    job = null
  }

  private suspend fun runCheck(check: suspend () -> Boolean): Boolean {
    return try {
      withTimeout(config.timeoutMs) { check() }
    } catch (_: Throwable) {
      false
    }
  }
}
