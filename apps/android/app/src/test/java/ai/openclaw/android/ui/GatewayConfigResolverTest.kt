package ai.openclaw.android.ui

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GatewayConfigResolverTest {
  @Test
  fun resolveScannedSetupCodeAcceptsRawSetupCode() {
    val setupCode = encodeSetupCode("""{"url":"wss://gateway.example:18789","token":"token-1"}""")

    val resolved = resolveScannedSetupCode(setupCode)

    assertEquals(setupCode, resolved)
  }

  @Test
  fun resolveScannedSetupCodeAcceptsQrJsonPayload() {
    val setupCode = encodeSetupCode("""{"url":"wss://gateway.example:18789","password":"pw-1"}""")
    val qrJson =
      """
      {
        "setupCode": "$setupCode",
        "gatewayUrl": "wss://gateway.example:18789",
        "auth": "password",
        "urlSource": "gateway.remote.url"
      }
      """.trimIndent()

    val resolved = resolveScannedSetupCode(qrJson)

    assertEquals(setupCode, resolved)
  }

  @Test
  fun resolveScannedSetupCodeRejectsInvalidInput() {
    val resolved = resolveScannedSetupCode("not-a-valid-setup-code")
    assertNull(resolved)
  }

  @Test
  fun resolveScannedSetupCodeRejectsJsonWithInvalidSetupCode() {
    val qrJson = """{"setupCode":"invalid"}"""
    val resolved = resolveScannedSetupCode(qrJson)
    assertNull(resolved)
  }

  @Test
  fun resolveScannedSetupCodeRejectsJsonWithNonStringSetupCode() {
    val qrJson = """{"setupCode":{"nested":"value"}}"""
    val resolved = resolveScannedSetupCode(qrJson)
    assertNull(resolved)
  }

  // ── parseGatewayEndpoint port defaulting ─────────────────────────────────

  @Test
  fun parseGatewayEndpointWssNoPortDefaultsTo443() {
    val result = parseGatewayEndpoint("wss://smarty.tailc0f6de.ts.net")
    assertEquals(443, result?.port)
    assertEquals(true, result?.tls)
    assertEquals("smarty.tailc0f6de.ts.net", result?.host)
  }

  @Test
  fun parseGatewayEndpointHttpsNoPortDefaultsTo443() {
    val result = parseGatewayEndpoint("https://gateway.example.com")
    assertEquals(443, result?.port)
    assertEquals(true, result?.tls)
  }

  @Test
  fun parseGatewayEndpointWsNoPortDefaultsTo80() {
    val result = parseGatewayEndpoint("ws://gateway.local")
    assertEquals(80, result?.port)
    assertEquals(false, result?.tls)
  }

  @Test
  fun parseGatewayEndpointHttpNoPortDefaultsTo80() {
    val result = parseGatewayEndpoint("http://10.0.2.2")
    assertEquals(80, result?.port)
    assertEquals(false, result?.tls)
  }

  @Test
  fun parseGatewayEndpointExplicitPortOverridesDefault() {
    val result = parseGatewayEndpoint("wss://gateway.example.com:18789")
    assertEquals(18789, result?.port)
    assertEquals(true, result?.tls)
  }

  @Test
  fun parseGatewayEndpointNoSchemeNormalizesToHttpsAndPort443() {
    // Bare hostnames get normalized to https:// by the parser.
    val result = parseGatewayEndpoint("gateway.local")
    assertEquals(443, result?.port)
    assertEquals(true, result?.tls)
  }

  private fun encodeSetupCode(payloadJson: String): String {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.toByteArray(Charsets.UTF_8))
  }
}
