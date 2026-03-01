# OkHttp3
-keepattributes Signature
-keepattributes AnnotationDefault
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**

# Jsoup
-keep class org.jsoup.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# StarFieldView (чтобы анимация не сломалась при обфускации)
-keep class com.example.proxychecker.StarFieldView { *; }