-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class org.jsoup.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.astratv.app.Library$Title { *; }
-keep class com.astratv.app.Library$Addon { *; }
