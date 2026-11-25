package com.ddw.nukeyauto.ui.auto

import android.app.Application
import android.content.SharedPreferences
import android.os.Looper
import androidx.car.app.OnDoneCallback
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestCarContext
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.ddw.nukeyauto.data.PrefKeys
import com.ddw.nukeyauto.data.api.NukiApiService
import com.ddw.nukeyauto.data.demo.DemoLock
import com.ddw.nukeyauto.data.model.LockStateResponse
import com.ddw.nukeyauto.data.model.SmartLockResponse
import com.ddw.nukeyauto.data.repo.NukiRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Android Auto screen: what the car head unit is asked to draw, without a car or emulator. */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the real one starts Hilt and the Android Keystore
@Config(application = Application::class, sdk = [35])
class DoorControlScreenTest {

    private lateinit var api: NukiApiService
    private lateinit var prefs: SharedPreferences
    private lateinit var carContext: TestCarContext

    @Before
    fun setup() {
        api = mock()
        prefs = mock()
        whenever(prefs.getString(PrefKeys.NUKI_ID, "")).thenReturn("123")
        whenever(prefs.getString(PrefKeys.NUKI_TOKEN, "")).thenReturn("test-token")
        runBlocking {
            whenever(api.getSmartLockState(any())).thenReturn(SmartLockResponse(LockStateResponse(state = 1, batteryCharge = 70)))
        }
        carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
    }

    private fun startScreen(): Pair<DoorControlScreen, ScreenController> {
        val screen = DoorControlScreen(carContext, NukiRepository(api, prefs, DemoLock()))
        val controller = ScreenController(screen)
        controller.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()
        return screen to controller
    }

    private fun DoorControlScreen.pane(): Pane = (onGetTemplate() as PaneTemplate).pane

    private fun Pane.actionTitles() = actions.map { it.title.toString() }

    @Test
    fun `locked lock shows status, battery and an Open door action`() {
        val (screen, _) = startScreen()

        val pane = screen.pane()
        assertEquals(false, pane.isLoading)
        assertEquals(2, pane.rows.size)
        assertEquals(listOf("Open door"), pane.actionTitles())
    }

    @Test
    fun `screen refreshes every time it becomes visible again`() = runBlocking<Unit> {
        val (_, controller) = startScreen()
        verify(api, times(1)).getSmartLockState(123L)

        controller.moveToState(Lifecycle.State.CREATED) // car app sent to the background
        controller.moveToState(Lifecycle.State.RESUMED)
        shadowOf(Looper.getMainLooper()).idle()

        verify(api, times(2)).getSmartLockState(123L)
    }

    @Test
    fun `with confirmation on, the first tap only asks for confirmation`() = runBlocking<Unit> {
        whenever(prefs.getBoolean(PrefKeys.CONFIRM_ACTION, true)).thenReturn(true)
        val (screen, _) = startScreen()

        screen.pane().actions.first().onClickDelegate!!.sendClick(mock<OnDoneCallback>())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("Open door?"), screen.pane().actionTitles().take(1))
        verify(api, times(0)).performAction(any(), any())
    }

    @Test
    fun `without a door sensor the status row has no door line`() {
        val (screen, _) = startScreen()
        val pane = screen.pane()
        assertEquals(2, pane.rows.size)
        assertEquals(1, pane.rows[0].texts.size)
    }

    @Test
    fun `an open door says so in the status row and hides Open and Lock`() = runBlocking<Unit> {
        whenever(api.getSmartLockState(any()))
            .thenReturn(SmartLockResponse(LockStateResponse(state = 3, batteryCharge = 70, doorState = 3)))
        val (screen, _) = startScreen()

        val pane = screen.pane()
        // Two rows only: a third would need scrolling on a small car screen
        assertEquals(2, pane.rows.size)
        assertEquals("Close the door to lock", pane.rows[0].texts[1].toString())
        assertTrue(pane.actions.isEmpty())
    }

    @Test
    fun `a closed door keeps the normal actions`() = runBlocking<Unit> {
        whenever(api.getSmartLockState(any()))
            .thenReturn(SmartLockResponse(LockStateResponse(state = 3, batteryCharge = 70, doorState = 2)))
        val (screen, _) = startScreen()

        assertEquals(listOf("Open door", "Lock door"), screen.pane().actionTitles())
    }

    @Test
    fun `demo mode is named in the car title and works without credentials`() {
        whenever(prefs.getBoolean(PrefKeys.DEMO_MODE, false)).thenReturn(true)
        whenever(prefs.getString(PrefKeys.NUKI_TOKEN, "")).thenReturn("")
        val (screen, _) = startScreen()

        val template = screen.onGetTemplate() as PaneTemplate
        assertEquals("NuKey Auto demo", template.header!!.title.toString())
        assertTrue(template.pane.isLoading || template.pane.rows.size == 2)
    }
}
