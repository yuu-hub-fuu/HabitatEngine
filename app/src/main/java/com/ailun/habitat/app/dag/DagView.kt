package com.ailun.habitat.app.dag

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.GestureDetector
import android.view.ScaleGestureDetector
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class DagView(context: Context) : View(context) {

    var graph: DagGraph? = null
        private set

    var onNodeClicked: ((DagNode) -> Unit)? = null
    var onNodeLongPressed: ((DagNode) -> Unit)? = null

    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f
    private var minScale = 0.3f
    private var maxScale = 3.0f

    private val density = resources.displayMetrics.density
    private val nodeWidth = 180f * density
    private val nodeHeight = 56f * density
    private val cornerRadius = 12f * density
    private val arrowSize = 10f * density
    private val nodePadding = 16f * density

    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())
    private val gestureDetector = GestureDetector(context, GestureListener())

    // Paints
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nodeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * density
        color = Color.WHITE
        isFakeBoldText = true
    }
    private val subtextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * density
        color = Color.argb(180, 255, 255, 255)
    }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val edgeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10f * density
        color = Color.GRAY
        textAlign = Paint.Align.CENTER
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val bgFillPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    private var surfaceColor: Int = Color.WHITE
    private var onSurfaceColor: Int = Color.BLACK

    init {
        surfaceColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        onSurfaceColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        nodeBorderPaint.color = Color.argb(60, 0, 0, 0)
        bgFillPaint.color = surfaceColor
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRect(0, 0, view.width, view.height)
            }
        }
    }

    fun setGraph(newGraph: DagGraph) {
        val layoutEngine = DagLayoutEngine
        graph = layoutEngine.layout(newGraph, density)
        zoomToFit()
        invalidate()
    }

    fun zoomToFit() {
        val g = graph ?: return
        if (g.nodes.isEmpty()) return

        val maxX = g.nodes.maxOf { it.x + nodeWidth } + nodePadding * 2
        val maxY = g.nodes.maxOf { it.y + nodeHeight } + nodePadding * 2

        val scaleX = (width - 2 * nodePadding) / maxX.coerceAtLeast(1f)
        val scaleY = (height - 2 * nodePadding) / maxY.coerceAtLeast(1f)
        scaleFactor = minOf(scaleX, scaleY).coerceIn(minScale, maxScale)

        translateX = (width - maxX * scaleFactor) / 2f
        translateY = (height - maxY * scaleFactor) / 2f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (graph != null && graph!!.nodes.isNotEmpty()) {
            zoomToFit()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val g = graph ?: return

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        bgFillPaint.color = surfaceColor
        canvas.drawRect(0f, 0f, viewW, viewH, bgFillPaint)

        canvas.save()
        canvas.clipRect(0f, 0f, viewW, viewH)
        canvas.translate(translateX, translateY)
        canvas.scale(scaleFactor, scaleFactor)

        for (edge in g.edges) {
            val fromNode = g.nodes.find { it.stepId == edge.fromNodeId } ?: continue
            val toNode = g.nodes.find { it.stepId == edge.toNodeId } ?: continue
            drawEdge(canvas, fromNode, toNode, edge)
        }

        for (node in g.nodes) {
            drawNode(canvas, node)
        }

        canvas.restore()
    }

    private fun drawNode(canvas: Canvas, node: DagNode) {
        val rect = RectF(node.x, node.y, node.x + nodeWidth, node.y + nodeHeight)

        val bgColor = categoryColor(node.categoryId)
        nodePaint.color = bgColor
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, nodePaint)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, nodeBorderPaint)

        if (node.isTrigger) {
            val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(255, 255, 255, 255)
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(
                RectF(node.x, node.y, node.x + 6f * density, node.y + nodeHeight),
                3f * density, 3f * density, indicatorPaint
            )
        }

        val textX = node.x + nodePadding
        val textY = node.y + nodeHeight / 2f - 4f * density
        canvas.drawText(node.moduleName, textX, textY, textPaint)

        val labelText = node.label
        if (labelText.isNotEmpty() && labelText != node.moduleName) {
            val labelY = node.y + nodeHeight / 2f + 14f * density
            val maxWidth = nodeWidth - nodePadding * 2
            val truncated = if (subtextPaint.measureText(labelText) > maxWidth) {
                var end = labelText.length
                while (end > 0 && subtextPaint.measureText(labelText.substring(0, end) + "...") > maxWidth) end--
                labelText.substring(0, end.coerceAtLeast(1)) + "..."
            } else labelText
            canvas.drawText(truncated, textX, labelY, subtextPaint)
        }
    }

    private fun drawEdge(canvas: Canvas, from: DagNode, to: DagNode, edge: DagEdge) {
        val startX = from.x + nodeWidth / 2f
        val startY = from.y + nodeHeight
        val endX = to.x + nodeWidth / 2f
        val endY = to.y

        val color = edgeColor(edge.type)
        edgePaint.color = color
        arrowPaint.color = color
        edgeLabelPaint.color = color

        when (edge.type) {
            DagEdgeType.BACK_EDGE -> {
                edgePaint.pathEffect = DashPathEffect(floatArrayOf(8f * density, 4f * density), 0f)
            }
            DagEdgeType.EXIT_EDGE, DagEdgeType.JUMP -> {
                edgePaint.pathEffect = DashPathEffect(floatArrayOf(6f * density, 3f * density), 0f)
            }
            else -> {
                edgePaint.pathEffect = null
            }
        }

        val path = Path()
        val midY = (startY + endY) / 2f
        val cpx1 = startX
        val cpy1 = midY
        val cpx2 = endX
        val cpy2 = midY

        path.moveTo(startX, startY)
        if (edge.type == DagEdgeType.BACK_EDGE) {
            val loopOffset = 40f * density
            val cx1 = from.x + nodeWidth + loopOffset
            val cy1 = from.y + nodeHeight / 2f
            val cx2 = to.x + nodeWidth + loopOffset
            val cy2 = to.y + nodeHeight / 2f
            path.reset()
            path.moveTo(from.x + nodeWidth, from.y + nodeHeight / 2f)
            path.cubicTo(cx1, cy1, cx2, cy2, to.x + nodeWidth, to.y + nodeHeight / 2f)
            canvas.drawPath(path, edgePaint)
            drawArrowHead(
                canvas,
                to.x + nodeWidth, to.y + nodeHeight / 2f,
                cx2, cy2,
                color
            )
            if (edge.label.isNotEmpty()) {
                canvas.drawText(edge.label, cx1, cy1 - 8f * density, edgeLabelPaint)
            }
            return
        }

        path.moveTo(startX, startY)
        path.cubicTo(cpx1, cpy1, cpx2, cpy2, endX, endY)
        canvas.drawPath(path, edgePaint)

        val angle = atan2((endY - cpy2), (endX - cpx2))
        val ax = endX - arrowSize * cos(angle - Math.PI / 6).toFloat()
        val ay = endY - arrowSize * sin(angle - Math.PI / 6).toFloat()
        val bx = endX - arrowSize * cos(angle + Math.PI / 6).toFloat()
        val by = endY - arrowSize * sin(angle + Math.PI / 6).toFloat()

        val arrowPath = Path().apply {
            moveTo(endX, endY)
            lineTo(ax, ay)
            lineTo(bx, by)
            close()
        }
        canvas.drawPath(arrowPath, arrowPaint)

        if (edge.label.isNotEmpty()) {
            val labelX = (startX + endX) / 2f
            val labelY = (startY + endY) / 2f - 6f * density
            canvas.drawText(edge.label, labelX, labelY, edgeLabelPaint)
        }
    }

    private fun drawArrowHead(canvas: Canvas, tipX: Float, tipY: Float, fromX: Float, fromY: Float, color: Int) {
        val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        val angle = atan2((tipY - fromY).toDouble(), (tipX - fromX).toDouble())
        val ax = tipX - arrowSize * cos(angle - Math.PI / 6).toFloat()
        val ay = tipY - arrowSize * sin(angle - Math.PI / 6).toFloat()
        val bx = tipX - arrowSize * cos(angle + Math.PI / 6).toFloat()
        val by = tipY - arrowSize * sin(angle + Math.PI / 6).toFloat()
        val arrowPath = Path().apply {
            moveTo(tipX, tipY)
            lineTo(ax, ay)
            lineTo(bx, by)
            close()
        }
        canvas.drawPath(arrowPath, arrowPaint)
    }

    /** 内置节点分类颜色映射。 */
    private fun categoryColor(categoryId: String): Int = when (categoryId) {
        "trigger" -> Color.rgb(96, 125, 139)
        "interaction" -> Color.rgb(240, 144, 118)
        "logic" -> Color.rgb(255, 193, 7)
        "data" -> Color.rgb(170, 134, 237)
        "file" -> Color.rgb(76, 175, 80)
        "network" -> Color.rgb(231, 94, 169)
        "device" -> Color.rgb(3, 169, 244)
        "core" -> Color.rgb(104, 126, 137)
        "shizuku" -> Color.rgb(63, 77, 171)
        "template" -> Color.rgb(104, 126, 137)
        "ui" -> Color.rgb(104, 126, 137)
        "feishu" -> Color.rgb(0, 185, 107)
        "user_module" -> Color.rgb(168, 224, 102)
        else -> Color.rgb(104, 126, 137) // default: static_pill_color
    }

    private fun edgeColor(type: DagEdgeType): Int = when (type) {
        DagEdgeType.SEQUENTIAL -> Color.rgb(120, 120, 130)
        DagEdgeType.BRANCH_TRUE -> Color.rgb(34, 197, 94)
        DagEdgeType.BRANCH_FALSE -> Color.rgb(239, 68, 68)
        DagEdgeType.BACK_EDGE -> Color.rgb(251, 146, 60)
        DagEdgeType.EXIT_EDGE -> Color.rgb(168, 85, 247)
        DagEdgeType.JUMP -> Color.rgb(168, 85, 247)
        DagEdgeType.TRIGGER -> Color.rgb(59, 130, 246)
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun clampTranslate() {
        val g = graph ?: return
        if (g.nodes.isEmpty() || width <= 0 || height <= 0) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val contentW = (g.nodes.maxOf { it.x + nodeWidth } - g.nodes.minOf { it.x }) * scaleFactor
        val contentH = (g.nodes.maxOf { it.y + nodeHeight } - g.nodes.minOf { it.y }) * scaleFactor

        if (contentW <= viewW && contentH <= viewH) {
            translateX = 0f
            translateY = 0f
            return
        }

        val minNodeX = g.nodes.minOf { it.x } * scaleFactor
        val maxNodeX = (g.nodes.maxOf { it.x + nodeWidth }) * scaleFactor
        val minNodeY = g.nodes.minOf { it.y } * scaleFactor
        val maxNodeY = (g.nodes.maxOf { it.y + nodeHeight }) * scaleFactor

        val overscrollX = viewW * 0.4f
        val overscrollY = viewH * 0.4f

        val rangeMinX = -(maxNodeX + overscrollX) + viewW
        val rangeMaxX = -(minNodeX - overscrollX)
        val rangeMinY = -(maxNodeY + overscrollY) + viewH
        val rangeMaxY = -(minNodeY - overscrollY)

        if (rangeMinX <= rangeMaxX) {
            translateX = translateX.coerceIn(rangeMinX, rangeMaxX)
        } else {
            translateX = (rangeMinX + rangeMaxX) / 2f
        }

        if (rangeMinY <= rangeMaxY) {
            translateY = translateY.coerceIn(rangeMinY, rangeMaxY)
        } else {
            translateY = (rangeMinY + rangeMaxY) / 2f
        }
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScale = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)
            val focusX = detector.focusX
            val focusY = detector.focusY
            translateX = focusX - (focusX - translateX) * (newScale / scaleFactor)
            translateY = focusY - (focusY - translateY) * (newScale / scaleFactor)
            scaleFactor = newScale
            clampTranslate()
            invalidate()
            return true
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, dx: Float, dy: Float): Boolean {
            if (e1 == null || e2.pointerCount > 1) return false
            translateX -= dx
            translateY -= dy
            clampTranslate()
            invalidate()
            return true
        }

        override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
            val hitNode = hitTest(e.x, e.y)
            if (hitNode != null) {
                onNodeClicked?.invoke(hitNode)
                return true
            }
            return false
        }

        override fun onLongPress(e: android.view.MotionEvent) {
            val hitNode = hitTest(e.x, e.y)
            if (hitNode != null) {
                onNodeLongPressed?.invoke(hitNode)
            }
        }

        override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
            zoomToFit()
            invalidate()
            return true
        }
    }

    private fun hitTest(screenX: Float, screenY: Float): DagNode? {
        val g = graph ?: return null
        val canvasX = (screenX - translateX) / scaleFactor
        val canvasY = (screenY - translateY) / scaleFactor
        return g.nodes.find { node ->
            canvasX >= node.x && canvasX <= node.x + nodeWidth &&
            canvasY >= node.y && canvasY <= node.y + nodeHeight
        }
    }
}
