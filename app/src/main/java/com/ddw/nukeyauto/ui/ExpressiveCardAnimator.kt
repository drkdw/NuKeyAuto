package com.ddw.nukeyauto.ui

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import com.ddw.nukeyauto.data.model.LockState
import com.ddw.nukeyauto.data.model.isTransition

/**
 * Manages M3 Expressive shape morphing and emphasis animations for the main
 * status card.
 *
 * Each lock state maps to a distinct [RoundedPolygon] shape (diamond, cookie,
 * clover, burst, etc.). State transitions trigger a smooth morph animation via
 * [ShapeMorphView.morphTo], with additional emphasis animations (shake, bounce,
 * pulse, settle) layered on top.
 */
class ExpressiveCardAnimator(private val shapeView: ShapeMorphView) {

    private var currentAnimator: Animator? = null
    private var currentState: LockState? = null
    private var pulseAnimator: ValueAnimator? = null
    private var ambientCycleRunnable: Runnable? = null
    private var ambientCycleIndex: Int = 0
    private var isAmbientCycling = false

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

        val targetShape = if (isWelcome) diamondShape() else shapeForState(state, isError)

        // Cancel any running animations
        currentAnimator?.cancel()
        stopPulse()
        stopAmbientCycle()

        // Morph the shape
        shapeView.morphTo(targetShape)

        // Build emphasis animation based on state type
        val emphasisAnimator = when {
            isError || state in listOf(LockState.MotorBlocked, LockState.Uncalibrated, LockState.Undefined) -> buildShakeAnimator(shapeView)
            state == LockState.Unlatched -> scaleAnimator(shapeView, 600, OvershootInterpolator(2f), 1f, 1.06f, 0.96f, 1f)
            state == LockState.Locked -> scaleAnimator(shapeView, 400, DecelerateInterpolator(), 1f, 0.97f, 1f)
            state == LockState.Unlocked -> scaleAnimator(shapeView, 500, AccelerateDecelerateInterpolator(), 1f, 1.04f, 1f)
            else -> null
        }

        emphasisAnimator?.start()
        currentAnimator = emphasisAnimator

        // Start continuous pulse for transitional states
        if (state.isTransition) {
            startPulse()
        }

        // Continuous ambient shape morphing for all states
        startAmbientCycle(isWelcome)

        // Idle rotation for stable states
        shapeView.startIdleAnimation()

        currentState = state
    }

    /**
     * Set the initial shape without animation (e.g. on first layout).
     */
    fun setInitialState(state: LockState, isWelcome: Boolean = false) {
        val shape = if (isWelcome) diamondShape() else shapeForState(state)
        shapeView.setShape(shape)
        currentState = state
        shapeView.startIdleAnimation()
        startAmbientCycle(isWelcome)
    }

    /** Start a gentle continuous pulse for loading/transitional states. */
    private fun startPulse() {
        pulseAnimator = ValueAnimator.ofFloat(1f, PULSE_SCALE).apply {
            duration = PULSE_DURATION
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                val scale = it.animatedValue as Float
                shapeView.scaleX = scale
                shapeView.scaleY = scale
            }
            start()
        }
    }

    /** Stop the continuous pulse and reset scale. */
    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        shapeView.scaleX = 1f
        shapeView.scaleY = 1f
    }

    /**
     * Gently morph through a set of related shapes. Welcome uses a faster cycle,
     * every other state the same slower, more subtle cadence. Also called after the
     * countdown to resume cycling; no-op while already cycling.
     */
    fun startAmbientCycle(isWelcome: Boolean = false) {
        if (isAmbientCycling) return
        isAmbientCycling = true
        ambientCycleIndex = 0

        val shapes = if (isWelcome) WELCOME_SHAPES else AMBIENT_SHAPES
        val interval = if (isWelcome) WELCOME_CYCLE_INTERVAL else AMBIENT_CYCLE_INTERVAL
        val morphDuration = if (isWelcome) WELCOME_MORPH_DURATION else AMBIENT_MORPH_DURATION

        val runnable = object : Runnable {
            override fun run() {
                if (!isAmbientCycling) return
                ambientCycleIndex = (ambientCycleIndex + 1) % shapes.size
                shapeView.morphTo(shapes[ambientCycleIndex](), morphDuration)
                shapeView.postDelayed(this, interval)
            }
        }
        ambientCycleRunnable = runnable
        shapeView.postDelayed(runnable, interval)
    }

    /** Stop the ambient shape cycle. */
    private fun stopAmbientCycle() {
        isAmbientCycling = false
        ambientCycleRunnable?.let { shapeView.removeCallbacks(it) }
        ambientCycleRunnable = null
    }

    /**
     * Hold a fixed shape during countdown (no ambient morphing): the 7-sided cookie,
     * used nowhere else, so the countdown reads as its own state.
     */
    fun holdCountdownShape() {
        stopAmbientCycle()
        shapeView.morphTo(cookie7Shape())
    }

    /**
     * Stop every animation this class owns. Call from the host Activity's onStop
     * and onDestroy — the pulse, ambient cycle and idle rotation all repeat
     * indefinitely and would otherwise keep burning frames off-screen (and keep
     * the Activity alive after teardown).
     */
    fun pause() {
        currentAnimator?.cancel()
        currentAnimator = null
        stopPulse()
        stopAmbientCycle()
        shapeView.stopIdleAnimation()
    }

    /**
     * Restart the continuous animations for the state we were last showing.
     * Call from the host Activity's onStart; no-op before the first state arrives.
     */
    fun resume() {
        val state = currentState ?: return
        startAmbientCycle()
        shapeView.startIdleAnimation()
        if (state.isTransition) {
            startPulse()
        }
    }

    companion object {
        private const val PULSE_DURATION = 800L
        private const val PULSE_SCALE = 0.97f
        private const val WELCOME_CYCLE_INTERVAL = 2500L
        private const val WELCOME_MORPH_DURATION = 1000L
        private const val AMBIENT_CYCLE_INTERVAL = 3500L
        private const val AMBIENT_MORPH_DURATION = 1200L

        /** Shapes to cycle through on the welcome screen. */
        private val WELCOME_SHAPES: List<() -> RoundedPolygon> = listOf(
            ::diamondShape,
            ::cookie4Shape,
            ::sunnyShape,
            ::cookie6Shape,
            ::diamondShape,
            ::sunnyShape
        )

        /**
         * Shapes every non-welcome state gently morphs through. The state's own home
         * shape is set by [transitionTo] before the cycle starts.
         */
        private val AMBIENT_SHAPES: List<() -> RoundedPolygon> = listOf(
            ::diamondShape,
            ::cookie4Shape,
            ::cookie6Shape,
            ::sunnyShape,
            ::burstShape
        )

        // -----------------------------------------------------------------
        // M3 Expressive shape definitions
        // -----------------------------------------------------------------

        /**
         * Diamond — solid and secure feel (Locked state).
         *
         * 4 vertices with generous rounding create a squircle-like diamond.
         */
        private fun diamondShape(): RoundedPolygon = RoundedPolygon(
            numVertices = 4,
            radius = 1f,
            centerX = 0f,
            centerY = 0f,
            rounding = CornerRounding(radius = 0.3f, smoothing = 0.65f)
        )

        /**
         * 4-sided cookie — soft, relaxed bumps (Unlocked state).
         */
        private fun cookie4Shape(): RoundedPolygon = RoundedPolygon.star(
            numVerticesPerRadius = 4,
            radius = 1f,
            innerRadius = 0.72f,
            rounding = CornerRounding(radius = 0.22f, smoothing = 0.5f),
            innerRounding = CornerRounding(radius = 0.22f, smoothing = 0.5f)
        )

        /**
         * Sunny — radiant, open feel (Unlatched / door open).
         *
         * 8-point soft star with gentle bumps.
         */
        private fun sunnyShape(): RoundedPolygon = RoundedPolygon.star(
            numVerticesPerRadius = 8,
            radius = 1f,
            innerRadius = 0.82f,
            rounding = CornerRounding(radius = 0.15f, smoothing = 0.5f),
            innerRounding = CornerRounding(radius = 0.15f, smoothing = 0.5f)
        )

        /**
         * 6-sided cookie — gentle transitional shape (Locking / Unlocking / Unlatching).
         */
        private fun cookie6Shape(): RoundedPolygon = RoundedPolygon.star(
            numVerticesPerRadius = 6,
            radius = 1f,
            innerRadius = 0.78f,
            rounding = CornerRounding(radius = 0.18f, smoothing = 0.5f),
            innerRounding = CornerRounding(radius = 0.18f, smoothing = 0.5f)
        )

        /**
         * 7-sided cookie — held during countdown / delay transitions.
         *
         * Slightly more complex than cookie6 for visual distinction.
         */
        private fun cookie7Shape(): RoundedPolygon = RoundedPolygon.star(
            numVerticesPerRadius = 7,
            radius = 1f,
            innerRadius = 0.76f,
            rounding = CornerRounding(radius = 0.16f, smoothing = 0.5f),
            innerRounding = CornerRounding(radius = 0.16f, smoothing = 0.5f)
        )

        /**
         * Burst — sharp, jagged alarm feel (MotorBlocked / error).
         *
         * 8-point star with minimal rounding.
         */
        private fun burstShape(): RoundedPolygon = RoundedPolygon.star(
            numVerticesPerRadius = 8,
            radius = 1f,
            innerRadius = 0.65f,
            rounding = CornerRounding(radius = 0.07f, smoothing = 0.3f),
            innerRounding = CornerRounding(radius = 0.07f, smoothing = 0.3f)
        )

        /**
         * Map a [LockState] to its corresponding M3 Expressive [RoundedPolygon].
         */
        private fun shapeForState(state: LockState, isError: Boolean = false): RoundedPolygon {
            if (isError) return burstShape()
            return when (state) {
                LockState.Locked, LockState.Unknown -> diamondShape()
                LockState.Unlocked -> cookie4Shape()
                LockState.Unlatched -> sunnyShape()
                LockState.Locking, LockState.Unlocking, LockState.Unlatching,
                LockState.BootRun -> cookie6Shape()
                LockState.MotorBlocked, LockState.Uncalibrated, LockState.Undefined -> burstShape()
            }
        }

        // -----------------------------------------------------------------
        // Emphasis animations
        // -----------------------------------------------------------------

        /** Quick horizontal shake for error states. */
        private fun buildShakeAnimator(view: View): ObjectAnimator {
            return ObjectAnimator.ofFloat(
                view, "translationX",
                0f, -16f, 16f, -12f, 12f, -8f, 8f, 0f
            ).apply {
                duration = 500
                interpolator = DecelerateInterpolator()
            }
        }

        /** Scale emphasis: the same scaleX/scaleY pair for bounce, settle and pulse. */
        private fun scaleAnimator(
            view: View,
            durationMs: Long,
            interpolator: Interpolator,
            vararg values: Float
        ): AnimatorSet {
            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", *values).apply {
                duration = durationMs
                this.interpolator = interpolator
            }
            val scaleY = ObjectAnimator.ofFloat(view, "scaleY", *values).apply {
                duration = durationMs
                this.interpolator = interpolator
            }
            return AnimatorSet().apply { playTogether(scaleX, scaleY) }
        }
    }
}
