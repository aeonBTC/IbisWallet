# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# Hide the original source file name (show "SourceFile" instead)
-renamesourcefileattribute SourceFile

# ==================== Security: Strip debug/verbose logs in release ====================
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ==================== Enums persisted via .name/valueOf() ====================
# These enums are stored as strings in SharedPreferences/backups.
# R8 must not obfuscate their entry names or valueOf()/values() will break.
-keepclassmembers enum github.aeonbtc.ibiswallet.data.model.AddressType { *; }
-keepclassmembers enum github.aeonbtc.ibiswallet.data.model.WalletNetwork { *; }
-keepclassmembers enum github.aeonbtc.ibiswallet.data.local.SecureStorage$SecurityMethod { *; }
-keepclassmembers enum github.aeonbtc.ibiswallet.data.local.SecureStorage$LockTiming { *; }

# ==================== BDK (Bitcoin Dev Kit) ====================
# Keep BDK JNI classes and native methods
-keep class org.bitcoindevkit.** { *; }
-keep class org.rustbitcoin.** { *; }
-keepclassmembers class org.bitcoindevkit.** { native <methods>; }

# ==================== JNA (used by BDK via UniFFI) ====================
# BDK's native code accesses JNA fields/classes via JNI reflection (e.g.
# Pointer.peer). R8 must not rename or strip them.
-keep class com.sun.jna.** { *; }
-keepclassmembers class com.sun.jna.** { *; }
# JNA bundles desktop AWT helpers that reference java.awt.* — not available on Android
-dontwarn java.awt.**

# ==================== Tor ====================
-keep class org.torproject.jni.** { *; }

# ==================== BC-UR / Hummingbird ====================
-keep class com.sparrowwallet.hummingbird.** { *; }

# ==================== OkHttp ====================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ==================== Compose ====================
-keep class androidx.compose.** { *; }

# ==================== Crypto ====================
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }
-keep class androidx.security.crypto.** { *; }

# ==================== CameraX ====================
# Defensive: CameraX ships consumer rules but has had R8 issues historically
-keep class androidx.camera.** { *; }

# ==================== Biometric ====================
# Defensive: BiometricFragment is instantiated via reflection internally
-keep class androidx.biometric.** { *; }

# ==================== TOFU SSL ====================
# TofuTrustManager implements X509TrustManager (accessed by JVM SSL subsystem)
-keep class github.aeonbtc.ibiswallet.util.TofuTrustManager { *; }
-keep class github.aeonbtc.ibiswallet.util.CertificateMismatchException { *; }
-keep class github.aeonbtc.ibiswallet.util.CertificateFirstUseException { *; }

# ==================== Google Error Prone Annotations ====================
# Referenced by Tink (used by security-crypto) at compile time only; not needed at runtime.
-dontwarn com.google.errorprone.annotations.**

# ==================== ZXing (QR) ====================
-keep class com.google.zxing.** { *; }
