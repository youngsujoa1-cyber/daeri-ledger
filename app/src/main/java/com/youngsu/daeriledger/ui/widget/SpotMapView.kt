package com.youngsu.daeriledger.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.youngsu.daeriledger.R
import com.youngsu.daeriledger.map.MapTileProvider
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/**
 * `지도에서 위치 고르기` 화면의 지도.
 *
 *  - OpenStreetMap 타일을 Canvas 에 직접 그린다(지도 SDK·API 키 없음).
 *  - 손가락으로 밀어 이동(팬), 두 손가락으로 확대·축소(핀치), 두 번 탭하면 한 단계 확대.
 *  - 화면 **정중앙에 고정 핀**이 떠 있고, 핀 끝이 지도의 어느 지점을 가리키는지 위경도로 돌려준다.
 *  - 핀 위치가 바뀌면 [onMove] 로 알리고, 손을 뗀 뒤 잠잠해지면 `settled = true` 로 한 번 더 알린다
 *    (주소 찾기는 그때 한 번만 하면 된다).
 *  - 다크 테마에서도 OSM 표준 컬러 타일을 **그대로** 그린다(색 반전 없음). 도로·공원·물·건물·지명이
 *    선명하게 보이는 대신 지도가 밝아지므로, 핀과 위에 얹는 버튼은 어두운 테두리·그림자로 대비를 준다.
 */
class SpotMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        const val MIN_ZOOM = 6f
        const val MAX_ZOOM = 18f

        /** 위치를 못 받았을 때 처음 맞추는 곳 — 성남 정자동(집 근처) */
        const val DEFAULT_LAT = 37.3661
        const val DEFAULT_LNG = 127.1086
        const val DEFAULT_ZOOM = 16f

        private const val TILE_PX = 256.0
        private const val SETTLE_MS = 420L
    }

    private val tiles = MapTileProvider.get(context)
    private val density = resources.displayMetrics.density

    /** 지도 중심 — 월드 정규 좌표(0~1) */
    private var centerX = 0.5
    private var centerY = 0.5

    /** 확대 단계(정수 타일 줌 사이를 부드럽게) */
    var zoom: Float = DEFAULT_ZOOM
        private set

    private var ignoreTouches = false

    var onMove: ((lat: Double, lng: Double, settled: Boolean) -> Unit)? = null

    val centerLat: Double get() = worldYToLat(centerY)
    val centerLng: Double get() = worldXToLon(centerX)

    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        isDither = true
        // 색 행렬을 걸지 않는다 — OSM 타일 고유 색을 그대로 살린다
        colorFilter = null
    }

    /** 타일을 아직 못 받았을 때 깔리는 바탕 — 밝은 지도와 같은 톤 */
    private val backdropPaint = Paint().apply {
        color = Color.parseColor("#E9EEF3")
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
        color = Color.parseColor("#D6DFE9")
    }

    private val accent = ContextCompat.getColor(context, R.color.accent)

    /** 밝은 지도 위에서도 핀이 묻히지 않게 두르는 어두운 반투명 테두리 */
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.parseColor("#59000000")
    }
    private val pinOutline = Color.WHITE
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000")
    }
    private val stemShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 5.5f * density
        color = Color.parseColor("#40000000")
    }
    private val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 3f * density
        color = accent
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val headRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = pinOutline
    }
    private val headDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pinOutline }

    private val tileRect = RectF()

    private val settleRunnable = Runnable {
        onMove?.invoke(centerLat, centerLng, true)
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            panBy(distanceX, distanceY)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            applyZoom(2f, e.x, e.y)
            scheduleSettle()
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            performClick()
            return super.onSingleTapUp(e)
        }
    })

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val factor = detector.scaleFactor
                if (factor.isFinite() && factor > 0f) applyZoom(factor, detector.focusX, detector.focusY)
                return true
            }
        }
    )

    init {
        isClickable = true
        isFocusable = true
    }

    // ------------------------------------------------------------------ 공개 API

    /** 지도 중심을 위경도로 옮긴다(내 위치·처음 위치 맞추기). */
    fun setCenter(lat: Double, lng: Double, zoom: Float? = null, notify: Boolean = true) {
        centerX = lonToWorldX(lng)
        centerY = latToWorldY(lat)
        if (zoom != null) this.zoom = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        clampCenter()
        invalidate()
        if (notify) {
            onMove?.invoke(centerLat, centerLng, true)
            scheduleSettle()
        }
    }

    /** +,- 버튼용 */
    fun zoomBy(delta: Float) {
        applyZoom(Math.pow(2.0, delta.toDouble()).toFloat(), width / 2f, height / 2f)
        scheduleSettle()
    }

    /** 손가락 이벤트를 받을지 (아직 지도가 준비되지 않았을 때 잠글 수 있다) */
    fun setIgnoreTouches(ignore: Boolean) {
        ignoreTouches = ignore
    }

    // -------------------------------------------------------------------- 제스처

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (ignoreTouches) return false
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount <= 1) gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                removeCallbacks(settleRunnable)
            }
            MotionEvent.ACTION_MOVE -> onMove?.invoke(centerLat, centerLng, false)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                scheduleSettle()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun panBy(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        val perWorld = pxPerWorld()
        centerX += dx / perWorld
        centerY += dy / perWorld
        clampCenter()
        removeCallbacks(settleRunnable)
        onMove?.invoke(centerLat, centerLng, false)
        invalidate()
    }

    private fun applyZoom(factor: Float, focusX: Float, focusY: Float) {
        val beforeX = screenToWorldX(focusX)
        val beforeY = screenToWorldY(focusY)
        val target = (zoom + log2(factor)).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (abs(target - zoom) < 0.001f) return
        zoom = target
        val afterX = screenToWorldX(focusX)
        val afterY = screenToWorldY(focusY)
        centerX += beforeX - afterX
        centerY += beforeY - afterY
        clampCenter()
        onMove?.invoke(centerLat, centerLng, false)
        invalidate()
    }

    private fun scheduleSettle() {
        removeCallbacks(settleRunnable)
        postDelayed(settleRunnable, SETTLE_MS)
    }

    private fun pxPerWorld(): Double = TILE_PX * density * Math.pow(2.0, zoom.toDouble())

    private fun screenToWorldX(px: Float): Double =
        centerX + (px - width / 2.0) / pxPerWorld()

    private fun screenToWorldY(py: Float): Double =
        centerY + (py - height / 2.0) / pxPerWorld()

    private fun clampCenter() {
        // 경도는 한 바퀴를 넘어도 되게 두고, 위도만 메르카토르 한계 안으로
        centerX = ((centerX % 1.0) + 1.0) % 1.0
        centerY = centerY.coerceIn(0.001, 0.999)
    }

    // ---------------------------------------------------------------------- 그리기

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height
        if (w == 0 || h == 0) return

        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), backdropPaint)

        val z = zoom.roundToInt().coerceIn(3, 18)
        val n = 1 shl z
        val tileSide = TILE_PX * density * Math.pow(2.0, (zoom - z).toDouble())
        val scale = tileSide / TILE_PX
        val worldPx = TILE_PX * n
        val cx = centerX * worldPx
        val cy = centerY * worldPx

        val halfW = w / 2.0 / scale
        val halfH = h / 2.0 / scale
        val x0 = floor((cx - halfW) / TILE_PX).toInt()
        val x1 = floor((cx + halfW) / TILE_PX).toInt()
        val y0 = floor((cy - halfH) / TILE_PX).toInt()
        val y1 = floor((cy + halfH) / TILE_PX).toInt()

        var drawn = 0
        for (tx in x0..x1) {
            for (ty in y0..y1) {
                if (ty < 0 || ty >= n) continue
                val left = ((tx * TILE_PX - cx) * scale + w / 2.0).toFloat()
                val top = ((ty * TILE_PX - cy) * scale + h / 2.0).toFloat()
                // 1px 씩 겹쳐 그려 타일 사이 흰 실금이 보이지 않게 한다
                tileRect.set(left, top, left + tileSide.toFloat() + 1f, top + tileSide.toFloat() + 1f)

                val wrapped = ((tx % n) + n) % n
                val bmp: Bitmap? = tiles.tile(z, wrapped, ty) { invalidate() }
                if (bmp != null) {
                    canvas.drawBitmap(bmp, null, tileRect, tilePaint)
                    drawn++
                } else {
                    // 아직 안 받은 타일은 옅은 격자로 자리를 잡아 둔다
                    canvas.drawRect(tileRect, gridPaint)
                }
            }
        }

        if (drawn == 0) {
            // 첫 타일을 기다리는 동안 — 부드러운 안내는 화면 쪽 카드가 한다
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), backdropPaint)
        }

        drawPin(canvas, w / 2f, h / 2f)
    }

    /** 화면 정중앙 고정 핀 — 끝(점)이 지도 위치를 가리킨다 */
    private fun drawPin(canvas: Canvas, tipX: Float, tipY: Float) {
        val headR = 12.5f * density
        val headCy = tipY - 34f * density

        // 바닥 그림자
        canvas.drawOval(
            tipX - 7f * density, tipY - 3.5f * density,
            tipX + 7f * density, tipY + 3.5f * density,
            shadowPaint
        )

        // 기둥 — 밝은 도로 위에서도 보이게 어두운 밑선을 먼저 깐다
        canvas.drawLine(
            tipX, tipY - 2f * density,
            tipX, headCy + headR * 0.7f,
            stemShadowPaint
        )
        canvas.drawLine(
            tipX, tipY - 2f * density,
            tipX, headCy + headR * 0.7f,
            stemPaint
        )

        // 머리 — 흰 테두리 바깥에 어두운 반투명 테두리를 한 겹 둘러 밝은 지도·건물 위에서도 떠 보이게
        canvas.drawCircle(tipX, headCy, headR + 4.5f * density, haloPaint)
        canvas.drawCircle(tipX, headCy, headR + 1.5f * density, headRingPaint)
        canvas.drawCircle(tipX, headCy, headR, headPaint)
        canvas.drawCircle(tipX, headCy, headR * 0.34f, headDotPaint)

        // 핀 끝 점
        canvas.drawCircle(tipX, tipY, 4.6f * density, haloPaint)
        canvas.drawCircle(tipX, tipY, 3.2f * density, headRingPaint)
        canvas.drawCircle(tipX, tipY, 2.4f * density, headPaint)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(settleRunnable)
        super.onDetachedFromWindow()
    }

    // ------------------------------------------------------------- 좌표 변환

    private fun lonToWorldX(lng: Double): Double = (lng + 180.0) / 360.0

    private fun latToWorldY(lat: Double): Double {
        val clamped = lat.coerceIn(-85.05112878, 85.05112878)
        val rad = clamped * PI / 180.0
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0
    }

    private fun worldXToLon(x: Double): Double = x * 360.0 - 180.0

    private fun worldYToLat(y: Double): Double =
        atan(sinh(PI * (1.0 - 2.0 * y))) * 180.0 / PI
}
