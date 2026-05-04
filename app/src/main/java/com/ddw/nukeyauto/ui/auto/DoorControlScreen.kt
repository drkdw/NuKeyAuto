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
import com.ddw.nukeyauto.data.model.NukiError
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
                    val message = state.error?.toLocalizedString(carContext)
                    if (message != null) {
                        CarToast.makeText(carContext, message, CarToast.LENGTH_LONG).show()
                    }
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

        // Show countdown when delay is active
        if (state.countdownSeconds > 0) {
            val countdownRow = Row.Builder()
                .setTitle(carContext.getString(R.string.auto_row_title))
                .addText(carContext.getString(R.string.status_countdown, state.countdownSeconds))
                .setImage(CarIcon.Builder(
                    IconCompat.createWithResource(carContext, R.drawable.ic_hourglass)
                ).setTint(CarColor.PRIMARY).build())
                .build()
            return PaneTemplate.Builder(
                Pane.Builder().addRow(countdownRow).build()
            ).setHeader(header).build()
        }

        // Empty pane during loading — Car App Library forbids rows+actions with setLoading(true)
        if (state.isLoading) {
            return PaneTemplate.Builder(
                Pane.Builder().setLoading(true).build()
            ).setHeader(header).build()
        }

        // Icon tints match mobile color logic: all normal states = PRIMARY
        val icon = when (state.lockState) {
            LockState.Locked -> CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_lock)
            ).setTint(CarColor.PRIMARY).build()
            LockState.Unlocked -> CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_lock_open)
            ).setTint(CarColor.PRIMARY).build()
            LockState.Unlatched -> CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_lock_open)
            ).setTint(CarColor.PRIMARY).build()
            LockState.Locking, LockState.Unlocking, LockState.Unlatching -> CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_refresh)
            ).setTint(CarColor.SECONDARY).build()
            LockState.MotorBlocked -> CarIcon.Builder(
                IconCompat.createWithResource(carContext, R.drawable.ic_lock)
            ).setTint(CarColor.YELLOW).build()
            LockState.Unknown -> CarIcon.ALERT
        }

        val statusText = when (state.lockState) {
            LockState.Locked     -> carContext.getString(R.string.status_locked)
            LockState.Locking    -> carContext.getString(R.string.status_locking)
            LockState.Unlocked   -> carContext.getString(R.string.status_unlocked)
            LockState.Unlocking  -> carContext.getString(R.string.status_unlocking)
            LockState.Unlatched  -> carContext.getString(R.string.status_unlatched)
            LockState.Unlatching -> carContext.getString(R.string.status_unlatching)
            LockState.MotorBlocked -> carContext.getString(R.string.status_motor_blocked)
            LockState.Unknown    -> state.error?.toLocalizedString(carContext) ?: carContext.getString(R.string.status_unknown)
        }

        // Hide battery info when in error state with no valid battery reading
        val statusLine = if (state.lockState == LockState.Unknown && state.battery <= 0) {
            statusText
        } else {
            carContext.getString(R.string.auto_status_format, statusText, state.battery)
        }

        val row = Row.Builder()
            .setTitle(carContext.getString(R.string.auto_row_title))
            .addText(statusLine)
            .setImage(icon)
            .build()

        // No action buttons during transition states
        val isInTransition = state.lockState in listOf(
            LockState.Locking, LockState.Unlocking, LockState.Unlatching
        )

        val paneBuilder = Pane.Builder().addRow(row)

        if (!isInTransition) {
            when (state.lockState) {
                LockState.Unlocked -> {
                    // Two actions: Open + Lock (PaneTemplate supports max 2 actions)
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
                    // One action: Close (lock)
                    paneBuilder.addAction(buildAction(
                        label = R.string.btn_close_door,
                        confirmLabel = R.string.confirm_close_title,
                        pendingType = PendingAction.CLOSE,
                        execute = { viewModel.lockDoor() }
                    ))
                }
                LockState.Unknown -> {
                    if (state.error != null && !state.error.isConfigError()) {
                        // Retry action for connection/API errors
                        paneBuilder.addAction(
                            Action.Builder()
                                .setTitle(carContext.getString(R.string.btn_retry))
                                .setBackgroundColor(CarColor.PRIMARY)
                                .setOnClickListener { viewModel.refresh() }
                                .build()
                        )
                    } else {
                        // Default: Open door (for Locked, MotorBlocked, config errors)
                        paneBuilder.addAction(buildAction(
                            label = R.string.btn_open_door,
                            confirmLabel = R.string.confirm_open_title,
                            pendingType = PendingAction.OPEN,
                            execute = { viewModel.openDoor() }
                        ))
                    }
                }
                else -> {
                    // Locked, MotorBlocked: Open door
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
            // Second tap: execute
            Action.Builder()
                .setTitle(carContext.getString(confirmLabel))
                .setBackgroundColor(CarColor.PRIMARY)
                .setOnClickListener {
                    pendingAction = null
                    execute()
                }
                .build()
        } else if (repository.isConfirmationEnabled()) {
            // First tap: show confirmation
            Action.Builder()
                .setTitle(carContext.getString(label))
                .setBackgroundColor(CarColor.PRIMARY)
                .setOnClickListener {
                    pendingAction = pendingType
                    invalidate()
                }
                .build()
        } else {
            // No confirmation: execute directly
            Action.Builder()
                .setTitle(carContext.getString(label))
                .setBackgroundColor(CarColor.PRIMARY)
                .setOnClickListener { execute() }
                .build()
        }
    }

}
