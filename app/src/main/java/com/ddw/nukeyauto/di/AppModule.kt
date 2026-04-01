package com.ddw.nukeyauto.di

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ddw.nukeyauto.BuildConfig
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
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

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
    @Provides
    @Singleton
    fun provideSharedPrefs(@ApplicationContext context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return try {
            createEncryptedPrefs(context, masterKey)
        } catch (e: Exception) {
            // Corrupted prefs file (ClassCastException, SecurityException, etc.)
            // Delete the corrupted files and retry
            Log.e(TAG, "Encrypted prefs corrupted, resetting: ${e.message}")
            deletePrefsFiles(context)
            createEncryptedPrefs(context, masterKey)
        }
    }

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

    private fun deletePrefsFiles(context: Context) {
        val prefsDir = File(context.filesDir.parent, "shared_prefs")
        val prefsFile = File(prefsDir, "$PREFS_FILE.xml")
        if (prefsFile.exists()) {
            prefsFile.delete()
        }
    }

    /**
     * Provide the Nuki Web API Retrofit client with Bearer token auth interceptor.
     */
    @Provides
    @Singleton
    fun provideNukiApi(prefs: SharedPreferences): NukiApiService {
        val logLevel = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
        val logger = HttpLoggingInterceptor().apply { level = logLevel }

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            // Auth interceptor: reads token from encrypted prefs on every request
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                try {
                    val token = prefs.getString("nuki_token", "") ?: ""
                    if (token.isNotEmpty()) {
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                        requestBuilder.header("Accept", "application/json")
                    } else if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Token is empty — request sent without auth header")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read token: ${e.message}", e)
                }
                chain.proceed(requestBuilder.build())
            }
            // Logger after auth so headers are visible in debug output
            .addInterceptor(logger)
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NukiApiService::class.java)
    }
}
