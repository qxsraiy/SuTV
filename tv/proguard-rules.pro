# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class tv.danmaku.ijk.media.player.** { *; }
-keep class com.aliyun.rts.network.** { *; }
-keep class com.wangsu.httpclient.** { *; }
-keep class org.mozilla.javascript.** { *; }

# 保留所有应用自有类
-keep class top.yogiczy.mytv.** { *; }
-keep class top.yogiczy.slcs.** { *; }
-keepclassmembers class top.yogiczy.mytv.** { *; }
-keepclassmembers class top.yogiczy.slcs.** { *; }

# 保留 Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class top.yogiczy.mytv.**$$serializer { *; }
-keepclassmembers class top.yogiczy.mytv.** {
    *** Companion;
}
-keepclasseswithmembers class top.yogiczy.mytv.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# 保留 Cordis / dynamic plugin 相关
-keep class com.koushikdutta.async.** { *; }
-keep class com.koushikdutta.async.http.** { *; }

# 保留 ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# 保留 Compose
-dontwarn androidx.compose.**

# 保留 WebView JS 接口
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.swing.**

-dontwarn dalvik.system.VMStack
-keep class com.tencent.smtt.** { *; }
-keep class com.tencent.tbs.** { *; }

# Sentry: 保留 OkHttp 集成类 (release 通过 tracesSampleRate=0 减少开销)
-keep class io.sentry.okhttp.** { *; }
-dontwarn io.sentry.okhttp.**
