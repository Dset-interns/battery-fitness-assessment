# MediaPipe Tasks ships native code reached reflectively; keep its surface.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }

# Room generated implementations
-keep class * extends androidx.room.RoomDatabase { *; }
