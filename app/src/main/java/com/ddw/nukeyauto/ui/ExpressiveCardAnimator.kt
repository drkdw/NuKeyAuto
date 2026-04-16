package com.ddw.nukeyauto.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.RectF
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import com.ddw.nukeyauto.data.model.LockState
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * Handles M3 Expressive shape morphing and animations for the main status card.
 *
 * Each lock state has a distinct organic shape (asymmetric corners). Transitions
 * between states animate the corner radii smoothly, with extra emphasis animations
 * (pulse, bounce, shake) depending on the target state.
 */
class ExpressiveCardAnimator(private val card: MaterialCardView) {

    private var currentAnimator: AnimatorSet? = null
    private var currentState: LockState? = null
    private var pulseAnimator: ValueAnimator? = null

    /** Corner radii per state: [topLeft, topRight, bottomRight, bottomLeft] in dp. */
    private val stateShapes: Map<LockState, FloatArray> = mapOf(
        LockState.Locked to floatArrayOf(40f, 20f, 40f, 20f),
        LockState.Unlocked to floatArrayOf(20f, 40f, 20f, 40f),
        LockState.Unlatched to floatArrayOf(36f, 36f, 36f, 36f),
        LockState.Locking to floatArrayOf(28f, 28f, 28f, 28f),
        LockState.Unlocking to floatArrayOf(28f, 28f, 28f, 28f),
        LockState.Unlatching to floatArrayOf(28f, 28f, 28f, 28f),
        LockState.MotorBlocked to floatArrayOf(12f, 12f, 12f, 12f),
        LockState.Unknown to floatArrayOf(32f, 32f, 32f, 32f)
    )

    /** Shape for the welcome / no-credentials state. */
    private val welcomeShape = floatArrayOf(36f, 36f, 36f, 36f)

    /**
     * Animate the card to the shape and emphasis for the given lock state.
     *
     * @param state the new lock state
     * @param isWelcome true when showing the first-setup welcome screen
     * @param isError true when the state includes an error (Unknown + error)
     */
    fun transitionTo(state: LockState, isWelcome: Boolean = false, isError: Boolean = false) {
        val targetCorners = if (isWelcome) welcomeShape else stateShapes[state] ?: stateShapes[LockState.Unknown]!!

        // Cancel any running animations
        currentAnimator?.cancel()
        stopPulse()

        val density = card.resources.displayMetrics.density
        val currentShape = getCurrentCorners()
        val targetPx = targetCorners.map { it * density }.toFloatArray()

        // Shape morph animation
        val morphAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = MORPH_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                val tl = lerp(currentShape[0], targetPx[0], fraction)
                val tr = lerp(currentShape[1], targetPx[1], fraction)
                val br = lerp(currentShape[2], targetPx[2], fraction)
                val bl = lerp(currentShape[3], targetPx[3], fraction)

                card.shapeAppearanceModel = ShapeAppearanceModel.builder()
                    .setTopLeftCornerSize(tl)
                    .setTopRightCornerSize(tr)
                    .setBottomRightCornerSize(br)
                    .setBottomLeftCornerSize(bl)
                    .build()
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
        val density = card.resources.displayMetrics.density
        card.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setTopLeftCornerSize(corners[0] * density)
            .setTopRightCornerSize(corners[1] * density)
            .setBottomRightCornerSize(corners[2] * density)
            .setBottomLeftCornerSize(corners[3] * density)
            .build()
        currentState = state
    }

    /**
     * Get the current corner sizes in pixels from the card.
     */
    private fun getCurrentCorners(): FloatArray {
        val shape = card.shapeAppearanceModel
        val bounds = RectF(0f, 0f, card.measuredWidth.toFloat(), card.measuredHeight.toFloat())
        return floatArrayOf(
            shape.topLeftCornerSize.getCornerSize(bounds),
            shape.topRightCornerSize.getCornerSize(bounds),
            shape.bottomRightCornerSize.getCornerSize(bounds),
            shape.bottomLeftCornerSize.getCornerSize(bounds)
        )
    }

    private fun lerp(start: Float, end: Float, fraction: Float): Float {
        return start + (end - start) * fraction
    }

    companion object {
        private const val MORPH_DURATION = 400L
        private const val PULSE_DURATION = 800L
        private const val PULSE_SCALE = 0.97f

        /**
         * Quick horizontal shake for error states.
         */
        fun buildShakeAnimator(view: View): ObjectAnimator {
            return ObjectAnimator.ofFloat(
                view, "translationX",
                0f, -12f, 12f, -10f, 10f, -6f, 6f, 0f
            ).apply {
                duration = 500
                interpolator = DecelerateInterpolator()
            }
        }

        /**
         * Bounce scale for success states (door opened).
         */
        fun buildBounceAnimator(view: View): AnimatorSet {
            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.05f, 0.97f, 1f).apply {
                duration = 500
                interpolator = OvershootInterpolator(2f)
            }
            val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.05f, 0.97f, 1f).apply {
                duration = 500
                interpolator = OvershootInterpolator(2f)
            }
            return AnimatorSet().apply { playTogether(scaleX, scaleY) }
        }

        /**
         * Subtle settle animation for locked state (slight scale down then back).
         */
        fun buildSettleAnimator(view: View): AnimatorSet {
            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.98f, 1f).apply {
                duration = 350
                interpolator = DecelerateInterpolator()
            }
            val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.98f, 1f).apply {
                duration = 350
                interpolator = DecelerateInterpolator()
            }
            return AnimatorSet().apply { playTogether(scaleX, scaleY) }
        }

        /**
         * Single pulse for unlocked state.
         */
        fun buildPulseOnce(view: View): AnimatorSet {
            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.03f, 1f).apply {
                duration = 400
                interpolator = AccelerateDecelerateInterpolator()
            }
            val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.03f, 1f).apply {
                duration = 400
                interpolator = AccelerateDecelerateInterpolator()
            }
            return AnimatorSet().apply { playTogether(scaleX, scaleY) }
        }
    }
}
