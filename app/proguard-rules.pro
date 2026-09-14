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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# Keep model classes to prevent Firebase deserialization errors
-keep class com.example.newtacks.models.** { *; }

# Keep Cloudinary callback classes
-keep class com.cloudinary.android.** { *; }

# Keep MapLibre classes
-keep class org.maplibre.android.** { *; }