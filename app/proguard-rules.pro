# SwipeDelete Zero — R8/ProGuard rules.
# The app is 100% offline; no reflection-heavy networking libs are bundled.

# Keep Room generated code
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }

# Keep Hilt generated components
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }

# Compose
-keepclassmembers class androidx.compose.** { *; }

# WorkManager workers are instantiated by name
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
