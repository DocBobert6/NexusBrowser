# Keep WebView related
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
