# ProGuard rules for TOols application

# Keep Hilt-generated classes
-keep class hilt_aggregated_deps
-keep class **_HiltModules { *; }
-keep class **_Factory { *; }
-keep class **_Impl { *; }

# Keep Jetpack Compose
-keep class androidx.compose.** { *; }

# Keep Room Database
-keep class androidx.room.** { *; }
-keepattributes Signature,RuntimeVisibleAnnotations
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlin.reflect.jvm.internal.**

# Keep Application class
-keep public class com.tools.maestro.TOolsApplication

# Keep MainActivity
-keep public class com.tools.maestro.ui.MainActivity

# Keep Models
-keep class com.tools.maestro.domain.model.** { *; }
-keep class com.tools.maestro.data.local.entity.** { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Remove logging in Release builds
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
