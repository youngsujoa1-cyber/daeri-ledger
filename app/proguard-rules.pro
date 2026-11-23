# Keep CameraX / MediaStore model classes referenced via reflection-free but safe defaults.
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# AdMob / Play Services
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.**

# Guava is pulled in by CameraX (ListenableFuture) — silence optional-dependency warnings.
-dontwarn com.google.common.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**

# Keep Kotlin metadata for reflective access in release builds.
-keepattributes *Annotation*, InnerClasses
