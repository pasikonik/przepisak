# Reguły R8 dla wydania "release".
#
# Domyślnie R8 jest WYŁĄCZONY (patrz app/build.gradle.kts) — aplikacja jest na
# własny użytek, a Supabase i Ktor sięgają po refleksję. Te reguły są gotowe,
# jeśli chcesz włączyć skracanie; po włączeniu przetestuj RĘCZNIE import przepisu
# i synchronizację, bo to ścieżki, które R8 psuje najchętniej.

# --- kotlinx.serialization ---
# Serializatory są generowane statycznie, ale R8 nie widzi powiązania klasa->serializer.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class pl.przepisak.**$$serializer { *; }
-keepclassmembers class pl.przepisak.** {
    *** Companion;
}
-keepclasseswithmembers class pl.przepisak.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Ktor + OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }

# --- Supabase (używa kotlin-reflect) ---
-dontwarn io.github.jan.supabase.**
-keep class io.github.jan.supabase.** { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# --- Hilt / Dagger ---
-dontwarn dagger.hilt.**

# --- Ksoup ---
-dontwarn com.fleeksoft.ksoup.**

# --- Modele domenowe i encje trzymamy w całości ---
# Room i serializacja odwołują się do nich po nazwach pól.
-keep class pl.przepisak.domain.model.** { *; }
-keep class pl.przepisak.data.local.entity.** { *; }
-keep class pl.przepisak.data.local.relation.** { *; }
-keep class pl.przepisak.data.remote.** { *; }

# Zachowaj nazwy plików i numery linii w zgłoszeniach awarii.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
