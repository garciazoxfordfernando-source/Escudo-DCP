# Escudo R8 / ProGuard rules
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Android system components (DeviceAdminReceiver, VpnService, Activities)
-keep public class com.example.service.EscudoDeviceAdminReceiver { *; }
-keep public class com.example.service.EscudoVpnService { *; }
-keep public class com.example.service.ShortcutActionActivity { *; }
-keep public class com.example.service.AppPackageMonitorReceiver { *; }
-keep,includedescriptorclasses class com.example.data.local.av.**$$serializer { *; }

# Shizuku UserService and AIDL interface (instantiated via reflection in app_process)
-keep public class com.example.service.EscudoShizukuUserService {
    public <init>(...);
    public *;
}
-keep interface com.example.shizuku.IEscudoShizukuService { *; }
-keep class com.example.shizuku.IEscudoShizukuService$Stub { *; }
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# Kotlinx Serialization routes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.ui.navigation.**$$serializer { *; }
-keepclassmembers class com.example.ui.navigation.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.ui.navigation.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# BiometricPrompt & FragmentActivity
-keepclassmembers class androidx.biometric.** { *; }

# Room & Hilt
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
