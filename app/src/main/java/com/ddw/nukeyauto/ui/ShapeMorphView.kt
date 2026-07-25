package com.ddw.nukeyauto.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.graphics.withClip
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/**
 * Custom FrameLayout that draws an M3 Expressive [RoundedPolygon] shape as its
 * background and clips children to the shape boundary.
 *
 * Supports smooth morphing transitions between any two [RoundedPolygon] shapes
 * via the [androidx.graphics.shapes.Morph] class.
 *
 * An optional animated stroke can be shown around the shape perimeter via
 * [startStrokeAnimation] / [setStrokeProgress] / [stopStrokeLoader].
 */
class ShapeMorphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val shapePath = Path()
    private val tempPath = Path()
    private val scaleMatrix = Matrix()

    private var currentPolygon: RoundedPolygon? = null
    private var morph: Morph? = null
    private var morphProgress: Float = 1f
    private var morphAnimator: ValueAnimator? = null
    private var idleAnimator: ValueAnimator? = null
    private var idleRotation: Float = 0f

    // Stroke progress state
    private var strokeProgress: Float = 0f
    private var strokePathLength: Float = 0f
    private var isStrokeActive = false
    private var strokeAnimator: ValueAnimator? = null

    /**
     * Fill color for the shape background.
     */
    var shapeColor: Int
        get() = shapePaint.color
        set(value) {
            shapePaint.color = value
            invalidate()
        }

    init {
        setWillNotDraw(false)

        // Approximate elevation shadow with oval outline
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: Outline) {
                val inset = (minOf(view.width, view.height) * 0.05f).toInt()
                outline.setOval(inset, inset, view.width - inset, view.height - inset)
            }
        }
    }

    /**
     * Set the shape without animation (e.g. on first layout).
     */
    fun setShape(polygon: RoundedPolygon) {
        currentPolygon = polygon
        morph = null
        morphProgress = 1f
        rebuildPath()
        invalidate()
    }

    /**
     * Animate morphing from the current shape to [target].
     *
     * @param target the target [RoundedPolygon] shape
     * @param durationMs morph animation duration in milliseconds
     */
    fun morphTo(target: RoundedPolygon, durationMs: Long = MORPH_DURATION) {
        morphAnimator?.cancel()

        val from = currentPolygon ?: target
        morph = Morph(from, target)
        currentPolygon = target
        morphProgress = 0f

        morphAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                morphProgress = it.animatedValue as Float
                rebuildPath()
                invalidate()
            }
            start()
        }
    }

    /**
     * Rebuild the [shapePath] from the current morph state or static polygon.
     * Transforms the normalised shape coordinates to fill the view bounds.
     */
    private fun rebuildPath() {
        shapePath.reset()
        if (width == 0 || height == 0) return

        tempPath.reset()
        if (morph != null && morphProgress < 1f) {
            morph?.toPath(morphProgress, tempPath)
        } else {
            currentPolygon?.toPath(tempPath) ?: return
        }

        // Determine scale: shape is normalised around origin with radius ~1.
        // Scale to FIT the smaller view dimension so the full shape is visible.
        val pathBounds = RectF()
        tempPath.computeBounds(pathBounds, true)
        if (pathBounds.isEmpty) return

        val scaleX = width.toFloat() / pathBounds.width()
        val scaleY = height.toFloat() / pathBounds.height()
        val scale = minOf(scaleX, scaleY)

        scaleMatrix.reset()
        scaleMatrix.postTranslate(-pathBounds.centerX(), -pathBounds.centerY())
        scaleMatrix.postScale(scale, scale)
        if (idleRotation != 0f) {
            scaleMatrix.postRotate(idleRotation)
        }
        scaleMatrix.postTranslate(width / 2f, height / 2f)
        tempPath.transform(scaleMatrix)

        shapePath.set(tempPath)

        // Update stroke path length for progress calculation
        if (isStrokeActive) {
            val measure = PathMeasure(shapePath, true)
            strokePathLength = measure.length
            updateStrokeProgress()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildPath()
    }

    /**
     * Cancel every running animation when the view leaves the window.
     * The idle animation repeats indefinitely, so without this it would keep
     * invalidating a detached view — and keep the hosting Activity alive.
     */
    override fun onDetachedFromWindow() {
        morphAnimator?.cancel()
        morphAnimator = null
        stopIdleAnimation()
        stopStrokeLoader()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (!shapePath.isEmpty) {
            canvas.drawPath(shapePath, shapePaint)

            // Draw progress stroke on top of fill
            if (isStrokeActive && strokePathLength > 0f) {
                canvas.drawPath(shapePath, strokePaint)
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!shapePath.isEmpty) {
            canvas.withClip(shapePath) {
                super.dispatchDraw(this)
            }
        } else {
            super.dispatchDraw(canvas)
        }
    }

    // -----------------------------------------------------------------
    // Stroke progress indicator
    // -----------------------------------------------------------------

    /**
     * Start a single smooth stroke animation from 0% to 100% over [durationMs].
     * The stroke draws continuously around the shape perimeter.
     *
     * @param durationMs total animation duration in milliseconds
     * @param color stroke color
     * @param widthPx stroke width in pixels
     */
    fun startStrokeAnimation(durationMs: Long, color: Int, widthPx: Float = STROKE_DEFAULT_WIDTH) {
        strokePaint.color = color
        strokePaint.strokeWidth = widthPx
        isStrokeActive = true
        strokeProgress = 0f

        // Measure current path
        val measure = PathMeasure(shapePath, true)
        strokePathLength = measure.length
        updateStrokeProgress()

        strokeAnimator?.cancel()
        strokeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener {
                strokeProgress = it.animatedValue as Float
                updateStrokeProgress()
                invalidate()
            }
            start()
        }
    }

    /**
     * Set stroke progress directly (no animation). Used for static states
     * like motor blocked.
     *
     * @param progress value between 0.0 and 1.0
     * @param color stroke color
     * @param widthPx stroke width in pixels
     */
    fun setStrokeProgress(progress: Float, color: Int, widthPx: Float = STROKE_DEFAULT_WIDTH) {
        strokeAnimator?.cancel()
        strokePaint.color = color
        strokePaint.strokeWidth = widthPx
        strokeProgress = progress.coerceIn(0f, 1f)
        isStrokeActive = true

        val measure = PathMeasure(shapePath, true)
        strokePathLength = measure.length
        updateStrokeProgress()
        invalidate()
    }

    /**
     * Stop the stroke progress indicator and clear the stroke.
     */
    fun stopStrokeLoader() {
        strokeAnimator?.cancel()
        strokeAnimator = null
        isStrokeActive = false
        strokeProgress = 0f
        strokePaint.pathEffect = null
        invalidate()
    }

    /**
     * Update the [DashPathEffect] so the visible dash covers [strokeProgress]
     * fraction of the total path length.
     */
    private fun updateStrokeProgress() {
        if (strokePathLength <= 0f) return

        val dashLen = strokeProgress * strokePathLength
        val gapLen = strokePathLength

        strokePaint.pathEffect = DashPathEffect(
            floatArrayOf(dashLen, gapLen),
            0f
        )
    }

    // -----------------------------------------------------------------
    // Idle animation
    // -----------------------------------------------------------------

    /**
     * Start a subtle continuous rotation to keep the shape feeling alive.
     * Oscillates gently between -[degrees] and +[degrees].
     */
    fun startIdleAnimation(degrees: Float = IDLE_ROTATION_DEGREES) {
        stopIdleAnimation()
        idleAnimator = ValueAnimator.ofFloat(-degrees, degrees).apply {
            duration = IDLE_DURATION
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                idleRotation = it.animatedValue as Float
                rebuildPath()
                invalidate()
            }
            start()
        }
    }

    /**
     * Stop the idle animation and reset rotation.
     */
    fun stopIdleAnimation() {
        idleAnimator?.cancel()
        idleAnimator = null
        if (idleRotation != 0f) {
            idleRotation = 0f
            rebuildPath()
            invalidate()
        }
    }

    companion object {
        const val MORPH_DURATION = 500L
        private const val IDLE_DURATION = 4000L
        private const val IDLE_ROTATION_DEGREES = 3f

        /** Default stroke width in dp-ish pixels. */
        private const val STROKE_DEFAULT_WIDTH = 6f
    }
}
