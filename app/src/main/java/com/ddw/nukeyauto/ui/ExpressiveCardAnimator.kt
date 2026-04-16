package com.ddw.nukeyauto.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import com.ddw.nukeyauto.data.model.LockState

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

    private var currentAnimator: AnimatorSet? = null
    private var currentState: LockState? = null
    private var pulseAnimator: ValueAnimator? = null
    private var welcomeCycleRunnable: Runnable? = null
    private var welcomeCycleIndex: Int = 0
    private var isWelcomeCycling = false

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

        val targetShape = if (isWelcome) welcomeShape() else shapeForState(state, isError)

        // Cancel any running animations
        currentAnimator?.cancel()
        stopPulse()
        stopWelcomeCycle()

        // Morph the shape
        shapeView.morphTo(targetShape)

        // Build emphasis animation based on state type
        val emphasisAnimator = when {
            isError || state == LockState.MotorBlocked -> buildShakeAnimator(shapeView)
            state == LockState.Unlatched -> buildBounceAnimator(shapeView)
            state == LockState.Locked -> buildSettleAnimator(shapeView)
            state == LockState.Unlocked -> buildPulseOnce(shapeView)
            else -> null
        }

        if (emphasisAnimator != null) {
            val set = AnimatorSet()
            set.play(emphasisAnimator)
            set.start()
            currentAnimator = set
        }

        // Start continuous pulse for transitional states
        if (state == LockState.Locking ||
            state == LockState.Unlocking ||
            state == LockState.Unlatching
        ) {
            startPulse()
        }

        // Welcome: cycle through shapes continuously
        if (isWelcome) {
            startWelcomeCycle()
        }

        // Idle rotation for stable states
        shapeView.startIdleAnimation()

        currentState = state
    }

    /**
     * Set the initial shape without animation (e.g. on first layout).
     */
    fun setInitialState(state: LockState, isWelcome: Boolean = false) {
        val shape = if (isWelcome) welcomeShape() else shapeForState(state, isError = false)
        shapeView.setShape(shape)
        currentState = state
        shapeView.startIdleAnimation()
        if (isWelcome) {
            startWelcomeCycle()
        }
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
     * Cycle through all M3 shapes on the welcome screen.
     * Morphs to a new shape every [WELCOME_CYCLE_INTERVAL] ms.
     */
    private fun startWelcomeCycle() {
        if (isWelcomeCycling) return
        isWelcomeCycling = true
        welcomeCycleIndex = 0

        welcomeCycleRunnable = object : Runnable {
            override fun run() {
                if (!isWelcomeCycling) return
                welcomeCycleIndex = (welcomeCycleIndex + 1) % WELCOME_SHAPES.size
                shapeView.morphTo(WELCOME_SHAPES[welcomeCycleIndex](), WELCOME_MORPH_DURATION)
                shapeView.postDelayed(this, WELCOME_CYCLE_INTERVAL)
            }
        }
        shapeView.postDelayed(welcomeCycleRunnable!!, WELCOME_CYCLE_INTERVAL)
    }

    /** Stop the welcome shape cycle. */
    private fun stopWelcomeCycle() {
        isWelcomeCycling = false
        welcomeCycleRunnable?.let { shapeView.removeCallbacks(it) }
        welcomeCycleRunnable = null
    }

    companion object {
        private const val PULSE_DURATION = 800L
        private const val PULSE_SCALE = 0.97f
        private const val WELCOME_CYCLE_INTERVAL = 3000L
        private const val WELCOME_MORPH_DURATION = 1200L

        /** Shapes to cycle through on the welcome screen. */
        private val WELCOME_SHAPES: List<() -> RoundedPolygon> = listOf(
            ::welcomeShape,
            ::circleShape,
            ::diamondShape,
            ::cookie4Shape,
            ::sunnyShape,
            ::cookie6Shape
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
         * Circle — neutral, waiting state (Unknown / loading).
         *
         * High-vertex polygon with maximum rounding to approximate a circle.
         */
        private fun circleShape(): RoundedPolygon = RoundedPolygon(
            numVertices = 10,
            radius = 1f,
            centerX = 0f,
            centerY = 0f,
            rounding = CornerRounding(radius = 1f, smoothing = 1f)
        )

        /**
         * Pentagon — friendly, gem-like feel (Welcome / first setup).
         */
        private fun welcomeShape(): RoundedPolygon = RoundedPolygon(
            numVertices = 5,
            radius = 1f,
            centerX = 0f,
            centerY = 0f,
            rounding = CornerRounding(radius = 0.3f, smoothing = 0.5f)
        )

        /**
         * Map a [LockState] to its corresponding M3 Expressive [RoundedPolygon].
         */
        fun shapeForState(state: LockState, isError: Boolean = false): RoundedPolygon {
            return when {
                isError -> burstShape()
                else -> when (state) {
                    LockState.Locked -> diamondShape()
                    LockState.Unlocked -> cookie4Shape()
                    LockState.Unlatched -> sunnyShape()
                    LockState.Locking,
                    LockState.Unlocking,
                    LockState.Unlatching -> cookie6Shape()
                    LockState.MotorBlocked -> burstShape()
                    LockState.Unknown -> circleShape()
                }
            }
        }

        // -----------------------------------------------------------------
        // Emphasis animations
        // -----------------------------------------------------------------

        /** Quick horizontal shake for error states. */
        fun buildShakeAnimator(view: View): ObjectAnimator {
            return ObjectAnimator.ofFloat(
                view, "translationX",
                0f, -16f, 16f, -12f, 12f, -8f, 8f, 0f
            ).apply {
                duration = 500
                interpolator = DecelerateInterpolator()
            }
        }

        /** Bounce scale for success states (door opened). */
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

        /** Subtle settle animation for locked state. */
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

        /** Single pulse for unlocked state. */
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
