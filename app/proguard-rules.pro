# Room database ProGuard rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class com.example.data.local.entity.** { *; }
-keep class com.example.data.local.dao.** { *; }

# Moshi & Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.squareup.moshi.** { *; }
-keep class * extends com.squareup.moshi.JsonAdapter
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}

# Kotlin Serialization
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Firebase & Play Services
-keepattributes EnclosingMethod
-dontwarn com.google.firebase.**
-keep class com.google.firebase.** { *; }

# App Models
-keep class com.example.core.model.** { *; }
-keep class com.example.domain.ai.** { *; }
-keep class com.example.domain.security.** { *; }

# Preserve line numbers for stack traces in release
-keepattributes SourceFile,LineNumberTable
