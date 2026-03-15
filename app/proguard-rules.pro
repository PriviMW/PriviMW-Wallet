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
