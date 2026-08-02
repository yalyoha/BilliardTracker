# === BilliardTracker ProGuard/R8 rules ===

# Общие: сохраняем аннотации и generic-типы (нужно для kotlinx-serialization + Retrofit).
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable, RuntimeVisibleAnnotations, EnclosingMethod

# Kotlin метаданные — обязательно для reflection.
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }

# === kotlinx.serialization ===
# Keep @Serializable classes + generated Companion serializers.
-keep,includedescriptorclasses class com.example.billiardtracker.**$$serializer { *; }
-keepclassmembers class com.example.billiardtracker.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.billiardtracker.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }

# === Retrofit ===
-keepattributes Signature
-keepattributes Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Ignore R8 warnings for optional Retrofit deps.
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# Retrofit interfaces с annotations — сохраняем method signatures.
-keep,allowobfuscation,allowshrinking @interface retrofit2.http.*
-keep interface com.example.billiardtracker.data.remote.ApiService { *; }

# === Room ===
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class androidx.room.** { *; }
-dontwarn androidx.room.**

# === Compose ===
# По умолчанию R8 понимает Compose, ничего доп. не нужно, но подавляем warnings.
-dontwarn androidx.compose.**

# === DataStore Preferences ===
-keep class androidx.datastore.** { *; }

# === OkHttp SSE + Logging ===
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# === Play Services Location ===
-keep class com.google.android.gms.location.** { *; }
-dontwarn com.google.android.gms.**

# === Наши DTO — все data-class'ы с @Serializable сохраняем целиком ===
-keep @kotlinx.serialization.Serializable class com.example.billiardtracker.** { *; }

# === Entity/DAO нашего Room ===
-keep class com.example.billiardtracker.data.local.entity.** { *; }
-keep interface com.example.billiardtracker.data.local.dao.** { *; }

# === Отключаем предупреждения о missing classes от unused deps ===
-dontwarn java.lang.invoke.**

# === kotlinx-coroutines ===
# CoroutineExceptionHandler + другие context elements имеют Key объекты,
# нужны reflection-friendly.
-keep class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepnames class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# === WorkManager ===
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**
