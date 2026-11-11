# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
# WebView JS bridge
-keepclassmembers class app.garminytd.auth.TicketBridge {
    @android.webkit.JavascriptInterface <methods>;
}
