package com.ddw.nukeyauto.data.repo

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.ddw.nukeyauto.data.api.NukiApiService
import com.ddw.nukeyauto.data.PrefKeys
import com.ddw.nukeyauto.data.model.ActionRequest
import com.ddw.nukeyauto.data.model.LockState
import com.ddw.nukeyauto.data.model.NukiError
import com.ddw.nukeyauto.data.model.UiState
import com.ddw.nukeyauto.data.model.isTransition
import com.ddw.nukeyauto.data.safeBoolean
import com.ddw.nukeyauto.data.safeInt
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
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

    /** Set by [cancelOpen], read by the countdown in [performLockAction]. */
    @Volatile private var openCancelled = false

    /**
     * Persist Nuki Web API credentials to encrypted storage.
     */
    fun saveCredentials(token: String, id: String) {
        prefs.edit {
            putString(PrefKeys.NUKI_TOKEN, token)
            putString(PrefKeys.NUKI_ID, id)
        }
    }

    /**
     * Fetch the current lock state and battery level from the Nuki Web API.
     * Wrapped in try/catch because EncryptedSharedPreferences can throw
     * a SecurityException if the AndroidKeyStore key is invalidated.
     */
    suspend fun fetchState() {
        try {
            Log.d(TAG, "fetchState() started")

            // Always signal loading first so StateFlow emits even when
            // the resulting state is identical (fixes stuck SwipeRefreshLayout).
            _uiState.update { it.copy(isLoading = true, error = null) }

            val smartlockId = validateAndGetSmartlockId() ?: return

            Log.d(TAG, "API call for Smart Lock ID: $smartlockId")

            val response = api.getSmartLockState(smartlockId)
            val batteryLevel = response.state.batteryCharge
            val nukiStateInt = response.state.state

            Log.d(TAG, "API response: state=$nukiStateInt, battery=$batteryLevel")

            val lockState = when (nukiStateInt) {
                0 -> LockState.Uncalibrated
                1 -> LockState.Locked
                2 -> LockState.Unlocking
                3 -> LockState.Unlocked
                4 -> LockState.Locking
                5 -> LockState.Unlatched
                6 -> LockState.Unlocked // Lock 'n' Go leaves the lock unlocked
                7 -> LockState.Unlatching
                253 -> LockState.BootRun
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

            // A refresh during an open countdown must not end the loading state: the
            // countdown still owns it, and the phone would re-enable the Lock button.
            _uiState.update {
                it.copy(
                    lockState = lockState,
                    battery = batteryLevel,
                    batteryCritical = response.state.batteryCritical,
                    isLoading = it.countdownSeconds > 0,
                    error = error
                )
            }

        } catch (e: CancellationException) {
            _uiState.update { it.copy(isLoading = it.countdownSeconds > 0) }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "fetchState() failed: ${e::class.simpleName}: ${e.message}", e)
            _uiState.update {
                it.copy(isLoading = it.countdownSeconds > 0, error = mapException(e))
            }
        }
    }

    /**
     * Execute a lock action and poll until the state stabilizes.
     * Action 2 = lock, Action 3 = unlatch (physically open, NOT action 1 unlock).
     */
    suspend fun performLockAction(shouldLock: Boolean) {
        try {
            val smartlockId = validateAndGetSmartlockId() ?: return

            // Clear any previous error so stale errors are not re-rendered by the
            // countdown emissions below (would show one Snackbar/CarToast per tick).
            _uiState.update { it.copy(isLoading = true, error = null) }

            Log.d(TAG, "performLockAction: shouldLock=$shouldLock")

            // Apply user-configured delay before opening (not locking)
            if (!shouldLock) {
                val delaySec = getOpenDelaySeconds()
                if (delaySec > 0) {
                    Log.d(TAG, "Waiting ${delaySec}s before opening...")
                    openCancelled = false
                    for (i in delaySec downTo 1) {
                        _uiState.update { it.copy(countdownSeconds = i) }
                        delay(1000L)
                        // Checked after every tick, so the last check runs right before the action is sent
                        if (openCancelled) {
                            _uiState.update { it.copy(isLoading = false, countdownSeconds = 0) }
                            return
                        }
                    }
                    _uiState.update { it.copy(countdownSeconds = 0) }
                }
            }

            val action = if (shouldLock) 2 else 3
            api.performAction(smartlockId, ActionRequest(action))

            Log.d(TAG, "Action $action sent, waiting for physical movement...")

            // Initial wait for the physical movement to start
            delay(LOCK_MOVEMENT_DELAY_MS)
            fetchState()

            // Poll until state is stable (not a transition state)
            var pollCount = 0
            while (pollCount < MAX_POLL_ATTEMPTS && _uiState.value.lockState.isTransition) {
                Log.d(TAG, "State still transitioning (${_uiState.value.lockState}), polling... ($pollCount)")
                delay(POLL_INTERVAL_MS)
                fetchState()
                pollCount++
            }

        } catch (e: CancellationException) {
            // Scope gone (car session ended, activity finished): don't leave a countdown on screen
            _uiState.update { it.copy(isLoading = false, countdownSeconds = 0) }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "performLockAction() failed: ${e.message}", e)
            _uiState.update {
                it.copy(isLoading = false, error = mapException(e))
            }
        }
    }

    /**
     * Abort a running open-delay countdown before the door is unlatched. Works from either
     * surface, whichever one started the countdown. No-op once the action has been sent.
     */
    fun cancelOpen() {
        openCancelled = true
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
            Log.w(TAG, "No credentials configured")
            _uiState.update { it.copy(isLoading = false, error = NukiError.NoCredentials) }
            return null
        }
        val smartlockId = id.toLongOrNull()
        if (smartlockId == null) {
            Log.w(TAG, "Invalid Smart Lock ID: '$id'")
            _uiState.update { it.copy(isLoading = false, error = NukiError.InvalidSmartLockId) }
            return null
        }
        return smartlockId
    }

    /** Configured delay in seconds before opening the door. Defaults to 0 (no delay). */
    fun getOpenDelaySeconds(): Int = prefs.safeInt(PrefKeys.OPEN_DELAY_SECONDS, 0)

    /** Whether the user wants a confirmation dialog before door actions. Defaults to true. */
    fun isConfirmationEnabled(): Boolean = prefs.safeBoolean(PrefKeys.CONFIRM_ACTION, true)

    /**
     * Map a caught exception to the appropriate [NukiError] type.
     * IOException → connection problem, HttpException → HTTP status error,
     * everything else → generic API exception with class name for debugging.
     */
    private fun mapException(e: Exception): NukiError {
        return when (e) {
            is IOException -> NukiError.ConnectionFailed
            is HttpException -> NukiError.HttpError(e.code())
            else -> NukiError.ApiException // details are in the log line written by the caller
        }
    }

    companion object {
        private const val TAG = "NukiRepository"
        private const val LOCK_MOVEMENT_DELAY_MS = 3500L
        private const val POLL_INTERVAL_MS = 2000L
        private const val MAX_POLL_ATTEMPTS = 5
    }
}
