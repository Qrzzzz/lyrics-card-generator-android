-keepattributes *Annotation*
-keep class com.qrzzzz.lyricscard.model.** { *; }
-keep class * extends androidx.room.RoomDatabase

# The minified release AndroidTest runner loads this transitive dependency from the target APK.
-keep class androidx.tracing.** { *; }

# AndroidX Test's Kotlin implementation also resolves the stdlib through the target APK.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.savedstate.** { *; }
-keep class androidx.activity.** { *; }

# The release AndroidTest APK executes against this exact minified target APK. Target classes are
# library inputs while shrinking the separate test APK, so its calls are not rewritten when target
# methods are renamed or vertically merged. Preserve the target app's test-visible ABI while still
# allowing method-body optimization and private-member shrinking.
-keep,allowoptimization class com.qrzzzz.lyricscard.** {
    public protected *;
}

