# Add project-specific ProGuard rules here.
# MediaPipe and TFLite use reflection; keep their classes if minification is ever enabled.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.mlkit.** { *; }
