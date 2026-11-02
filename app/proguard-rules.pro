# Methods called from the injected page script must survive shrinking.
-keepclassmembers class com.adochi.browser.JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
