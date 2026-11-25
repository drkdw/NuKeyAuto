package com.ddw.nukeyauto

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ddw.nukeyauto.billing.BillingHelper
import com.ddw.nukeyauto.data.PrefKeys
import com.ddw.nukeyauto.data.safeBoolean
import com.ddw.nukeyauto.data.model.DoorState
import com.ddw.nukeyauto.data.model.LockState
import com.ddw.nukeyauto.data.model.doorLabelRes
import com.ddw.nukeyauto.data.model.NukiError
import com.ddw.nukeyauto.data.model.iconRes
import com.ddw.nukeyauto.data.model.isFault
import com.ddw.nukeyauto.data.model.isTransition
import com.ddw.nukeyauto.data.model.statusRes
import com.ddw.nukeyauto.ui.MainViewModel
import com.ddw.nukeyauto.ui.ExpressiveCardAnimator
import com.ddw.nukeyauto.ui.ShapeMorphView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var prefs: SharedPreferences
    private val viewModel: MainViewModel by viewModels()

    private lateinit var cardStatus: ShapeMorphView
    private lateinit var cardAnimator: ExpressiveCardAnimator

    /** Clears the countdown stroke and resumes ambient morphing shortly after the countdown ends. */
    private val resumeAmbientRunnable = Runnable {
        cardStatus.stopStrokeLoader()
        cardAnimator.startAmbientCycle()
    }

    /** Dynamic-colors setting this activity was themed with; a change recreates it. */
    private var dynamicColorsAtCreate = true

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // After super.onCreate: Hilt injects prefs there
        dynamicColorsAtCreate = prefs.safeBoolean(PrefKeys.DYNAMIC_COLORS, true)
        setContentView(R.layout.activity_main)

        // Handle window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val ivIcon = findViewById<ImageView>(R.id.ivIcon)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        val tvBattery = findViewById<TextView>(R.id.tvBattery)
        val tvCountdown = findViewById<TextView>(R.id.tvCountdown)
        val tvWelcome = findViewById<TextView>(R.id.tvWelcome)
        val btnAction = findViewById<MaterialButton>(R.id.btnAction)
        val btnSecondary = findViewById<MaterialButton>(R.id.btnSecondary)
        val swipeRefresh = findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        cardStatus = findViewById(R.id.cardStatus)
        val surfaceAttr = com.google.android.material.R.attr.colorSurfaceContainerHighest
        cardStatus.shapeColor = MaterialColors.getColor(cardStatus, surfaceAttr, 0)
        cardAnimator = ExpressiveCardAnimator(cardStatus)
        var isFirstState = true
        var countdownMax = 0
        var lastShownError: NukiError? = null

        swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_supporter -> {
                    val count = BillingHelper.getDonationCount(prefs)
                    val msg = resources.getQuantityString(R.plurals.supporter_thanks, count, count)
                    MaterialAlertDialogBuilder(this)
                        .setIcon(R.drawable.ic_star_shine)
                        .setTitle(R.string.supporter_dialog_title)
                        .setMessage(msg)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    true
                }
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Door left open: watch it while this screen is visible
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.map { it.doorState == DoorState.Open }.distinctUntilChanged()
                    .collectLatest { open -> if (open) viewModel.refreshWhileDoorOpen() }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    Log.d(TAG, "state=$state")
                    toolbar.subtitle = if (viewModel.isDemoMode()) getString(R.string.demo_badge) else null

                    val batteryText = state.battery?.let { getString(R.string.battery_label, it) }
                        ?: getString(if (state.batteryCritical) R.string.battery_critical else R.string.battery_ok)
                    // Door sensor info only when a sensor is in use; nothing changes without one
                    tvBattery.text = listOfNotNull(
                        batteryText,
                        state.doorState.doorLabelRes?.let { getString(R.string.door_label, getString(it)) },
                        if (state.doorSensorBatteryCritical) getString(R.string.door_sensor_battery_low) else null
                    ).joinToString(" · ")
                    swipeRefresh.isRefreshing = state.isLoading && state.countdownSeconds == 0

                    val colorPrimary = MaterialColors.getColor(btnAction, android.R.attr.colorPrimary)
                    val colorError = MaterialColors.getColor(btnAction, android.R.attr.colorError)

                    // Countdown: fixed shape + smooth progress stroke
                    if (state.countdownSeconds > 0) {
                        if (countdownMax == 0) {
                            countdownMax = state.countdownSeconds
                            // A resume queued by a just-cancelled countdown would clear this new stroke
                            cardStatus.removeCallbacks(resumeAmbientRunnable)
                            cardAnimator.holdCountdownShape()
                            // Start one smooth animation from 0→1 over the full duration
                            val strokeWidthPx = 4f * resources.displayMetrics.density
                            val totalMs = countdownMax * 1000L
                            cardStatus.startStrokeAnimation(totalMs, colorPrimary, strokeWidthPx)
                        }
                        tvCountdown.text = getString(R.string.status_countdown, state.countdownSeconds)
                        tvCountdown.visibility = View.VISIBLE
                    } else {
                        if (countdownMax > 0) {
                            // Brief pause at full, then clear and resume cycling
                            cardStatus.postDelayed(resumeAmbientRunnable, RESUME_AMBIENT_DELAY_MS)
                        }
                        countdownMax = 0
                        tvCountdown.visibility = View.GONE
                    }

                    // Defaults: hide secondary button, welcome message, stroke (normal states)
                    btnSecondary.visibility = View.GONE
                    tvWelcome.visibility = View.GONE
                    if (state.countdownSeconds == 0 && state.lockState != LockState.MotorBlocked) {
                        cardStatus.stopStrokeLoader()
                    }

                    // First setup: no credentials yet → friendly welcome screen
                    if (state.error is NukiError.NoCredentials) {
                        // Our own car-and-key mark welcomes people, not a generic lock
                        ivIcon.setImageResource(R.drawable.ic_launcher_monochrome)
                        // The launcher artwork keeps adaptive-icon padding; enlarge it to fill
                        ivIcon.scaleX = BRAND_ICON_SCALE
                        ivIcon.scaleY = BRAND_ICON_SCALE
                        ivIcon.imageTintList = ColorStateList.valueOf(colorPrimary)
                        tvStatus.text = getString(R.string.welcome_title)
                        tvBattery.visibility = View.GONE
                        tvWelcome.visibility = View.VISIBLE
                        btnAction.text = getString(R.string.btn_get_started)
                        btnAction.backgroundTintList = buttonTint(btnAction, colorPrimary)
                        btnAction.isEnabled = true
                        btnAction.setOnClickListener {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        }
                        btnSecondary.text = getString(R.string.btn_try_demo)
                        btnSecondary.isEnabled = true
                        btnSecondary.visibility = View.VISIBLE
                        btnSecondary.setOnClickListener { viewModel.startDemo() }
                        cardStatus.contentDescription = tvStatus.text
                        if (isFirstState) {
                            cardAnimator.setInitialState(LockState.Unknown, isWelcome = true)
                            isFirstState = false
                        } else {
                            cardAnimator.transitionTo(LockState.Unknown, isWelcome = true)
                        }
                        return@collect
                    }

                    ivIcon.scaleX = 1f
                    ivIcon.scaleY = 1f
                    val lock = state.lockState
                    if (lock != LockState.Unknown) {
                        ivIcon.setImageResource(lock.iconRes)
                        ivIcon.imageTintList =
                            ColorStateList.valueOf(if (lock.isFault) colorError else colorPrimary)
                        tvStatus.text = getString(lock.statusRes)
                        val btnRes = when {
                            lock == LockState.Unlatched -> R.string.btn_lock_door
                            lock in RETRY_STATES -> R.string.btn_retry
                            lock.isTransition -> null
                            else -> R.string.btn_open_door
                        }
                        btnRes?.let {
                            btnAction.text = getString(it)
                            btnAction.backgroundTintList = buttonTint(btnAction, colorPrimary)
                        }
                        // Retry stays tappable while loading; door actions wait for the lock
                        btnAction.isEnabled = btnRes != null && (lock in RETRY_STATES || !state.isLoading)
                        if (lock == LockState.Unlocked) {
                            btnSecondary.text = getString(R.string.btn_lock_door)
                            btnSecondary.isEnabled = !state.isLoading
                            btnSecondary.visibility = View.VISIBLE
                        }
                        if (state.lockedButDoorOpen) {
                            ivIcon.setImageResource(R.drawable.ic_warning)
                            ivIcon.imageTintList = ColorStateList.valueOf(colorError)
                            tvStatus.text = getString(R.string.door_open_while_locked)
                        }
                        // Locking or opening a door that stands open does nothing useful
                        if (state.doorState == DoorState.Open && btnRes != null && lock !in RETRY_STATES) {
                            btnAction.text = getString(R.string.door_close_first)
                            btnAction.isEnabled = false
                            btnSecondary.visibility = View.GONE
                        }
                        if (lock == LockState.MotorBlocked) {
                            val strokeWidthPx = 4f * resources.displayMetrics.density
                            cardStatus.setStrokeProgress(1f, colorError, strokeWidthPx)
                        }
                    } else if (state.error != null) {
                        // Unknown lock state with an error to explain it
                        ivIcon.setImageResource(R.drawable.ic_warning)
                        ivIcon.imageTintList = ColorStateList.valueOf(colorError)
                        tvStatus.text = state.error.toLocalizedString(this@MainActivity)
                        if (state.error.isConfigError()) {
                            btnAction.text = getString(R.string.btn_open_settings)
                            btnAction.backgroundTintList = buttonTint(btnAction, colorError)
                        } else {
                            btnAction.text = getString(R.string.btn_retry)
                            btnAction.backgroundTintList = buttonTint(btnAction, colorPrimary)
                        }
                        btnAction.isEnabled = true
                    } else {
                        // Unknown lock state, no error yet: still loading
                        ivIcon.setImageResource(R.drawable.ic_hourglass)
                        ivIcon.imageTintList = ColorStateList.valueOf(colorPrimary)
                        tvStatus.text = getString(R.string.status_loading)
                        btnAction.isEnabled = false
                    }

                    // The shape and icon carry the state visually; give TalkBack the same text
                    cardStatus.contentDescription = tvStatus.text

                    // Animate card shape based on state
                    val isError = state.error != null && state.lockState == LockState.Unknown
                    if (isFirstState) {
                        cardAnimator.setInitialState(state.lockState)
                        isFirstState = false
                    } else {
                        cardAnimator.transitionTo(state.lockState, isError = isError)
                    }

                    // Show Snackbar when an error occurs but the UI still shows a valid lock state.
                    // Once per error: later emissions (ticks, polls) carry the same error object.
                    val isNewError = state.error != lastShownError
                    lastShownError = state.error
                    if (isNewError && state.error != null && state.lockState != LockState.Unknown) {
                        // "Did the door move?" must not vanish before it is read
                        val duration = if (state.error is NukiError.ActionNotConfirmed) {
                            UNCONFIRMED_SNACKBAR_MS
                        } else {
                            Snackbar.LENGTH_LONG
                        }
                        Snackbar.make(swipeRefresh, state.error.toLocalizedString(this@MainActivity), duration)
                            .setAction(R.string.btn_retry) { viewModel.refresh() }
                            .show()
                    }

                    // During the open delay the primary button is the way out
                    if (state.countdownSeconds > 0) {
                        btnAction.text = getString(R.string.btn_cancel)
                        btnAction.backgroundTintList = buttonTint(btnAction, colorError)
                        btnAction.isEnabled = true
                    }

                    // Primary button click
                    btnAction.setOnClickListener {
                        if (state.countdownSeconds > 0) {
                            viewModel.cancelOpen()
                        } else if (state.lockState in RETRY_STATES) {
                            viewModel.refresh()
                        } else if (state.lockState == LockState.Unknown && state.error != null) {
                            if (state.error.isConfigError()) {
                                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                            } else {
                                viewModel.refresh()
                            }
                        } else {
                            // After unlatching, the only sensible next step is locking again
                            val isLock = state.lockState == LockState.Unlatched
                            val action: () -> Unit = if (isLock) {
                                { viewModel.lockDoor() }
                            } else {
                                { viewModel.openDoor() }
                            }
                            maybeConfirmThenExecute(
                                isLock = isLock,
                                action = action
                            )
                        }
                    }

                    // Secondary button click (lock, only visible in Unlocked state)
                    btnSecondary.setOnClickListener {
                        maybeConfirmThenExecute(
                            isLock = true,
                            action = { viewModel.lockDoor() }
                        )
                    }

                    tvBattery.visibility = if (
                        state.lockState != LockState.Unknown || state.battery != null
                    ) View.VISIBLE else View.INVISIBLE
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::cardAnimator.isInitialized) {
            cardAnimator.resume()
        }
    }

    override fun onStop() {
        // Do not keep morphing shapes while the app is in the background.
        if (::cardAnimator.isInitialized) {
            cardAnimator.pause()
        }
        super.onStop()
    }

    override fun onDestroy() {
        // Guarded: onCreate may have failed before the views were bound.
        // Animations are already stopped in onStop and by ShapeMorphView on detach.
        if (::cardStatus.isInitialized) {
            cardStatus.removeCallbacks(resumeAmbientRunnable)
        }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.safeBoolean(PrefKeys.DYNAMIC_COLORS, true) != dynamicColorsAtCreate) {
            recreate()
            return
        }
        Log.d(TAG, "onResume → refresh()")
        viewModel.refresh()
        updateSupporterBadge()
    }

    /** Show or hide the supporter star badge based on donation status. */
    private fun updateSupporterBadge() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val supporterItem = toolbar.menu.findItem(R.id.action_supporter)
        supporterItem?.isVisible = BillingHelper.isSupporter(prefs)
    }

    /**
     * Show a confirmation dialog if the setting is enabled, otherwise execute directly.
     *
     * @param isLock true when locking/closing, false when opening
     * @param action the lambda to execute after confirmation (or immediately)
     */
    private fun maybeConfirmThenExecute(isLock: Boolean, action: () -> Unit) {
        if (!viewModel.isConfirmationEnabled()) {
            action()
            return
        }

        val title = if (isLock) R.string.confirm_lock_title else R.string.confirm_open_title
        val message = if (isLock) R.string.confirm_lock_message else R.string.confirm_open_message

        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.confirm_yes) { _, _ -> action() }
            .setNegativeButton(R.string.confirm_no, null)
            .show()
    }


    /**
     * Button background with a proper disabled look. A plain valueOf() tint keeps the full
     * color when disabled, so a disabled button looked enabled with unreadable text.
     */
    private fun buttonTint(button: View, enabledColor: Int): ColorStateList {
        val onSurface = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnSurface)
        val disabled = MaterialColors.compositeARGBWithAlpha(onSurface, DISABLED_CONTAINER_ALPHA)
        return ColorStateList(
            arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(disabled, enabledColor)
        )
    }

    companion object {
        private const val TAG = "MainActivity"

        /** Material 3 disabled container: onSurface at 12% opacity. */
        private const val DISABLED_CONTAINER_ALPHA = 31

        private const val UNCONFIRMED_SNACKBAR_MS = 10_000

        private const val BRAND_ICON_SCALE = 1.7f

        /** Pause at a full stroke ring before clearing it when the countdown ends. */
        private const val RESUME_AMBIENT_DELAY_MS = 300L
    }
}

/** States whose primary button reads "Retry": it must refresh, never open the door. */
private val RETRY_STATES = setOf(LockState.Uncalibrated, LockState.Undefined)
