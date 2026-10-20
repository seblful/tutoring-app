# --- kotlinx.serialization ---
# Keep generated serializers for the serializable Room entities and backup models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Keep the companion `serializer()` and generated serializer classes.
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
