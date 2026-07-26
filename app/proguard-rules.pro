# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# --- kotlinx.serialization ---
# Room/Hilt ship their own consumer R8 rules; serialization needs ours. @Serializable types are
# reflected into at runtime via their generated $$serializer, so keep the serializers and the
# annotated classes (entities in data/local, the backup DTOs in data/backup).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep the companion `serializer()` and the synthetic $$serializer for every @Serializable type.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.seblful.klas.data.**$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.seblful.klas.data.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
