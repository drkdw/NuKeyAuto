package com.ddw.nukeyauto.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/**
 * Custom FrameLayout that draws an M3 Expressive [RoundedPolygon] shape as its
 * background and clips children to the shape boundary.
 *
 * Supports smooth morphing transitions between any two [RoundedPolygon] shapes
 * via the [androidx.graphics.shapes.Morph] class.
 */
class ShapeMorphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
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
            morph!!.toPath(morphProgress, tempPath)
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
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildPath()
    }

    override fun onDraw(canvas: Canvas) {
        if (!shapePath.isEmpty) {
            canvas.drawPath(shapePath, shapePaint)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (!shapePath.isEmpty) {
            canvas.save()
            canvas.clipPath(shapePath)
            super.dispatchDraw(canvas)
            canvas.restore()
        } else {
            super.dispatchDraw(canvas)
        }
    }

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
    }
}
