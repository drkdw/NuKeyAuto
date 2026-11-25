package com.ddw.nukeyauto.data.repo

import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.ddw.nukeyauto.data.api.NukiApiService
import com.ddw.nukeyauto.data.PrefKeys
import com.ddw.nukeyauto.data.demo.DemoLock
import com.ddw.nukeyauto.data.model.ActionRequest
import com.ddw.nukeyauto.data.model.LockState
import com.ddw.nukeyauto.data.model.NukiError
import com.ddw.nukeyauto.data.model.UiState
import com.ddw.nukeyauto.data.model.isTransition
import com.ddw.nukeyauto.data.safeBoolean
import com.ddw.nukeyauto.data.safeInt
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for Nuki smart lock state.
 * Handles credential storage, API calls, and state mapping.
 */
@Singleton
class NukiRepository @Inject constructor(
    private val realApi: NukiApiService,
    private val prefs: SharedPreferences,
    private val demoLock: DemoLock
) {
    /** Demo mode swaps in a simulated lock; the stored credentials stay untouched. */
    fun isDemoMode(): Boolean = prefs.safeBoolean(PrefKeys.DEMO_MODE, false)

    fun setDemoMode(enabled: Boolean) {
        // A countdown started in one mode must never fire in the other (demo open -> real door)
        cancelOpen()
        prefs.edit { putBoolean(PrefKeys.DEMO_MODE, enabled) }
        // Never show the real lock's state as if it were the demo's, or the other way round
        _uiState.update { UiState() }
    }

    private val api: NukiApiService get() = if (isDemoMode()) demoLock else realApi

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Set by [cancelOpen], read by the countdown in [performLockAction]. */
    @Volatile private var openCancelled = false

    /** Held for the whole of [performLockAction], so phone and car can never send two actions at once. */
    private val actionMutex = Mutex()

    /** A running countdown or lock action owns the loading state; a plain refresh must not end it. */
    private fun UiState.busy(): Boolean = countdownSeconds > 0 || actionMutex.isLocked

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

            // A refresh during a countdown or action must not end the loading state:
            // the action still owns it, and the phone would re-enable the buttons.
            _uiState.update {
                it.copy(
                    lockState = lockState,
                    battery = batteryLevel,
                    batteryCritical = response.state.batteryCritical,
                    isLoading = it.busy(),
                    error = error
                )
            }

        } catch (e: CancellationException) {
            _uiState.update { it.copy(isLoading = it.busy()) }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "fetchState() failed: ${e::class.simpleName}: ${e.message}", e)
            val error = mapException(e)
            _uiState.update {
                if (error.isConfigError()) {
                    // Token revoked or lock gone: the last known state is no longer trustworthy,
                    // and Unknown routes both screens to their "fix your settings" flow.
                    it.copy(lockState = LockState.Unknown, battery = null, batteryCritical = false,
                        isLoading = it.busy(), error = error)
                } else {
                    it.copy(isLoading = it.busy(), error = error)
                }
            }
        }
    }

    /**
     * Execute a lock action and poll until the lock reports the expected result.
     * Action 2 = lock, Action 3 = unlatch (physically open, NOT action 1 unlock).
     * Ignored while another action (from either surface) is still running.
     */
    suspend fun performLockAction(shouldLock: Boolean) {
        if (!actionMutex.tryLock()) {
            Log.w(TAG, "performLockAction ignored: another action is still running")
            return
        }
        var actionSent = false
        // Fixed for the whole action: switching demo mode mid-countdown must not redirect it
        val actionApi = api
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
            actionSent = true
            actionApi.performAction(smartlockId, ActionRequest(action))
            // Show "Locking…" / "Opening…" right away instead of the old state during the
            // movement delay; the polls below replace it with what the lock reports.
            _uiState.update { it.copy(lockState = if (shouldLock) LockState.Locking else LockState.Unlatching) }

            Log.d(TAG, "Action $action sent, waiting for physical movement...")

            // Initial wait for the physical movement to start
            delay(LOCK_MOVEMENT_DELAY_MS)
            fetchState()

            // Poll until the lock reports the expected result. The Web API is cloud-cached,
            // so the first answer can still be the old state (e.g. Unlocked right after Lock).
            val expected = if (shouldLock) LOCK_RESULT else OPEN_RESULT
            var pollCount = 0
            while (pollCount < MAX_POLL_ATTEMPTS && needsAnotherPoll(expected)) {
                Log.d(TAG, "Waiting for $expected, now ${_uiState.value.lockState}, polling... ($pollCount)")
                delay(POLL_INTERVAL_MS)
                fetchState()
                pollCount++
            }
            val end = _uiState.value
            if (end.lockState !in expected && end.lockState !in STOP_STATES && end.error?.isConfigError() != true) {
                markUnconfirmed()
            }

        } catch (e: CancellationException) {
            // Scope gone (car session ended, activity finished): don't leave a countdown on screen
            _uiState.update { it.copy(isLoading = false, countdownSeconds = 0) }
            if (actionSent) markUnconfirmed()
            throw e
        } catch (e: IOException) {
            // Timeout or dropped connection after sending: the request may have reached Nuki
            // and the door may have moved. Never report a plain failure then, or the user
            // taps Open a second time. Only "could not connect at all" is a clean failure.
            Log.e(TAG, "performLockAction() network error: ${e::class.simpleName}: ${e.message}", e)
            val neverArrived = e is ConnectException || e is UnknownHostException
            if (actionSent && !neverArrived) {
                fetchState()
                markUnconfirmed()
            } else {
                _uiState.update { it.copy(error = NukiError.ConnectionFailed) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "performLockAction() failed: ${e.message}", e)
            _uiState.update { it.copy(error = mapException(e)) }
        } finally {
            actionMutex.unlock()
            _uiState.update { it.copy(isLoading = it.countdownSeconds > 0) }
        }
    }

    /**
     * The action may or may not have happened. Never leave our own "Locking…"/"Opening…"
     * on screen then: it disables the buttons with no way out. Unknown shows a warning
     * with Retry instead.
     */
    private fun markUnconfirmed() {
        _uiState.update {
            it.copy(
                lockState = if (it.lockState.isTransition) LockState.Unknown else it.lockState,
                error = NukiError.ActionNotConfirmed
            )
        }
    }

    /**
     * True while the lock has not reached [expected] and polling can still change that:
     * stops on an error, and on stable problem states the lock itself already reports.
     */
    private fun needsAnotherPoll(expected: Set<LockState>): Boolean {
        val state = _uiState.value
        return state.error == null && state.lockState !in expected && state.lockState !in STOP_STATES
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
        if (isDemoMode()) return DEMO_SMARTLOCK_ID
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
        private const val DEMO_SMARTLOCK_ID = 0L
        private val LOCK_RESULT = setOf(LockState.Locked)
        // After an unlatch the lock briefly reports Unlatched, then settles on Unlocked
        private val OPEN_RESULT = setOf(LockState.Unlatched, LockState.Unlocked)
        private val STOP_STATES = setOf(LockState.MotorBlocked, LockState.Uncalibrated, LockState.Undefined)
    }
}
