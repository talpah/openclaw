package ai.openclaw.android.voice

/** Pure utility functions for ElevenLabs TTS parameter validation and parsing. */
internal object TalkModeRuntime {
  fun resolveSpeed(speed: Double?, rateWpm: Int?): Double? {
    if (rateWpm != null && rateWpm > 0) {
      val resolved = rateWpm.toDouble() / 175.0
      if (resolved <= 0.5 || resolved >= 2.0) return null
      return resolved
    }
    if (speed != null) {
      if (speed <= 0.5 || speed >= 2.0) return null
      return speed
    }
    return null
  }

  fun validatedUnit(value: Double?): Double? {
    if (value == null) return null
    if (value < 0 || value > 1) return null
    return value
  }

  fun validatedStability(value: Double?, modelId: String?): Double? {
    if (value == null) return null
    val normalized = modelId?.trim()?.lowercase()
    if (normalized == "eleven_v3") {
      return if (value == 0.0 || value == 0.5 || value == 1.0) value else null
    }
    return validatedUnit(value)
  }

  fun validatedSeed(value: Long?): Long? {
    if (value == null) return null
    if (value < 0 || value > 4294967295L) return null
    return value
  }

  fun validatedNormalize(value: String?): String? {
    val normalized = value?.trim()?.lowercase() ?: return null
    return if (normalized in listOf("auto", "on", "off")) normalized else null
  }

  fun validatedLanguage(value: String?): String? {
    val normalized = value?.trim()?.lowercase() ?: return null
    if (normalized.length != 2) return null
    if (!normalized.all { it in 'a'..'z' }) return null
    return normalized
  }

  fun validatedOutputFormat(value: String?): String? {
    val trimmed = value?.trim()?.lowercase() ?: return null
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("mp3_")) return trimmed
    return if (parsePcmSampleRate(trimmed) != null) trimmed else null
  }

  fun validatedLatencyTier(value: Int?): Int? {
    if (value == null) return null
    if (value < 0 || value > 4) return null
    return value
  }

  fun parsePcmSampleRate(value: String?): Int? {
    val trimmed = value?.trim()?.lowercase() ?: return null
    if (!trimmed.startsWith("pcm_")) return null
    val suffix = trimmed.removePrefix("pcm_")
    val digits = suffix.takeWhile { it.isDigit() }
    val rate = digits.toIntOrNull() ?: return null
    return if (rate in setOf(16000, 22050, 24000, 44100)) rate else null
  }

  fun isMessageTimestampAfter(timestamp: Double, sinceSeconds: Double): Boolean {
    val sinceMs = sinceSeconds * 1000
    return if (timestamp > 10_000_000_000) {
      timestamp >= sinceMs - 500
    } else {
      timestamp >= sinceSeconds - 0.5
    }
  }
}
