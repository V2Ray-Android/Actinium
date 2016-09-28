# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in G:\android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# For using GSON @Expose annotation
-keepattributes *Annotation*

# Gson specific classes
-keep class sun.misc.Unsafe { *; }

-dontwarn org.apache.commons.**
-keep class org.apache.commons.** { *;}

-dontwarn rx.**
-keep class rx.** { *;}

-dontwarn app.dinus.**
-keep class app.dinus.** { *;}

-keepclassmembers class ** {
    @com.hwangjr.rxbus.annotation.Subscribe public *;
    @com.hwangjr.rxbus.annotation.Produce public *;
}
-dontwarn com.hwangjr.rxbus.**
-keep class com.hwangjr.rxbus.** { *;}