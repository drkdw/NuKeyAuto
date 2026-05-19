package com.ddw.nukeyauto.data.repo

import android.content.SharedPreferences
import android.util.Log
import com.ddw.nukeyauto.BuildConfig
import com.ddw.nukeyauto.data.api.NukiApiService
import com.ddw.nukeyauto.data.PrefKeys
import com.ddw.nukeyauto.data.model.ActionRequest
import com.ddw.nukeyauto.data.model.LockState
import com.ddw.nukeyauto.data.model.NukiError
import com.ddw.nukeyauto.data.model.UiState
import java.io.IOException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for Nuki smart lock state.
 * Handles credential storage, API calls, and state mapping.
 */
@Singleton
class NukiRepository @Inject constructor(
    private val api: NukiApiService,
    private val prefs: SharedPreferences
) {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Persist Nuki Web API credentials to encrypted storage.
     */
    fun saveCredentials(token: String, id: String) {
        prefs.edit()
            .putString(PrefKeys.NUKI_TOKEN, token)
            .putString(PrefKeys.NUKI_ID, id)
            .apply()
    }

    /**
     * Check whether both API token and Smart Lock ID are configured.
     */
    fun hasCredentials(): Boolean {
        return try {
            val id = prefs.getString(PrefKeys.NUKI_ID, "").orEmpty()
            val token = prefs.getString(PrefKeys.NUKI_TOKEN, "").orEmpty()
            id.isNotEmpty() && token.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fetch the current lock state and battery level from the Nuki Web API.
     * Wrapped in try/catch because EncryptedSharedPreferences can throw
     * a SecurityException if the AndroidKeyStore key is invalidated.
     */
    suspend fun fetchState() {
        try {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "fetchState() started")
            }

            // Always signal loading first so StateFlow emits even when
            // the resulting state is identical (fixes stuck SwipeRefreshLayout).
            _uiState.update { it.copy(isLoading = true, error = null) }

            val smartlockId = validateAndGetSmartlockId() ?: return

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "API call for Smart Lock ID: $smartlockId")
            }

            val response = api.getSmartLockState(smartlockId)
            val batteryLevel = response.state.batteryCharge
            val nukiStateInt = response.state.state

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "API response: state=$nukiStateInt, battery=$batteryLevel%")
            }

            val lockState = when (nukiStateInt) {
                0 -> LockState.Uncalibrated
                1 -> LockState.Locked
                2 -> LockState.Unlocking
                3 -> LockState.Unlocked
                4 -> LockState.Locking
                5 -> LockState.Unlatched
                7 -> LockState.Unlatching
                254 -> LockState.MotorBlocked
                255 -> LockState.Undefined
                else -> LockState.Unknown
            }

            // Surface unknown state codes as an error so the UI never stays on "Loading..."
            val error = if (lockState == LockState.Unknown) {
                NukiError.UnknownLockState(nukiStateInt)
            } else {
                null
            }

            _uiState.update {
                it.copy(lockState = lockState, battery = batteryLevel, isLoading = false, error = error)
            }

        } catch (e: Exception) {
            Log.e(TAG, "fetchState() failed: ${e::class.simpleName} — ${e.message}", e)
            _uiState.update {
                it.copy(isLoading = false, error = mapException(e))
            }
        }
    }

    /**
     * Execute a lock action and poll until the state stabilizes.
     * Action 2 = lock, Action 3 = unlatch (physically open — NOT action 1 unlock).
     */
    suspend fun performLockAction(shouldLock: Boolean) {
        try {
            val smartlockId = validateAndGetSmartlockId() ?: return

            _uiState.update { it.copy(isLoading = true) }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "performLockAction: shouldLock=$shouldLock")
            }

            // Apply user-configured delay before opening (not locking)
            if (!shouldLock) {
                val delaySec = getOpenDelaySeconds()
                if (delaySec > 0) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Waiting ${delaySec}s before opening...")
                    }
                    _uiState.update { it.copy(countdownSeconds = delaySec) }
                    for (i in delaySec downTo 1) {
                        _uiState.update { it.copy(countdownSeconds = i) }
                        delay(1000L)
                    }
                    _uiState.update { it.copy(countdownSeconds = 0) }
                }
            }

            val action = if (shouldLock) 2 else 3
            api.performAction(smartlockId, ActionRequest(action))

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Action $action sent, waiting for physical movement...")
            }

            // Initial wait for the physical movement to start
            delay(LOCK_MOVEMENT_DELAY_MS)
            fetchState()

            // Poll until state is stable (not a transition state)
            var pollCount = 0
            while (pollCount < MAX_POLL_ATTEMPTS && isTransitionState(_uiState.value.lockState)) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "State still transitioning (${_uiState.value.lockState}), polling... ($pollCount)")
                }
                delay(POLL_INTERVAL_MS)
                fetchState()
                pollCount++
            }

        } catch (e: Exception) {
            Log.e(TAG, "performLockAction() failed: ${e.message}", e)
            _uiState.update {
                it.copy(isLoading = false, error = mapException(e))
            }
        }
    }

    /**
     * Read and validate credentials from encrypted prefs.
     * Returns the parsed smartlock ID, or null if credentials are missing or invalid.
     * If null is returned, the appropriate error has already been written to [uiState].
     */
    private fun validateAndGetSmartlockId(): Long? {
        val id = prefs.getString(PrefKeys.NUKI_ID, "").orEmpty()
        val token = prefs.getString(PrefKeys.NUKI_TOKEN, "").orEmpty()
        if (id.isEmpty() || token.isEmpty()) {
            if (BuildConfig.DEBUG) Log.w(TAG, "No credentials configured")
            _uiState.update { it.copy(isLoading = false, error = NukiError.NoCredentials) }
            return null
        }
        val smartlockId = id.toLongOrNull()
        if (smartlockId == null) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Invalid Smart Lock ID: '$id'")
            _uiState.update { it.copy(isLoading = false, error = NukiError.InvalidSmartLockId) }
            return null
        }
        return smartlockId
    }

    /**
     * Check whether the given lock state is a transition (in-progress) state.
     */
    private fun isTransitionState(state: LockState): Boolean {
        return state in listOf(LockState.Locking, LockState.Unlocking, LockState.Unlatching)
    }

    /**
     * Get the configured delay in seconds before opening the door.
     * Defaults to 0 (no delay).
     */
    fun getOpenDelaySeconds(): Int {
        return try {
            prefs.getInt(PrefKeys.OPEN_DELAY_SECONDS, 0)
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Check whether the user wants a confirmation dialog before door actions.
     * Defaults to true (confirmation enabled).
     */
    fun isConfirmationEnabled(): Boolean {
        return try {
            prefs.getBoolean(PrefKeys.CONFIRM_ACTION, true)
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Map a caught exception to the appropriate [NukiError] type.
     * IOException → connection problem, HttpException → HTTP status error,
     * everything else → generic API exception with class name for debugging.
     */
    private fun mapException(e: Exception): NukiError {
        return when (e) {
            is IOException -> NukiError.ConnectionFailed
            is HttpException -> NukiError.HttpError(e.code())
            else -> NukiError.ApiException("${e::class.simpleName}: ${e.message ?: "Unknown error"}")
        }
    }

    companion object {
        private const val TAG = "NukiRepository"
        private const val LOCK_MOVEMENT_DELAY_MS = 3500L
        private const val POLL_INTERVAL_MS = 2000L
        private const val MAX_POLL_ATTEMPTS = 5
    }
}
