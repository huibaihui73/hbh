# v1 未开启混淆。开启时保留 Room / 序列化 / OkHttp 所需规则。
-keep class com.quntodo.app.data.db.** { *; }
-keepclassmembers class * {
    @androidx.room.* <fields>;
}
-keepattributes Signature, InnerClasses, EnclosingMethod
-keep,includedescriptorclasses class com.quntodo.app.**$$serializer { *; }
-keepclassmembers class com.quntodo.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.quntodo.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn okhttp3.**
-dontwarn okio.**
