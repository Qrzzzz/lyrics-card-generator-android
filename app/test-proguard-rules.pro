# Optional compatibility wrapper referenced by Material test dependencies but unused by this test APK.
-dontwarn androidx.appcompat.graphics.drawable.DrawableWrapper

# Compile-time annotation API retained in Error Prone annotations but never loaded on Android.
-dontwarn javax.lang.model.element.Modifier
