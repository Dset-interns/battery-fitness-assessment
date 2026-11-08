# The page calls these through window.AndroidBridge; renaming or removing them would break printing and saving.
-keepclassmembers class com.batteryfitness.webapp.WebAppActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
