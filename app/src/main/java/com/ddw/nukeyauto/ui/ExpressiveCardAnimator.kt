package com.ddw.nukeyauto.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.ddw.nukeyauto.data.model.LockState
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * Handles M3 Expressive shape morphing and animations for the main status card.
 *
 * Each lock state has a distinct organic shape (asymmetric corners). Transitions
 * between states animate the corner radii smoothly, with extra emphasis animations
 * (pulse, bounce, shake) depending on the target state.
 */
class ExpressiveCardAnimator(private val card: MaterialCardView) {

    private val density = card.resources.displayMetrics.density
    private var currentAnimator: AnimatorSet? = null
    private var currentState: LockState? = null
    private var pulseAnimator: ValueAnimator? = null

    /** Current corner radii in px, tracked internally to avoid measurement issues. */
    private var currentCornersPx = floatArrayOf(0f, 0f, 0f, 0f)

    /** Corner radii per state: [topLeft, topRight, bottomRight, bottomLeft] in dp. */
    private val stateShapes: Map<LockState, FloatArray> = mapOf(
        LockState.Locked to floatArrayOf(48f, 16f, 48f, 16f),
        LockState.Unlocked to floatArrayOf(16f, 48f, 16f, 48f),
        LockState.Unlatched to floatArrayOf(40f, 40f, 40f, 40f),
        LockState.Locking to floatArrayOf(28f, 28f, 28f, 28f),
        LockState.Unlocking to floatArrayOf(28f, 28f, 28f, 28f),
        LockState.Unlatching to floatArrayOf(28f, 28f, 28f, 28f),
        LockState.MotorBlocked to floatArrayOf(8f, 8f, 8f, 8f),
        LockState.Unknown to floatArrayOf(32f, 32f, 32f, 32f)
    )

    /** Shape for the welcome / no-credentials state. */
    private val welcomeShape = floatArrayOf(40f, 40f, 40f, 40f)

    /**
     * Animate the card to the shape and emphasis for the given lock state.
     *
     * @param state the new lock state
     * @param isWelcome true when showing the first-setup welcome screen
     * @param isError true when the state includes an error (Unknown + error)
     */
    fun transitionTo(state: LockState, isWelcome: Boolean = false, isError: Boolean = false) {
        // Skip if same state (avoid redundant animations)
        if (state == currentState && !isWelcome) return

        val targetCorners = if (isWelcome) welcomeShape else stateShapes[state] ?: stateShapes[LockState.Unknown]!!

        // Cancel any running animations
        currentAnimator?.cancel()
        stopPulse()

        val startCorners = currentCornersPx.copyOf()
        val targetPx = targetCorners.map { it * density }.toFloatArray()

        // Shape morph animation
        val morphAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MORPH_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                val tl = lerp(startCorners[0], targetPx[0], f)
                val tr = lerp(startCorners[1], targetPx[1], f)
                val br = lerp(startCorners[2], targetPx[2], f)
                val bl = lerp(startCorners[3], targetPx[3], f)

                applyCorners(tl, tr, br, bl)
            }
        }

        // Build emphasis animation based on state type
        val emphasisAnimator = when {
            isError || state == LockState.MotorBlocked -> buildShakeAnimator(card)
            state == LockState.Unlatched -> buildBounceAnimator(card)
            state == LockState.Locked -> buildSettleAnimator(card)
            state == LockState.Unlocked -> buildPulseOnce(card)
            else -> null
        }

        // Combine into set
        val set = AnimatorSet()
        if (emphasisAnimator != null) {
            set.playTogether(morphAnimator, emphasisAnimator)
        } else {
            set.play(morphAnimator)
        }
        set.start()
        currentAnimator = set

        // Start continuous pulse for transitional states (locking, unlocking, unlatching)
        if (state == LockState.Locking || state == LockState.Unlocking || state == LockState.Unlatching) {
            startPulse()
        }

        currentState = state
    }

    /**
     * Start a gentle continuous pulse animation for loading/transitional states.
     */
    private fun startPulse() {
        pulseAnimator = ValueAnimator.ofFloat(1f, PULSE_SCALE).apply {
            duration = PULSE_DURATION
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val scale = it.animatedValue as Float
                card.scaleX = scale
                card.scaleY = scale
            }
            start()
        }
    }

    /**
     * Stop the continuous pulse animation and reset scale.
     */
    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        card.scaleX = 1f
        card.scaleY = 1f
    }

    /**
     * Set the initial shape without animation (e.g. on first layout).
     */
    fun setInitialState(state: LockState, isWelcome: Boolean = false) {
        val corners = if (isWelcome) welcomeShape else stateShapes[state] ?: stateShapes[LockState.Unknown]!!
        val tl = corners[0] * density
        val tr = corners[1] * density
        val br = corners[2] * density
        val bl = corners[3] * density
        applyCorners(tl, tr, br, bl)
        currentState = state
    }

    /**
     * Apply corner sizes in pixels to the card and force a visual update.
     */
    private fun applyCorners(tl: Float, tr: Float, br: Float, bl: Float) {
        currentCornersPx[0] = tl
        currentCornersPx[1] = tr
        currentCornersPx[2] = br
        currentCornersPx[3] = bl

        card.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setTopLeftCornerSize(tl)
            .setTopRightCornerSize(tr)
            .setBottomRightCornerSize(br)
            .setBottomLeftCornerSize(bl)
            .build()

        // Force MaterialCardView to redraw with the new shape
        card.invalidateOutline()
        card.invalidate()
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }

    companion object {
        private const val MORPH_DURATION = 500L
        private const val PULSE_DURATION = 800L
        private const val PULSE_SCALE = 0.97f

        /**
         * Quick horizontal shake for error states.
         */
        fun buildShakeAnimator(view: View): ObjectAnimator {
            return ObjectAnimator.ofFloat(
                view, "translationX",
                0f, -16f, 16f, -12f, 12f, -8f, 8f, 0f
            ).apply {
                duration = 500
                interpolator = DecelerateInterpolator()
            }
        }

        /**
         * Bounce scale for success states (door opened).
         */
        fun buildBounceAnimator(view: View): AnimatorSet {
            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.06f, 0.96f, 1f).apply {
                duration = 600
                interpolator = OvershootInterpolator(2f)
            }
            val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.06f, 0.96f, 1f).apply {
                duration = 600
                interpolator = OvershootInterpolator(2f)
            }
            return AnimatorSet().apply { playTogether(scaleX, scaleY) }
        }

        /**
         * Subtle settle animation for locked state (slight scale down then back).
         */
        fun buildSettleAnimator(view: View): AnimatorSet {
            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.97f, 1f).apply {
                duration = 400
                interpolator = DecelerateInterpolator()
            }
            val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.97f, 1f).apply {
                duration = 400
                interpolator = DecelerateInterpolator()
            }
            return AnimatorSet().apply { playTogether(scaleX, scaleY) }
        }

        /**
         * Single pulse for unlocked state.
         */
        fun buildPulseOnce(view: View): AnimatorSet {
            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.04f, 1f).apply {
                duration = 500
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.04f, 1f).apply {
                duration = 500
                interpolator = AccelerateDecelerateInterpolator()
            }
            return AnimatorSet().apply { playTogether(scaleX, scaleY) }
        }
    }
}
