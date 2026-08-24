# Optional compatibility wrapper referenced by Material test dependencies but unused by this test APK.
-dontwarn androidx.appcompat.graphics.drawable.DrawableWrapper

# Compile-time annotation API retained in Error Prone annotations but never loaded on Android.
-dontwarn javax.lang.model.element.Modifier

# Instrumentation and its tracing dependency are entered from the test manifest rather than
# ordinary app code. Keep the complete test runtime reachable in the minified release test APK.
-keep class androidx.test.** { *; }
-keep class androidx.tracing.** { *; }
