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

# Preserve line number information for debugging stack traces, plus annotations
# used by JNA/UniFFI structure layout metadata.
-keepattributes SourceFile,LineNumberTable,*Annotation*

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
# BDK uses UniFFI/JNA. Preserve only the bridge pieces that JNA/native code
# resolves by name; regular API/data classes may still be optimized.
-keep class org.bitcoindevkit.UniffiLib { *; }
-keep class org.bitcoindevkit.IntegrityCheckingUniffiLib { *; }
-keep class org.bitcoindevkit.RustBuffer { public *; }
-keep class org.bitcoindevkit.RustBuffer$ByReference { public *; }
-keep class org.bitcoindevkit.RustBuffer$ByValue { public *; }
-keep class org.bitcoindevkit.UniffiRustCallStatus { public *; }
-keep class org.bitcoindevkit.UniffiRustCallStatus$ByValue { public *; }
-keep class org.bitcoindevkit.UniffiForeignFuture* { public *; }
-keep class org.bitcoindevkit.UniffiVTable* { public *; }
-keep class org.bitcoindevkit.UniffiCallback* { *; }
-keep class org.bitcoindevkit.uniffiCallback* { *; }
-keep class org.bitcoindevkit.* implements com.sun.jna.Library { *; }
-keep interface org.bitcoindevkit.* extends com.sun.jna.Library { *; }
-keep class org.bitcoindevkit.* implements com.sun.jna.Callback { *; }
-keep interface org.bitcoindevkit.* extends com.sun.jna.Callback { *; }

# ==================== LWK (Liquid Wallet Kit) ====================
# LWK's generated Android bindings live in the `lwk` package, not
# `com.blockstream.lwk`. Keep the UniFFI/JNA bridge names used reflectively.
-keep class lwk.UniffiLib { *; }
-keep class lwk.RustBuffer* { public *; }
-keep class lwk.UniffiRust* { public *; }
-keep class lwk.UniffiForeignFuture* { public *; }
-keep class lwk.UniffiVTable* { public *; }
-keep class lwk.UniffiCallback* { *; }
-keep class lwk.uniffiCallback* { *; }
-keep class lwk.* implements com.sun.jna.Library { *; }
-keep interface lwk.* extends com.sun.jna.Library { *; }
-keep class lwk.* implements com.sun.jna.Callback { *; }
-keep interface lwk.* extends com.sun.jna.Callback { *; }

# ==================== Spark SDK ====================
# Breez Spark's generated Android bindings live in the `breez_sdk_spark`
# package and use UniFFI/JNA. Preserve bridge names without freezing every
# generated data/API class.
-keep class breez_sdk_spark.UniffiLib { *; }
-keep class breez_sdk_spark.IntegrityCheckingUniffiLib { *; }
-keep class breez_sdk_spark.bindings.UniffiLib { *; }
-keep class breez_sdk_spark.bindings.IntegrityCheckingUniffiLib { *; }
-keep class breez_sdk_spark.**.UniffiLib { *; }
-keep class breez_sdk_spark.**.IntegrityCheckingUniffiLib { *; }
-keep class breez_sdk_spark.**.RustBuffer* { public *; }
-keep class breez_sdk_spark.**.UniffiRust* { public *; }
-keep class breez_sdk_spark.**.UniffiForeignFuture* { public *; }
-keep class breez_sdk_spark.**.UniffiVTable* { public *; }
-keep class breez_sdk_spark.**.UniffiCallback* { *; }
-keep class breez_sdk_spark.**.uniffiCallback* { *; }

# ==================== Layer 2 enums persisted via .name/valueOf() ====================
-keepclassmembers enum github.aeonbtc.ibiswallet.data.model.WalletLayer { *; }
-keepclassmembers enum github.aeonbtc.ibiswallet.data.model.SwapDirection { *; }
-keepclassmembers enum github.aeonbtc.ibiswallet.data.model.SwapService { *; }

# ==================== JNA (used by BDK via UniFFI) ====================
# BDK's native code accesses JNA fields/classes via JNI reflection (e.g.
# Pointer.peer). R8 must not rename or strip them.
-keep class com.sun.jna.* { *; }
-keepclassmembers class com.sun.jna.* { *; }
-keep class * extends com.sun.jna.Structure { *; }
-keep class * implements com.sun.jna.Structure$ByValue { *; }
-keep class * implements com.sun.jna.Structure$ByReference { *; }
-keep @com.sun.jna.Structure$FieldOrder class * { *; }
-keepclassmembers class * extends com.sun.jna.Structure {
    public *;
}
# JNA bundles desktop AWT helpers that reference java.awt.* — not available on Android
-dontwarn java.awt.**

# ==================== Tor ====================
-keep class org.torproject.jni.** { *; }

# ==================== BC-UR / Hummingbird ====================
-keep class com.sparrowwallet.hummingbird.** { *; }

# ==================== OkHttp ====================
# OkHttp ships its own consumer rules; just suppress platform warnings.
-dontwarn okhttp3.**
-dontwarn okio.**

# ==================== Compose ====================
# Compose ships its own consumer rules — no blanket keep needed.

# ==================== Crypto ====================
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }
-keep class androidx.security.crypto.** { *; }

# ==================== CameraX ====================
# CameraX ships its own consumer rules — no blanket keep needed.

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
# Only keep the classes we actually use for encoding/decoding QR codes.
-keep class com.google.zxing.MultiFormatReader { *; }
-keep class com.google.zxing.BinaryBitmap { *; }
-keep class com.google.zxing.RGBLuminanceSource { *; }
-keep class com.google.zxing.common.GlobalHistogramBinarizer { *; }
-keep class com.google.zxing.qrcode.QRCodeWriter { *; }
-keep class com.google.zxing.qrcode.decoder.ErrorCorrectionLevel { *; }
-keep class com.google.zxing.BarcodeFormat { *; }
-keep class com.google.zxing.EncodeHintType { *; }
# ZXing uses ServiceLoader to discover format readers/writers
-keep class * implements com.google.zxing.Reader
-keep class * implements com.google.zxing.Writer
