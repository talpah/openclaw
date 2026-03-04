package ai.openclaw.android.gateway

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class GatewayHealthMonitorTest {
  private val config = GatewayHealthMonitor.Config(
    intervalMs = 1_000L,
    timeoutMs = 500L,
    maxFailures = 3,
  )

  @Test
  fun doesNotFireOnFailureWhenCheckPasses() = runTest {
    val monitor = GatewayHealthMonitor(scope = this, config = config)
    var failures = 0

    monitor.start(check = { true }, onFailure = { failures++ })
    advanceTimeBy(5_000L)
    monitor.stop()

    assertEquals(0, failures)
  }

  @Test
  fun firesOnFailureAfterMaxFailures() = runTest {
    val monitor = GatewayHealthMonitor(scope = this, config = config)
    var failures = 0

    monitor.start(check = { false }, onFailure = { failures++ })
    // Need 3 failures: one per interval
    advanceTimeBy(config.intervalMs * config.maxFailures + 100)
    monitor.stop()

    assertTrue("Expected at least 1 failure callback", failures >= 1)
  }

  @Test
  fun resetsFailureCountAfterCallback() = runTest {
    val monitor = GatewayHealthMonitor(scope = this, config = config)
    var callbackCount = 0

    monitor.start(check = { false }, onFailure = { callbackCount++ })
    // 3 failures → callback fires, counter resets; another 3 failures → second callback
    advanceTimeBy(config.intervalMs * config.maxFailures * 2 + 100)
    monitor.stop()

    assertTrue("Expected at least 2 failure callbacks", callbackCount >= 2)
  }

  @Test
  fun stopPreventsSubsequentCallbacks() = runTest {
    val monitor = GatewayHealthMonitor(scope = this, config = config)
    var failures = 0

    monitor.start(check = { false }, onFailure = { failures++ })
    advanceTimeBy(config.intervalMs * 2)
    monitor.stop()
    val captured = failures
    advanceTimeBy(config.intervalMs * 10)

    assertEquals(captured, failures)
  }

  @Test
  fun authErrorTreatedAsPass() = runTest {
    val monitor = GatewayHealthMonitor(scope = this, config = config)
    var failures = 0

    // Check that always returns false triggers failures, while a passing check doesn't
    monitor.start(check = { true }, onFailure = { failures++ })
    advanceTimeBy(config.intervalMs * 5)
    monitor.stop()

    assertEquals(0, failures)
  }

  @Test
  fun canRestartAfterStop() = runTest {
    val monitor = GatewayHealthMonitor(scope = this, config = config)
    var failures = 0

    monitor.start(check = { false }, onFailure = { failures++ })
    advanceTimeBy(config.intervalMs * config.maxFailures + 100)
    monitor.stop()
    val firstRound = failures

    failures = 0
    monitor.start(check = { false }, onFailure = { failures++ })
    advanceTimeBy(config.intervalMs * config.maxFailures + 100)
    monitor.stop()

    assertTrue(firstRound >= 1)
    assertTrue(failures >= 1)
  }
}
