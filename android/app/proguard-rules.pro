# BioKey R8 rules. kotlinx.serialization, Room, Firebase, WorkManager and zxing ship their own
# consumer rules; these cover what reflection/JNI reach beyond them.

# Protocol DTOs exchanged with pcbu-desktop (serialized by name) — keep names/fields stable.
-keep class org.pcbu.libreclient.protocol.model.** { *; }
-keepclassmembers class org.pcbu.libreclient.** {
    *** Companion;
}
-keepclasseswithmembers class org.pcbu.libreclient.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Components referenced only from the manifest are kept by AAPT; keep line numbers for crash logs.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
