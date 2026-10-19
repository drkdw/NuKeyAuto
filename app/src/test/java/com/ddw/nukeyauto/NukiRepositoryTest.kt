package com.ddw.nukeyauto

import android.content.SharedPreferences
import com.ddw.nukeyauto.data.PrefKeys
import com.ddw.nukeyauto.data.api.NukiApiService
import com.ddw.nukeyauto.data.model.ActionRequest
import com.ddw.nukeyauto.data.model.LockState
import com.ddw.nukeyauto.data.model.LockStateResponse
import com.ddw.nukeyauto.data.model.NukiError
import com.ddw.nukeyauto.data.model.SmartLockResponse
import com.ddw.nukeyauto.data.repo.NukiRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class NukiRepositoryTest {

    private lateinit var api: NukiApiService
    private lateinit var prefs: SharedPreferences
    private lateinit var repository: NukiRepository

    /** Helper: mock both prefs keys (nuki_id and nuki_token) at once. */
    private fun mockCredentials(id: String = "123456789", token: String = "test-token") {
        whenever(prefs.getString(PrefKeys.NUKI_ID, "")).thenReturn(id)
        whenever(prefs.getString(PrefKeys.NUKI_TOKEN, "")).thenReturn(token)
    }

    /** Helper: create a standard SmartLockResponse. */
    private fun mockResponse(state: Int, battery: Int = 70) = SmartLockResponse(
        state = LockStateResponse(state = state, batteryCharge = battery)
    )

    @Before
    fun setup() {
        api = mock()
        prefs = mock()
        repository = NukiRepository(api, prefs)
    }

    // --- fetchState tests ---

    @Test
    fun `fetchState without credentials sets NoCredentials error`() = runTest {
        whenever(prefs.getString(PrefKeys.NUKI_ID, "")).thenReturn("")
        whenever(prefs.getString(PrefKeys.NUKI_TOKEN, "")).thenReturn("")

        repository.fetchState()

        assertTrue(repository.uiState.value.error is NukiError.NoCredentials)
        assertEquals(false, repository.uiState.value.isLoading)
    }

    @Test
    fun `fetchState without token sets NoCredentials error`() = runTest {
        whenever(prefs.getString(PrefKeys.NUKI_ID, "")).thenReturn("123456789")
        whenever(prefs.getString(PrefKeys.NUKI_TOKEN, "")).thenReturn("")

        repository.fetchState()

        assertTrue(repository.uiState.value.error is NukiError.NoCredentials)
    }

    @Test
    fun `fetchState with invalid ID sets InvalidSmartLockId error`() = runTest {
        mockCredentials(id = "not-a-number", token = "test-token")

        repository.fetchState()

        assertTrue(repository.uiState.value.error is NukiError.InvalidSmartLockId)
        assertEquals(false, repository.uiState.value.isLoading)
    }

    @Test
    fun `fetchState with valid ID and locked state sets LockState Locked`() = runTest {
        mockCredentials()
        whenever(api.getSmartLockState(123456789L)).thenReturn(mockResponse(state = 1, battery = 85))

        repository.fetchState()

        val state = repository.uiState.value
        assertEquals(LockState.Locked, state.lockState)
        assertEquals(85, state.battery)
        assertNull(state.error)
        assertEquals(false, state.isLoading)
    }

    @Test
    fun `fetchState with state 3 sets LockState Unlocked`() = runTest {
        mockCredentials()
        whenever(api.getSmartLockState(123456789L)).thenReturn(mockResponse(state = 3, battery = 42))

        repository.fetchState()

        assertEquals(LockState.Unlocked, repository.uiState.value.lockState)
        assertEquals(42, repository.uiState.value.battery)
    }

    @Test
    fun `fetchState with state 5 sets LockState Unlatched`() = runTest {
        mockCredentials()
        whenever(api.getSmartLockState(123456789L)).thenReturn(mockResponse(state = 5))

        repository.fetchState()

        assertEquals(LockState.Unlatched, repository.uiState.value.lockState)
    }

    @Test
    fun `fetchState with state 6 maps lock n go to Unlocked`() = runTest {
        mockCredentials()
        whenever(api.getSmartLockState(123456789L)).thenReturn(mockResponse(state = 6, battery = 55))

        repository.fetchState()

        val state = repository.uiState.value
        assertEquals(LockState.Unlocked, state.lockState)
        assertEquals(55, state.battery)
        assertNull(state.error)
    }

    @Test
    fun `fetchState with state 253 sets LockState BootRun without error`() = runTest {
        mockCredentials()
        whenever(api.getSmartLockState(123456789L)).thenReturn(mockResponse(state = 253))

        repository.fetchState()

        val state = repository.uiState.value
        assertEquals(LockState.BootRun, state.lockState)
        assertNull(state.error)
    }

    @Test
    fun `fetchState with state 254 sets LockState MotorBlocked`() = runTest {
        mockCredentials()
        whenever(api.getSmartLockState(123456789L)).thenReturn(mockResponse(state = 254, battery = 68))

        repository.fetchState()

        val state = repository.uiState.value
        assertEquals(LockState.MotorBlocked, state.lockState)
        assertEquals(68, state.battery)
        assertNull(state.error)
    }

    @Test
    fun `fetchState with unknown state sets UnknownLockState error`() = runTest {
        mockCredentials()
        whenever(api.getSmartLockState(123456789L)).thenReturn(mockResponse(state = 99))

        repository.fetchState()

        val state = repository.uiState.value
        assertEquals(LockState.Unknown, state.lockState)
        val error = state.error as NukiError.UnknownLockState
        assertEquals(99, error.stateCode)
    }

    @Test
    fun `fetchState on API exception sets ApiException error`() = runTest {
        mockCredentials()
        whenever(api.getSmartLockState(any())).thenThrow(RuntimeException("Network timeout"))

        repository.fetchState()

        val state = repository.uiState.value
        assertEquals(false, state.isLoading)
        assertTrue(state.error is NukiError.ApiException)
    }

    @Test
    fun `fetchState on HttpException sets HttpError with status code`() = runTest {
        mockCredentials()
        val response = Response.error<SmartLockResponse>(401, "Unauthorized".toResponseBody())
        whenever(api.getSmartLockState(any())).thenThrow(HttpException(response))

        repository.fetchState()

        val state = repository.uiState.value
        assertEquals(false, state.isLoading)
        assertTrue(state.error is NukiError.HttpError)
        assertEquals(401, (state.error as NukiError.HttpError).code)
    }

    @Test
    fun `fetchState on HttpException 500 sets HttpError with server code`() = runTest {
        mockCredentials()
        val response = Response.error<SmartLockResponse>(500, "Server Error".toResponseBody())
        whenever(api.getSmartLockState(any())).thenThrow(HttpException(response))

        repository.fetchState()

        val state = repository.uiState.value
        assertTrue(state.error is NukiError.HttpError)
        assertEquals(500, (state.error as NukiError.HttpError).code)
    }

    @Test
    fun `fetchState on IOException sets ConnectionFailed error`() = runTest {
        mockCredentials()
        whenever(api.getSmartLockState(any())).thenAnswer { throw java.io.IOException("Socket timeout") }

        repository.fetchState()

        val state = repository.uiState.value
        assertEquals(false, state.isLoading)
        assertTrue(state.error is NukiError.ConnectionFailed)
    }

    @Test
    fun `fetchState on prefs exception sets ApiException error`() = runTest {
        whenever(prefs.getString(PrefKeys.NUKI_ID, "")).thenThrow(SecurityException("KeyStore corrupted"))

        repository.fetchState()

        val state = repository.uiState.value
        assertEquals(false, state.isLoading)
        assertTrue(state.error is NukiError.ApiException)
    }

    // --- performLockAction tests ---

    @Test
    fun `performLockAction with shouldLock=false sends action 3 (unlatch)`() = runTest {
        mockCredentials()
        whenever(api.getSmartLockState(123456789L)).thenReturn(mockResponse(state = 3))

        repository.performLockAction(shouldLock = false)

        verify(api).performAction(123456789L, ActionRequest(3))
    }

    @Test
    fun `performLockAction with shouldLock=true sends action 2 (lock)`() = runTest {
        mockCredentials()
        whenever(api.getSmartLockState(123456789L)).thenReturn(mockResponse(state = 1))

        repository.performLockAction(shouldLock = true)

        verify(api).performAction(123456789L, ActionRequest(2))
    }

    @Test
    fun `performLockAction without credentials does nothing`() = runTest {
        whenever(prefs.getString(PrefKeys.NUKI_ID, "")).thenReturn("")
        whenever(prefs.getString(PrefKeys.NUKI_TOKEN, "")).thenReturn("")

        repository.performLockAction(shouldLock = true)

        verify(api, never()).performAction(any(), any())
    }

    @Test
    fun `performLockAction with invalid ID does nothing`() = runTest {
        mockCredentials(id = "abc", token = "test-token")

        repository.performLockAction(shouldLock = true)

        verify(api, never()).performAction(any(), any())
    }

    @Test
    fun `performLockAction on RuntimeException sets ApiException error`() = runTest {
        mockCredentials()
        whenever(api.performAction(any(), any())).thenThrow(RuntimeException("Unexpected error"))

        repository.performLockAction(shouldLock = false)

        val state = repository.uiState.value
        assertEquals(false, state.isLoading)
        assertTrue(state.error is NukiError.ApiException)
    }

    @Test
    fun `performLockAction on IOException sets ConnectionFailed error`() = runTest {
        mockCredentials()
        whenever(api.performAction(any(), any())).thenAnswer { throw java.io.IOException("Connection refused") }

        repository.performLockAction(shouldLock = false)

        val state = repository.uiState.value
        assertEquals(false, state.isLoading)
        assertTrue(state.error is NukiError.ConnectionFailed)
    }

    @Test
    fun `performLockAction counts down before sending the action`() = runTest {
        mockCredentials()
        whenever(prefs.getInt(PrefKeys.OPEN_DELAY_SECONDS, 0)).thenReturn(2)
        whenever(api.getSmartLockState(123456789L)).thenReturn(mockResponse(state = 3))

        val countdowns = mutableListOf<Int>()
        backgroundScope.launch {
            repository.uiState.collect { countdowns.add(it.countdownSeconds) }
        }

        repository.performLockAction(shouldLock = false)

        verify(api).performAction(123456789L, ActionRequest(3))
        assertTrue(countdowns.contains(2))
        assertEquals(0, repository.uiState.value.countdownSeconds)
    }

    @Test
    fun `performLockAction clears a previous error before counting down`() = runTest {
        mockCredentials()
        whenever(prefs.getInt(PrefKeys.OPEN_DELAY_SECONDS, 0)).thenReturn(3)
        // First call fails (leaves an error in the state), every later call succeeds
        whenever(api.getSmartLockState(123456789L))
            .thenAnswer { throw java.io.IOException("Connection refused") }
            .thenReturn(mockResponse(state = 3))

        repository.fetchState()
        assertTrue(repository.uiState.value.error is NukiError.ConnectionFailed)

        // Every countdown tick emits a new state — none of them may carry the old error,
        // otherwise the UI shows one Snackbar/CarToast per second.
        val errorsDuringCountdown = mutableListOf<NukiError?>()
        backgroundScope.launch {
            repository.uiState.collect {
                if (it.countdownSeconds > 0) errorsDuringCountdown.add(it.error)
            }
        }

        repository.performLockAction(shouldLock = false)

        assertTrue(errorsDuringCountdown.isNotEmpty())
        assertTrue(errorsDuringCountdown.all { it == null })
    }

    @Test
    fun `cancelOpen during the countdown never sends the action`() = runTest {
        mockCredentials()
        whenever(prefs.getInt(PrefKeys.OPEN_DELAY_SECONDS, 0)).thenReturn(5)

        val job = launch { repository.performLockAction(shouldLock = false) }
        testScheduler.advanceTimeBy(1500)
        repository.cancelOpen()
        job.join()

        verify(api, never()).performAction(any(), any())
        assertEquals(0, repository.uiState.value.countdownSeconds)
        assertEquals(false, repository.uiState.value.isLoading)
        assertNull(repository.uiState.value.error)
    }

    @Test
    fun `a refresh during the countdown keeps the loading state`() = runTest {
        mockCredentials()
        whenever(prefs.getInt(PrefKeys.OPEN_DELAY_SECONDS, 0)).thenReturn(5)
        whenever(api.getSmartLockState(123456789L)).thenReturn(mockResponse(state = 3))

        val job = launch { repository.performLockAction(shouldLock = false) }
        testScheduler.advanceTimeBy(1500)
        repository.fetchState()

        // Otherwise the phone re-enables "Lock door" while the open is still pending
        assertTrue(repository.uiState.value.countdownSeconds > 0)
        assertEquals(true, repository.uiState.value.isLoading)

        repository.cancelOpen()
        job.join()
        assertEquals(false, repository.uiState.value.isLoading)
    }

    @Test
    fun `fetchState keeps battery null when the lock only reports batteryCritical`() = runTest {
        mockCredentials()
        whenever(api.getSmartLockState(123456789L)).thenReturn(
            SmartLockResponse(LockStateResponse(state = 1, batteryCharge = null, batteryCritical = true))
        )

        repository.fetchState()

        assertNull(repository.uiState.value.battery)
        assertEquals(true, repository.uiState.value.batteryCritical)
    }

    @Test
    fun `cancelling the scope mid-countdown clears the countdown without an error`() = runTest {
        mockCredentials()
        whenever(prefs.getInt(PrefKeys.OPEN_DELAY_SECONDS, 0)).thenReturn(5)

        val job = launch { repository.performLockAction(shouldLock = false) }
        testScheduler.advanceTimeBy(1500)
        job.cancel()
        job.join()

        verify(api, never()).performAction(any(), any())
        assertEquals(0, repository.uiState.value.countdownSeconds)
        assertNull(repository.uiState.value.error)
    }

    @Test
    fun `performLockAction keeps polling while the lock is still moving`() = runTest {
        mockCredentials()
        // Lock never leaves the "locking" transition state
        whenever(api.getSmartLockState(123456789L)).thenReturn(mockResponse(state = 4))

        repository.performLockAction(shouldLock = true)

        // One fetch after the movement delay + MAX_POLL_ATTEMPTS follow-up polls
        verify(api, times(6)).getSmartLockState(123456789L)
    }

    @Test
    fun `performLockAction stops polling once the lock settles`() = runTest {
        mockCredentials()
        whenever(api.getSmartLockState(123456789L)).thenReturn(mockResponse(state = 1))

        repository.performLockAction(shouldLock = true)

        verify(api, times(1)).getSmartLockState(123456789L)
        assertEquals(LockState.Locked, repository.uiState.value.lockState)
    }

    // --- saveCredentials tests ---

    @Test
    fun `saveCredentials stores token and id in prefs`() {
        val editor: SharedPreferences.Editor = mock()
        whenever(prefs.edit()).thenReturn(editor)
        whenever(editor.putString(any(), any())).thenReturn(editor)

        repository.saveCredentials("my-token-123", "987654321")

        verify(editor).putString(PrefKeys.NUKI_TOKEN, "my-token-123")
        verify(editor).putString(PrefKeys.NUKI_ID, "987654321")
        verify(editor).apply()
    }

    // --- getOpenDelaySeconds tests ---

    @Test
    fun `getOpenDelaySeconds returns stored value`() {
        whenever(prefs.getInt(PrefKeys.OPEN_DELAY_SECONDS, 0)).thenReturn(5)

        assertEquals(5, repository.getOpenDelaySeconds())
    }

    @Test
    fun `getOpenDelaySeconds returns 0 when not set`() {
        whenever(prefs.getInt(PrefKeys.OPEN_DELAY_SECONDS, 0)).thenReturn(0)

        assertEquals(0, repository.getOpenDelaySeconds())
    }

    @Test
    fun `getOpenDelaySeconds returns 0 on prefs exception`() {
        whenever(prefs.getInt(PrefKeys.OPEN_DELAY_SECONDS, 0)).thenThrow(SecurityException("KeyStore failure"))

        assertEquals(0, repository.getOpenDelaySeconds())
    }

    // --- isConfirmationEnabled tests ---

    @Test
    fun `isConfirmationEnabled returns true by default`() {
        whenever(prefs.getBoolean(PrefKeys.CONFIRM_ACTION, true)).thenReturn(true)

        assertTrue(repository.isConfirmationEnabled())
    }

    @Test
    fun `isConfirmationEnabled returns false when disabled`() {
        whenever(prefs.getBoolean(PrefKeys.CONFIRM_ACTION, true)).thenReturn(false)

        assertTrue(!repository.isConfirmationEnabled())
    }

    @Test
    fun `isConfirmationEnabled returns true on prefs exception`() {
        whenever(prefs.getBoolean(PrefKeys.CONFIRM_ACTION, true)).thenThrow(SecurityException("KeyStore failure"))

        assertTrue(repository.isConfirmationEnabled())
    }
}
