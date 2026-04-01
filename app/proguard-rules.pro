# ── Retrofit + Gson ──────────────────────────────────────────────
# Keep ALL model classes: class names, constructors, and fields.
# Gson needs reflection to instantiate these and read/write fields.
-keep class com.ddw.nukeyauto.data.model.** { *; }

# Keep @SerializedName annotations (Gson reads these at runtime)
-keepattributes *Annotation*
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Gson TypeToken needs generic signatures
-keepattributes Signature

# Retrofit service interfaces (method signatures read via reflection)
-keep interface com.ddw.nukeyauto.data.api.NukiApiService { *; }

# Retrofit uses generic return types via reflection
-keep,allowobfuscation class retrofit2.** { *; }
-keepclassmembers class retrofit2.** { *; }

# ── OkHttp ──────────────────────────────────────────────────────
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Hilt ────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── Encrypted SharedPreferences ─────────────────────────────────
-keep class androidx.security.crypto.** { *; }

# ── Android Auto ───────────────────────────────────────────────
# Car App Library uses reflection to instantiate Screen subclasses
-keep class com.ddw.nukeyauto.ui.auto.DoorControlScreen { *; }
-keep class com.ddw.nukeyauto.ui.auto.DoorControlService { *; }

# ── Stack traces ────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
