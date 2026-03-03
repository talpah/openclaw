package ai.openclaw.android.node

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ai.openclaw.android.gateway.GatewaySession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.util.UUID

private const val CHAT_PUSH_CHANNEL_ID = "openclaw.chat.push"

internal interface ChatNotificationPoster {
  fun isAuthorized(): Boolean
  fun post(messageId: String, text: String)
}

private class AndroidChatNotificationPoster(
  private val appContext: Context,
) : ChatNotificationPoster {
  override fun isAuthorized(): Boolean {
    if (Build.VERSION.SDK_INT >= 33) {
      val granted =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
          PackageManager.PERMISSION_GRANTED
      if (!granted) return false
    }
    return NotificationManagerCompat.from(appContext).areNotificationsEnabled()
  }

  override fun post(messageId: String, text: String) {
    ensureChannel()
    val notification =
      NotificationCompat.Builder(appContext, CHAT_PUSH_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("OpenClaw")
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()
    if (
      Build.VERSION.SDK_INT >= 33 &&
      ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      throw SecurityException("notifications permission missing")
    }
    NotificationManagerCompat.from(appContext).notify(
      (System.currentTimeMillis() and 0x7FFFFFFF).toInt(),
      notification,
    )
  }

  private fun ensureChannel() {
    val manager = appContext.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(CHAT_PUSH_CHANNEL_ID) == null) {
      manager.createNotificationChannel(
        NotificationChannel(CHAT_PUSH_CHANNEL_ID, "OpenClaw Chat", NotificationManager.IMPORTANCE_DEFAULT),
      )
    }
  }
}

/** Handles the [chat.push] invoke command — posts a notification for server-initiated messages. */
class ChatPushHandler private constructor(
  private val poster: ChatNotificationPoster,
) {
  constructor(appContext: Context) : this(poster = AndroidChatNotificationPoster(appContext))

  fun handleChatPush(paramsJson: String?): GatewaySession.InvokeResult {
    val params =
      parseParams(paramsJson)
        ?: return GatewaySession.InvokeResult.error(
          code = "INVALID_REQUEST",
          message = "INVALID_REQUEST: expected JSON object with text",
        )
    val text = params.first
    if (text.isEmpty()) {
      return GatewaySession.InvokeResult.error(
        code = "INVALID_REQUEST",
        message = "INVALID_REQUEST: empty chat.push text",
      )
    }
    val messageId = UUID.randomUUID().toString()
    if (poster.isAuthorized()) {
      try {
        poster.post(messageId, text)
      } catch (_: SecurityException) {
        // Permission revoked between isAuthorized() and post(); continue without notification.
      } catch (err: Throwable) {
        return GatewaySession.InvokeResult.error(
          code = "UNAVAILABLE",
          message = "NOTIFICATION_FAILED: ${err.message ?: "post failed"}",
        )
      }
    }
    val payload =
      buildJsonObject { put("messageId", JsonPrimitive(messageId)) }.toString()
    return GatewaySession.InvokeResult.ok(payload)
  }

  private fun parseParams(paramsJson: String?): Pair<String, Boolean>? {
    if (paramsJson.isNullOrBlank()) return null
    val obj =
      try {
        Json.parseToJsonElement(paramsJson) as? JsonObject
      } catch (_: Throwable) {
        null
      } ?: return null
    val text = (obj["text"] as? JsonPrimitive)?.contentOrNull?.trim() ?: return null
    val speak = (obj["speak"] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: true
    return text to speak
  }

  companion object {
    internal fun forTesting(poster: ChatNotificationPoster): ChatPushHandler = ChatPushHandler(poster)
  }
}
