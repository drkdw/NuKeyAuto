package com.ddw.nukeyauto.ui.auto

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ddw.nukeyauto.R
import com.ddw.nukeyauto.data.model.DoorState
import com.ddw.nukeyauto.data.model.LockState
import com.ddw.nukeyauto.data.model.doorLabelRes
import com.ddw.nukeyauto.data.model.iconRes
import com.ddw.nukeyauto.data.model.isFault
import com.ddw.nukeyauto.data.model.isTransition
import com.ddw.nukeyauto.data.model.statusRes
import com.ddw.nukeyauto.data.repo.NukiRepository
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Android Auto screen showing lock status and action buttons. */
class DoorControlScreen(
    carContext: CarContext,
    private val repository: NukiRepository
) : Screen(carContext) {

    /**
     * Tracks which action awaits confirmation (two-tap pattern).
     * null = no confirmation pending.
     */
    private var pendingAction: PendingAction? = null

    private enum class PendingAction { OPEN, LOCK }

    init {
        lifecycleScope.launch {
            var lastLockState = repository.uiState.value.lockState
            var lastShownError = repository.uiState.value.error

            repository.uiState.collect { state ->
                // Only drop a pending confirmation when the lock itself changed,
                // not on every emission (loading flags, countdown ticks).
                if (state.lockState != lastLockState) {
                    pendingAction = null
                    lastLockState = state.lockState
                }

                // Show toast when error occurs but UI still shows a valid lock state, once per error
                val isNewError = state.error != lastShownError
                lastShownError = state.error
                if (isNewError && state.error != null && state.lockState != LockState.Unknown) {
                    CarToast.makeText(
                        carContext,
                        state.error.toLocalizedString(carContext),
                        CarToast.LENGTH_LONG
                    ).show()
                }

                invalidate()
            }
        }

        // Refresh every time the screen becomes visible again, not only once: the lock can be
        // operated elsewhere (Nuki app, key) while the car session stays alive in the background.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = refresh()
        })

        // Door left open: watch it while the car screen is visible
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.uiState.map { it.doorState == DoorState.Open }.distinctUntilChanged()
                    .collectLatest { open -> if (open) repository.refreshWhileDoorOpen() }
            }
        }
    }

    // Launched in the Screen's own lifecycleScope: it is cancelled when the car session
    // ends, so a running countdown or poll loop never outlives the screen.
    private fun refresh() {
        lifecycleScope.launch { repository.fetchState() }
    }

    private fun performLockAction(shouldLock: Boolean) {
        lifecycleScope.launch { repository.performLockAction(shouldLock) }
    }

    override fun onGetTemplate(): Template {
        val state = repository.uiState.value

        val header = Header.Builder()
            .setStartHeaderAction(Action.APP_ICON)
            .setTitle(carContext.getString(
                if (repository.isDemoMode()) R.string.auto_title_demo else R.string.app_name
            ))
            .addEndHeaderAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(carContext, R.drawable.ic_refresh)
                        ).setTint(CarColor.DEFAULT).build()
                    )
                    .setOnClickListener {
                        pendingAction = null
                        refresh()
                    }
                    .build()
            )
            .build()

        // Empty pane only while nothing is known yet (Car App Library forbids rows+actions with
        // setLoading). During an action the rows stay, so the driver sees "Opening…".
        if (state.isLoading && state.countdownSeconds == 0 && state.lockState == LockState.Unknown) {
            return PaneTemplate.Builder(
                Pane.Builder().setLoading(true).build()
            ).setHeader(header).build()
        }

        // --- Row 1: Lock status ---
        val statusRowIcon: CarIcon
        val statusTitle: String
        val statusSubtitle: String

        if (state.countdownSeconds > 0) {
            statusRowIcon = CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_hourglass)
            ).setTint(CarColor.DEFAULT).build()
            statusTitle = carContext.getString(R.string.auto_row_title)
            statusSubtitle = carContext.getString(R.string.status_countdown, state.countdownSeconds)
        } else {
            statusRowIcon = if (state.lockedButDoorOpen) {
                CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_warning))
                    .setTint(CarColor.YELLOW).build()
            } else {
                lockStateToCarIcon(state.lockState)
            }
            statusTitle = carContext.getString(R.string.auto_row_title)
            statusSubtitle = if (state.lockState == LockState.Unknown && state.error != null) {
                state.error.toLocalizedString(carContext)
            } else if (state.lockedButDoorOpen) {
                carContext.getString(R.string.door_open_while_locked)
            } else {
                carContext.getString(state.lockState.statusRes)
            }
        }

        val statusRow = Row.Builder()
            .setTitle(statusTitle)
            .addText(statusSubtitle)
            .apply {
                // Quiet battery line under the lock status; a low battery says so
                if (state.countdownSeconds == 0 && state.lockState != LockState.Unknown) {
                    val battery = state.battery
                    val low = state.batteryCritical || (battery != null && battery < 20)
                    when {
                        low -> addText(carContext.getString(R.string.auto_battery_low))
                        battery != null -> addText(carContext.getString(R.string.battery_label, battery))
                    }
                }
            }
            .setImage(statusRowIcon)
            .build()


        // --- Large image on the right side (padded pane versions) ---
        val paneImage = if (state.lockedButDoorOpen) statusRowIcon else lockStateToCarIcon(state.lockState)

        val confirmationEnabled = repository.isConfirmationEnabled()

        // --- Build pane ---
        val paneBuilder = Pane.Builder()
            .addRow(statusRow)
            .apply {
                // Door row only when the sensor is sure (closed or open): two rows with two
                // lines each still fit a small car screen without scrolling
                state.doorState.doorLabelRes?.let { labelRes ->
                    val open = state.doorState == DoorState.Open
                    val doorRow = Row.Builder()
                        .setTitle(carContext.getString(R.string.door_title))
                        .addText(carContext.getString(labelRes).replaceFirstChar { it.uppercase() })
                        .setImage(
                            CarIcon.Builder(IconCompat.createWithResource(
                                carContext, if (open) R.drawable.ic_door_open else R.drawable.ic_door_closed
                            )).setTint(if (state.lockedButDoorOpen) CarColor.YELLOW else CarColor.DEFAULT).build()
                        )
                    when {
                        open -> doorRow.addText(carContext.getString(R.string.door_close_first))
                        state.doorSensorBatteryCritical ->
                            doorRow.addText(carContext.getString(R.string.door_sensor_battery_low))
                    }
                    addRow(doorRow.build())
                }
            }
            .setImage(paneImage)


        // --- Action buttons (hidden during transitions; only Cancel during the countdown) ---
        if (state.countdownSeconds > 0) {
            paneBuilder.addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.btn_cancel))
                    .setOnClickListener { repository.cancelOpen() }
                    .build()
            )
        } else if (!state.lockState.isTransition && !state.isLoading &&
            (state.doorState != DoorState.Open || state.lockState in RETRY_LOCK_STATES)
        ) {
            // An open door hides Open/Lock: the door row says to close it first
            when (state.lockState) {
                LockState.Unlocked -> {
                    paneBuilder.addAction(buildAction(
                        label = R.string.btn_open_door,
                        confirmLabel = R.string.confirm_open_title,
                        pendingType = PendingAction.OPEN,
                        confirmationEnabled = confirmationEnabled,
                        execute = { performLockAction(false) }
                    ))
                    paneBuilder.addAction(buildAction(
                        label = R.string.btn_lock_door,
                        confirmLabel = R.string.confirm_lock_title,
                        pendingType = PendingAction.LOCK,
                        confirmationEnabled = confirmationEnabled,
                        execute = { performLockAction(true) }
                    ))
                }
                LockState.Unlatched -> {
                    paneBuilder.addAction(buildAction(
                        label = R.string.btn_lock_door,
                        confirmLabel = R.string.confirm_lock_title,
                        pendingType = PendingAction.LOCK,
                        confirmationEnabled = confirmationEnabled,
                        execute = { performLockAction(true) }
                    ))
                }
                // Config errors (no token, 401) can only be fixed on the phone: the status row
                // already says so, and Retry or Open would just fail again.
                LockState.Unknown -> if (state.error?.isConfigError() != true) {
                    paneBuilder.addAction(retryAction())
                }
                LockState.Uncalibrated, LockState.Undefined -> paneBuilder.addAction(retryAction())
                else -> {
                    paneBuilder.addAction(buildAction(
                        label = R.string.btn_open_door,
                        confirmLabel = R.string.confirm_open_title,
                        pendingType = PendingAction.OPEN,
                        confirmationEnabled = confirmationEnabled,
                        execute = { performLockAction(false) }
                    ))
                }
            }
        }

        return PaneTemplate.Builder(paneBuilder.build())
            .setHeader(header)
            .build()
    }

    private fun retryAction(): Action = Action.Builder()
        .setTitle(carContext.getString(R.string.btn_retry))
        .setOnClickListener { refresh() }
        .build()

    /**
     * Build an action with optional two-tap confirmation.
     * First tap changes button text to confirm prompt, second tap executes.
     */
    private fun buildAction(
        label: Int,
        confirmLabel: Int,
        pendingType: PendingAction,
        confirmationEnabled: Boolean,
        execute: () -> Unit
    ): Action {
        val isConfirming = pendingAction == pendingType

        return if (isConfirming) {
            Action.Builder()
                .setTitle(carContext.getString(confirmLabel))

                .setOnClickListener {
                    pendingAction = null
                    execute()
                }
                .build()
        } else if (confirmationEnabled) {
            Action.Builder()
                .setTitle(carContext.getString(label))
                .setOnClickListener {
                    pendingAction = pendingType
                    invalidate()
                }
                .build()
        } else {
            Action.Builder()
                .setTitle(carContext.getString(label))
                .setOnClickListener { execute() }
                .build()
        }
    }

    /**
     * Map a [LockState] to the corresponding [CarIcon] for display in rows and pane image.
     * Transition states and errors use warning icons; stable states use the matching lock icon.
     */
    private fun lockStateToCarIcon(lockState: LockState): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, lockState.iconRes))
            .setTint(if (lockState.isFault) CarColor.YELLOW else CarColor.DEFAULT)
            .build()
}

/** Lock states that offer Retry; they stay available whatever the door does. */
private val RETRY_LOCK_STATES = setOf(LockState.Uncalibrated, LockState.Undefined, LockState.Unknown)
