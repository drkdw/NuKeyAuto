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
import com.ddw.nukeyauto.data.model.LockState
import com.ddw.nukeyauto.data.model.NukiError
import com.ddw.nukeyauto.data.repo.NukiRepository
import com.ddw.nukeyauto.ui.mobile.MainViewModel
import com.ddw.nukeyauto.ui.ExpressiveCardAnimator
import com.ddw.nukeyauto.ui.ShapeMorphView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var repository: NukiRepository
    @Inject lateinit var prefs: SharedPreferences
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
        val cardStatus = findViewById<ShapeMorphView>(R.id.cardStatus)
        val surfaceAttr = com.google.android.material.R.attr.colorSurfaceContainerHighest
        cardStatus.shapeColor = MaterialColors.getColor(cardStatus, surfaceAttr, 0)
        val cardAnimator = ExpressiveCardAnimator(cardStatus)
        var isFirstState = true
        var countdownMax = 0

        swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_supporter -> {
                    val count = BillingHelper.getDonationCount(prefs)
                    val msg = resources.getQuantityString(R.plurals.supporter_thanks, count, count)
                    MaterialAlertDialogBuilder(this)
                        .setIcon(R.drawable.ic_star)
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "state=$state")
                    }

                    tvBattery.text = getString(R.string.battery_label, state.battery)
                    swipeRefresh.isRefreshing = state.isLoading && state.countdownSeconds == 0

                    val colorPrimary = MaterialColors.getColor(btnAction, android.R.attr.colorPrimary)
                    val colorError = MaterialColors.getColor(btnAction, android.R.attr.colorError)

                    // Countdown: fixed shape + smooth progress stroke
                    if (state.countdownSeconds > 0) {
                        if (countdownMax == 0) {
                            countdownMax = state.countdownSeconds
                            cardAnimator.holdCountdownShape()
                            // Start one smooth animation from 0→1 over the full duration
                            val strokeWidthPx = 4f * resources.displayMetrics.density
                            val totalMs = countdownMax * 1000L
                            cardStatus.startStrokeAnimation(totalMs, colorPrimary, strokeWidthPx)
                        }
                        tvCountdown.text = getString(R.string.status_countdown, state.countdownSeconds)
                        tvCountdown.visibility = View.VISIBLE
                        btnAction.isEnabled = false
                    } else {
                        if (countdownMax > 0) {
                            // Brief pause at full, then clear and resume cycling
                            cardStatus.postDelayed({
                                cardStatus.stopStrokeLoader()
                                cardAnimator.resumeAmbientCycle()
                            }, 300)
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
                        ivIcon.setImageResource(R.drawable.ic_lock)
                        ivIcon.imageTintList = ColorStateList.valueOf(colorPrimary)
                        tvStatus.text = getString(R.string.welcome_title)
                        tvBattery.visibility = View.GONE
                        tvWelcome.visibility = View.VISIBLE
                        btnAction.text = getString(R.string.btn_get_started)
                        btnAction.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                        btnAction.isEnabled = true
                        btnAction.setOnClickListener {
                            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        }
                        if (isFirstState) {
                            cardAnimator.setInitialState(LockState.Unknown, isWelcome = true)
                            isFirstState = false
                        } else {
                            cardAnimator.transitionTo(LockState.Unknown, isWelcome = true)
                        }
                        return@collect
                    }

                    when (state.lockState) {
                        LockState.Locked -> {
                            ivIcon.setImageResource(R.drawable.ic_lock)
                            ivIcon.imageTintList = ColorStateList.valueOf(colorPrimary)
                            tvStatus.text = getString(R.string.status_locked)
                            btnAction.text = getString(R.string.btn_open_door)
                            btnAction.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                            btnAction.isEnabled = !state.isLoading
                        }
                        LockState.Unlocked -> {
                            ivIcon.setImageResource(R.drawable.ic_lock_open)
                            ivIcon.imageTintList = ColorStateList.valueOf(colorPrimary)
                            tvStatus.text = getString(R.string.status_unlocked)
                            btnAction.text = getString(R.string.btn_open_door)
                            btnAction.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                            btnAction.isEnabled = !state.isLoading
                            btnSecondary.text = getString(R.string.btn_lock_door)
                            btnSecondary.isEnabled = !state.isLoading
                            btnSecondary.visibility = View.VISIBLE
                        }
                        LockState.Unlatched -> {
                            ivIcon.setImageResource(R.drawable.ic_door_open)
                            ivIcon.imageTintList = ColorStateList.valueOf(colorPrimary)
                            tvStatus.text = getString(R.string.status_unlatched)
                            btnAction.text = getString(R.string.btn_close_door)
                            btnAction.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                            btnAction.isEnabled = !state.isLoading
                        }
                        LockState.Locking -> {
                            ivIcon.setImageResource(R.drawable.ic_hourglass)
                            ivIcon.imageTintList = ColorStateList.valueOf(colorPrimary)
                            tvStatus.text = getString(R.string.status_locking)
                            btnAction.isEnabled = false
                        }
                        LockState.Unlocking -> {
                            ivIcon.setImageResource(R.drawable.ic_hourglass)
                            ivIcon.imageTintList = ColorStateList.valueOf(colorPrimary)
                            tvStatus.text = getString(R.string.status_unlocking)
                            btnAction.isEnabled = false
                        }
                        LockState.Unlatching -> {
                            ivIcon.setImageResource(R.drawable.ic_hourglass)
                            ivIcon.imageTintList = ColorStateList.valueOf(colorPrimary)
                            tvStatus.text = getString(R.string.status_unlatching)
                            btnAction.isEnabled = false
                        }
                        LockState.MotorBlocked -> {
                            ivIcon.setImageResource(R.drawable.ic_warning)
                            ivIcon.imageTintList = ColorStateList.valueOf(colorError)
                            tvStatus.text = getString(R.string.status_motor_blocked)
                            val strokeWidthPx = 4f * resources.displayMetrics.density
                            cardStatus.setStrokeProgress(1f, colorError, strokeWidthPx)
                            btnAction.text = getString(R.string.btn_open_door)
                            btnAction.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                            btnAction.isEnabled = !state.isLoading
                        }
                        LockState.Unknown -> {
                            if (state.error != null) {
                                ivIcon.setImageResource(R.drawable.ic_warning)
                                ivIcon.imageTintList = ColorStateList.valueOf(colorError)
                                tvStatus.text = state.error.toLocalizedString(this@MainActivity)
                                if (state.error.isConfigError()) {
                                    btnAction.text = getString(R.string.btn_open_settings)
                                    btnAction.backgroundTintList = ColorStateList.valueOf(colorError)
                                } else {
                                    btnAction.text = getString(R.string.btn_retry)
                                    btnAction.backgroundTintList = ColorStateList.valueOf(colorPrimary)
                                }
                                btnAction.isEnabled = true
                            } else {
                                ivIcon.setImageResource(R.drawable.ic_hourglass)
                                ivIcon.imageTintList = ColorStateList.valueOf(colorPrimary)
                                tvStatus.text = getString(R.string.status_loading)
                                btnAction.isEnabled = false
                            }
                        }
                    }

                    // Animate card shape based on state
                    val isError = state.error != null && state.lockState == LockState.Unknown
                    if (isFirstState) {
                        cardAnimator.setInitialState(state.lockState)
                        isFirstState = false
                    } else {
                        cardAnimator.transitionTo(state.lockState, isError = isError)
                    }

                    // Show Snackbar when an error occurs but the UI still shows a valid lock state
                    if (state.error != null && state.lockState != LockState.Unknown) {
                        Snackbar.make(swipeRefresh, state.error.toLocalizedString(this@MainActivity), Snackbar.LENGTH_LONG)
                            .setAction(R.string.btn_retry) { viewModel.refresh() }
                            .show()
                    }

                    // Primary button click
                    btnAction.setOnClickListener {
                        if (state.lockState == LockState.Unknown && state.error != null) {
                            if (state.error.isConfigError()) {
                                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                            } else {
                                viewModel.refresh()
                            }
                        } else {
                            val isClose = state.lockState == LockState.Unlatched
                            val action: () -> Unit = if (isClose) {
                                { viewModel.lockDoor() }
                            } else {
                                { viewModel.openDoor() }
                            }
                            maybeConfirmThenExecute(
                                isLock = isClose,
                                action = action
                            )
                        }
                    }

                    // Secondary button click (lock — only visible in Unlocked state)
                    btnSecondary.setOnClickListener {
                        maybeConfirmThenExecute(
                            isLock = true,
                            action = { viewModel.lockDoor() }
                        )
                    }

                    tvBattery.visibility = if (
                        state.lockState != LockState.Unknown || state.battery > 0
                    ) View.VISIBLE else View.INVISIBLE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onResume → refresh()")
        }
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
        if (!repository.isConfirmationEnabled()) {
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


    companion object {
        private const val TAG = "MainActivity"
    }
}
