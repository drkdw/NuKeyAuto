package com.ddw.nukeyauto.di

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ddw.nukeyauto.BuildConfig
import com.ddw.nukeyauto.data.PrefKeys
import com.ddw.nukeyauto.data.api.NukiApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.KeyStore
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Hilt module providing app-wide singletons: encrypted SharedPreferences and Retrofit API client.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val TAG = "NukiAuth"
    private const val PREFS_FILE = "nuki_secret_prefs"
    private const val BASE_URL = "https://api.nuki.io/"

    /**
     * Provide encrypted SharedPreferences backed by AndroidKeyStore.
     * If the encrypted file is corrupted (e.g. after reinstall with backup restore),
     * delete it and create a fresh instance. The user will need to re-enter credentials.
     */
    // Suppressed: as of security-crypto 1.1.0 the whole Jetpack Security crypto API is
    // deprecated in favour of using the Android Keystore directly. It still works and is
    // now a stable release; migrating off it is a separate job (it touches how the stored
    // token is encrypted, so it needs its own upgrade test).
    @Suppress("DEPRECATION")
    @Provides
    @Singleton
    fun provideSharedPrefs(@ApplicationContext context: Context): SharedPreferences {
        return try {
            createEncryptedPrefs(context, buildMasterKey(context))
        } catch (first: Exception) {
            // KeyStore errors can be transient (seen on some devices right after an OS update):
            // retry once before treating the file as corrupt and wiping the stored token.
            Log.w(TAG, "Encrypted prefs failed, retrying once: ${first.message}")
            try {
                createEncryptedPrefs(context, buildMasterKey(context))
            } catch (e: Exception) {
                resetEncryptedPrefs(context, e)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun resetEncryptedPrefs(context: Context, e: Exception): SharedPreferences {
        // Corrupted prefs file or an invalidated Keystore key. Wipe both: the file is
        // unreadable either way, and retrying with the same broken key would crash the
        // app on every launch (this runs during Application.onCreate injection).
        Log.e(TAG, "Encrypted prefs corrupted, resetting: ${e.message}")
        context.deleteSharedPreferences(PREFS_FILE)
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        return createEncryptedPrefs(context, buildMasterKey(context))
    }

    @Suppress("DEPRECATION")
    private fun buildMasterKey(context: Context): MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    @Suppress("DEPRECATION")
    private fun createEncryptedPrefs(context: Context, masterKey: MasterKey): SharedPreferences {
        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Provide the Nuki Web API Retrofit client with Bearer token auth interceptor.
     */
    @Provides
    @Singleton
    fun provideNukiApi(prefs: SharedPreferences): NukiApiService {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10.seconds)
            .readTimeout(15.seconds)
            .writeTimeout(15.seconds)
            // Auth interceptor: reads token from encrypted prefs on every request
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                try {
                    val token = prefs.getString(PrefKeys.NUKI_TOKEN, "").orEmpty()
                    if (token.isNotEmpty()) {
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                        requestBuilder.header("Accept", "application/json")
                    } else Log.w(TAG, "Token is empty, request sent without auth header")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read token: ${e.message}", e)
                }
                chain.proceed(requestBuilder.build())
            }

        // Logger after auth so headers are visible in debug output. The constant branch
        // lets R8 strip okhttp-logging from the release build entirely.
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                    // The bearer token unlocks the front door, never print it.
                    redactHeader("Authorization")
                }
            )
        }

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(builder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NukiApiService::class.java)
    }
}
