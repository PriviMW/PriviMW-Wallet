# Keep JNI bridge classes — libwallet-jni.so has hardcoded method signatures
-keep class com.mw.beam.beamwallet.core.** { *; }
-keep class com.mw.beam.beamwallet.core.entities.** { *; }
-keep class com.mw.beam.beamwallet.core.entities.dto.** { *; }
-keep class com.mw.beam.beamwallet.core.listeners.** { *; }

# Keep DApp WebView bridge
-keep class com.privimemobile.dapp.BeamDAppWebView$BeamBridge { *; }
-keepclassmembers class com.privimemobile.dapp.BeamDAppWebView$BeamBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# Tink/Crypto — missing annotation classes (referenced but not needed at runtime)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.http.**

# Keep SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Keep ALL PriviMW code — no removal, no obfuscation, no optimization
# R8 only shrinks/optimizes third-party libraries
-keep class com.privimemobile.** { *; }
-keepclassmembers class com.privimemobile.** { *; }
-dontoptimize
