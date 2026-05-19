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
import androidx.lifecycle.lifecycleScope
import com.ddw.nukeyauto.R
import com.ddw.nukeyauto.data.model.LockState
import com.ddw.nukeyauto.data.repo.NukiRepository
import kotlinx.coroutines.launch

/** Android Auto screen showing lock status and action buttons. */
class DoorControlScreen(
    carContext: CarContext,
    private val repository: NukiRepository
) : Screen(carContext) {

    private val viewModel = MainViewModel(repository)

    /**
     * Tracks which action awaits confirmation (two-tap pattern).
     * null = no confirmation pending.
     */
    private var pendingAction: PendingAction? = null

    private enum class PendingAction { OPEN, LOCK, CLOSE }

    init {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                // Reset confirmation state when lock state changes
                pendingAction = null

                // Show toast when error occurs but UI still shows a valid lock state
                if (state.error != null && state.lockState != LockState.Unknown) {
                    CarToast.makeText(
                        carContext,
                        state.error.toLocalizedString(carContext),
                        CarToast.LENGTH_LONG
                    ).show()
                }

                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val state = viewModel.uiState.value

        val header = Header.Builder()
            .setStartHeaderAction(Action.APP_ICON)
            .setTitle(carContext.getString(R.string.app_name))
            .addEndHeaderAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(carContext, R.drawable.ic_refresh)
                        ).build()
                    )
                    .setOnClickListener {
                        pendingAction = null
                        viewModel.refresh()
                    }
                    .build()
            )
            .build()

        // Empty pane during loading — Car App Library forbids rows+actions with setLoading(true)
        if (state.isLoading && state.countdownSeconds == 0) {
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
            ).setTint(CarColor.PRIMARY).build()
            statusTitle = carContext.getString(R.string.auto_row_title)
            statusSubtitle = carContext.getString(R.string.status_countdown, state.countdownSeconds)
        } else {
            statusRowIcon = lockStateToCarIcon(state.lockState)
            statusTitle = carContext.getString(R.string.auto_row_title)
            statusSubtitle = when (state.lockState) {
                LockState.Locked -> carContext.getString(R.string.status_locked)
                LockState.Locking -> carContext.getString(R.string.status_locking)
                LockState.Unlocked -> carContext.getString(R.string.status_unlocked)
                LockState.Unlocking -> carContext.getString(R.string.status_unlocking)
                LockState.Unlatched -> carContext.getString(R.string.status_unlatched)
                LockState.Unlatching -> carContext.getString(R.string.status_unlatching)
                LockState.MotorBlocked -> carContext.getString(R.string.status_motor_blocked)
                LockState.Uncalibrated -> carContext.getString(R.string.status_uncalibrated)
                LockState.Undefined -> carContext.getString(R.string.status_undefined)
                LockState.Unknown -> state.error?.toLocalizedString(carContext)
                    ?: carContext.getString(R.string.status_unknown)
            }
        }

        val statusRow = Row.Builder()
            .setTitle(statusTitle)
            .addText(statusSubtitle)
            .setImage(statusRowIcon)
            .build()

        // --- Row 2: Battery (icon matches level) ---
        val batteryIconRes = when {
            state.battery < 15 -> R.drawable.ic_battery_0
            state.battery < 30 -> R.drawable.ic_battery_1
            state.battery < 45 -> R.drawable.ic_battery_2
            state.battery < 55 -> R.drawable.ic_battery_3
            state.battery < 70 -> R.drawable.ic_battery_4
            state.battery < 85 -> R.drawable.ic_battery_5
            state.battery < 95 -> R.drawable.ic_battery_6
            else -> R.drawable.ic_battery
        }
        val batteryIcon = CarIcon.Builder(
            IconCompat.createWithResource(carContext, batteryIconRes)
        ).setTint(
            if (state.battery < 20) CarColor.YELLOW else CarColor.PRIMARY
        ).build()

        val batteryTitle = carContext.getString(R.string.battery_label, state.battery)
        val batterySubtitle = when {
            state.battery <= 0 -> carContext.getString(R.string.auto_battery_unknown)
            state.battery < 20 -> carContext.getString(R.string.auto_battery_low)
            state.battery < 50 -> carContext.getString(R.string.auto_battery_medium)
            else -> carContext.getString(R.string.auto_battery_good)
        }

        val batteryRow = Row.Builder()
            .setTitle(batteryTitle)
            .addText(batterySubtitle)
            .setImage(batteryIcon)
            .build()

        // --- Large image on the right side (padded pane versions) ---
        val paneImage = lockStateToCarIcon(state.lockState)

        // --- Build pane ---
        val paneBuilder = Pane.Builder()
            .addRow(statusRow)
            .addRow(batteryRow)
            .setImage(paneImage)

        // --- Action buttons (hidden during countdown and transitions) ---
        val isInTransition = state.lockState in listOf(
            LockState.Locking, LockState.Unlocking, LockState.Unlatching
        )

        if (!isInTransition && state.countdownSeconds == 0) {
            when (state.lockState) {
                LockState.Unlocked -> {
                    paneBuilder.addAction(buildAction(
                        label = R.string.btn_open_door,
                        confirmLabel = R.string.confirm_open_title,
                        pendingType = PendingAction.OPEN,
                        execute = { viewModel.openDoor() }
                    ))
                    paneBuilder.addAction(buildAction(
                        label = R.string.btn_lock_door,
                        confirmLabel = R.string.confirm_lock_title,
                        pendingType = PendingAction.LOCK,
                        execute = { viewModel.lockDoor() }
                    ))
                }
                LockState.Unlatched -> {
                    paneBuilder.addAction(buildAction(
                        label = R.string.btn_close_door,
                        confirmLabel = R.string.confirm_close_title,
                        pendingType = PendingAction.CLOSE,
                        execute = { viewModel.lockDoor() }
                    ))
                }
                LockState.Unknown -> {
                    if (state.error != null && !state.error.isConfigError()) {
                        paneBuilder.addAction(
                            Action.Builder()
                                .setTitle(carContext.getString(R.string.btn_retry))
                                .setOnClickListener { viewModel.refresh() }
                                .build()
                        )
                    } else {
                        paneBuilder.addAction(buildAction(
                            label = R.string.btn_open_door,
                            confirmLabel = R.string.confirm_open_title,
                            pendingType = PendingAction.OPEN,
                            execute = { viewModel.openDoor() }
                        ))
                    }
                }
                else -> {
                    paneBuilder.addAction(buildAction(
                        label = R.string.btn_open_door,
                        confirmLabel = R.string.confirm_open_title,
                        pendingType = PendingAction.OPEN,
                        execute = { viewModel.openDoor() }
                    ))
                }
            }
        }

        return PaneTemplate.Builder(paneBuilder.build())
            .setHeader(header)
            .build()
    }

    /**
     * Build an action with optional two-tap confirmation.
     * First tap changes button text to confirm prompt, second tap executes.
     */
    private fun buildAction(
        label: Int,
        confirmLabel: Int,
        pendingType: PendingAction,
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
        } else if (viewModel.isConfirmationEnabled()) {
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
    private fun lockStateToCarIcon(lockState: LockState): CarIcon = when (lockState) {
        LockState.Locked -> CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_lock)
        ).setTint(CarColor.PRIMARY).build()
        LockState.Unlocked -> CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_lock_open)
        ).setTint(CarColor.PRIMARY).build()
        LockState.Unlatched -> CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_door_open)
        ).setTint(CarColor.PRIMARY).build()
        LockState.Locking, LockState.Unlocking, LockState.Unlatching -> CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_hourglass)
        ).setTint(CarColor.PRIMARY).build()
        LockState.MotorBlocked, LockState.Uncalibrated -> CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_warning)
        ).setTint(CarColor.YELLOW).build()
        LockState.Undefined -> CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_cloud_off)
        ).setTint(CarColor.YELLOW).build()
        LockState.Unknown -> CarIcon.Builder(
            IconCompat.createWithResource(carContext, R.drawable.ic_warning)
        ).setTint(CarColor.YELLOW).build()
    }
}
