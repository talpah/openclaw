package ai.openclaw.android.voice

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Shared JSON extension helpers for the voice package. */

internal fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asStringOrNull(): String? =
  (this as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonElement?.asDoubleOrNull(): Double? {
  val primitive = this as? JsonPrimitive ?: return null
  return primitive.content.toDoubleOrNull()
}

internal fun JsonElement?.asIntOrNull(): Int? {
  val primitive = this as? JsonPrimitive ?: return null
  return primitive.content.toIntOrNull()
}

internal fun JsonElement?.asLongOrNull(): Long? {
  val primitive = this as? JsonPrimitive ?: return null
  return primitive.content.toLongOrNull()
}

internal fun JsonElement?.asBooleanOrNull(): Boolean? {
  val primitive = this as? JsonPrimitive ?: return null
  val content = primitive.content.trim().lowercase()
  return when (content) {
    "true", "yes", "1" -> true
    "false", "no", "0" -> false
    else -> null
  }
}
