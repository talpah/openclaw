# ── kotlinx.serialization ────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep class kotlinx.serialization.** { *; }

# ── Parcelable ────────────────────────────────────────────────────
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ── WebView @JavascriptInterface ──────────────────────────────────
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ── DeviceIdentityStore / crypto key material ─────────────────────
-keep class ai.openclaw.android.gateway.DeviceIdentityStore { *; }
-keep class ai.openclaw.android.gateway.DeviceIdentity { *; }

# ── Bouncy Castle ─────────────────────────────────────────────────
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ── CameraX ───────────────────────────────────────────────────────
-keep class androidx.camera.** { *; }

# ── OkHttp ────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.internal.platform.** { *; }

# ── Misc suppressions ────────────────────────────────────────────
-dontwarn com.sun.jna.**
-dontwarn javax.naming.**
-dontwarn lombok.Generated
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn sun.net.spi.nameservice.NameServiceDescriptor
