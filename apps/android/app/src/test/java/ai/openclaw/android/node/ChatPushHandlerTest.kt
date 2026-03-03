package ai.openclaw.android.node

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPushHandlerTest {
  @Test
  fun handleChatPush_rejectsMissingParams() {
    val handler = ChatPushHandler.forTesting(FakeChatPoster(authorized = true))

    val result = handler.handleChatPush(null)

    assertFalse(result.ok)
    assertEquals("INVALID_REQUEST", result.error?.code)
  }

  @Test
  fun handleChatPush_rejectsMissingTextField() {
    val handler = ChatPushHandler.forTesting(FakeChatPoster(authorized = true))

    val result = handler.handleChatPush("""{"speak":true}""")

    assertFalse(result.ok)
    assertEquals("INVALID_REQUEST", result.error?.code)
  }

  @Test
  fun handleChatPush_rejectsEmptyText() {
    val handler = ChatPushHandler.forTesting(FakeChatPoster(authorized = true))

    val result = handler.handleChatPush("""{"text":"   "}""")

    assertFalse(result.ok)
    assertEquals("INVALID_REQUEST", result.error?.code)
  }

  @Test
  fun handleChatPush_postsNotificationWhenAuthorized() {
    val poster = FakeChatPoster(authorized = true)
    val handler = ChatPushHandler.forTesting(poster)

    val result = handler.handleChatPush("""{"text":"Hello from the agent!"}""")

    assertTrue(result.ok)
    assertEquals(1, poster.posts)
  }

  @Test
  fun handleChatPush_returnsMessageIdInPayload() {
    val handler = ChatPushHandler.forTesting(FakeChatPoster(authorized = true))

    val result = handler.handleChatPush("""{"text":"ping"}""")

    assertTrue(result.ok)
    assertNotNull(result.payloadJson)
    assertTrue(result.payloadJson!!.contains("messageId"))
  }

  @Test
  fun handleChatPush_skipsNotificationWhenUnauthorized() {
    val poster = FakeChatPoster(authorized = false)
    val handler = ChatPushHandler.forTesting(poster)

    val result = handler.handleChatPush("""{"text":"ping"}""")

    // Still returns ok — notification is best-effort
    assertTrue(result.ok)
    assertEquals(0, poster.posts)
  }

  @Test
  fun handleChatPush_returnsUnavailableWhenPostThrowsUnexpectedly() {
    val handler = ChatPushHandler.forTesting(ThrowingChatPoster(authorized = true, error = IllegalStateException("boom")))

    val result = handler.handleChatPush("""{"text":"ping"}""")

    assertFalse(result.ok)
    assertEquals("UNAVAILABLE", result.error?.code)
  }

  @Test
  fun handleChatPush_survivesSecurityExceptionFromPost() {
    // Permission revoked between isAuthorized() check and post(); should not crash, returns ok.
    val handler = ChatPushHandler.forTesting(ThrowingChatPoster(authorized = true, error = SecurityException("revoked")))

    val result = handler.handleChatPush("""{"text":"ping"}""")

    // Security exception is swallowed; notification skipped but the command succeeds.
    assertTrue(result.ok)
  }
}

private class FakeChatPoster(
  private val authorized: Boolean,
) : ChatNotificationPoster {
  var posts: Int = 0
    private set

  override fun isAuthorized(): Boolean = authorized

  override fun post(messageId: String, text: String) {
    posts += 1
  }
}

private class ThrowingChatPoster(
  private val authorized: Boolean,
  private val error: Throwable,
) : ChatNotificationPoster {
  override fun isAuthorized(): Boolean = authorized

  override fun post(messageId: String, text: String) {
    throw error
  }
}
