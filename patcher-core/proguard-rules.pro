# 基本配置
-ignorewarnings
-dontwarn **

# 主类
-keep class patcher.core.PatcherEntry {
    public static void main(java.lang.String[]);
}

# Kotlin 反射相关
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keep class kotlin.jvm.internal.** { *; }

# Kotlin 协程
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.coroutines.internal.** { *; }
-keep class kotlinx.coroutines.android.** { *; }

# 所有注解
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations

# 泛型信息
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod