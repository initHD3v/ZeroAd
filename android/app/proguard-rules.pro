# ZeroAd ProGuard / R8 Rules

# Keep ZeroAd engine classes (used via reflection/Service)
-keep class com.hidayatfauzi6.zeroad.AdBlockVpnService { *; }
-keep class com.hidayatfauzi6.zeroad.engine.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.hidayatfauzi6.zeroad.**$$serializer { *; }
-keepclassmembers class com.hidayatfauzi6.zeroad.** {
    *** Companion;
}
-keepclasseswithmembers class com.hidayatfauzi6.zeroad.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Sentry (crash reporting)
-keep class io.sentry.** { *; }
-dontwarn io.sentry.**

# Keep Flutter engine
-keep class io.flutter.** { *; }
-dontwarn io.flutter.**
