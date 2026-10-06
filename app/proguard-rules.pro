# Retrofit, OkHttp, Gson, Hilt, security-crypto and the Car App Library ship their own
# consumer rules; only app-specific rules belong here.

# Gson fills the API models by reflection
-keep class com.ddw.nukeyauto.data.model.** { *; }
-keepattributes RuntimeVisibleAnnotations,Signature

# Release builds drop debug and warning logs (no BuildConfig.DEBUG guards needed).
# Log.e stays: crash-relevant, and never contains the token.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int w(...);
}

# Readable stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
