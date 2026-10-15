# Kotlinx Serialization: keep @Serializable metadata and generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class com.zoomers.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.zoomers.app.**$$serializer { *; }
-keep @kotlinx.serialization.Serializable class com.zoomers.app.** { *; }

# Retrofit
-keepattributes Signature, Exceptions
-keep interface com.zoomers.app.core.network.** { *; }
