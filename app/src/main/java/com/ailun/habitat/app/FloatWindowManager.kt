package com.ailun.habitat.app

import android.app.Application
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ailun.habitat.HabitatExecutionService
import com.ailun.habitat.HabitatStateStore
import com.ailun.habitat.HabitatWorkflow
import com.ailun.habitat.NodeHandlerFactory
import com.ailun.habitat.WorkflowRepository
import com.ailun.habitat.app.bridge.AppAccessibilityProvider
import com.ailun.habitat.app.bridge.ShizukuShellExecutor
import com.ailun.habitat.app.bridge.applyAppHandlers
import com.ailun.habitat.app.HabitatLogger
import com.ailun.habitat.app.habitatColorScheme
import kotlinx.coroutines.*
import kotlin.math.abs

class FloatWindowManager private constructor(private val application: Application) {

    companion object {
        private const val TAG = "FloatWindowManager"
        @Volatile private var instance: FloatWindowManager? = null

        fun getInstance(application: Application): FloatWindowManager =
            instance ?: synchronized(this) { instance ?: FloatWindowManager(application).also { instance = it } }

        fun getInstanceOrNull(): FloatWindowManager? = instance
    }

    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager: WindowManager = application.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var composeView: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null

    private var isExpanded by mutableStateOf(false)
    private var inCloseZone by mutableStateOf(false)
    private var panelDragStartX = 0
    private var panelDragStartY = 0

    private var workflows by mutableStateOf(listOf<HabitatWorkflow>())
    private var libObserverJob: Job? = null

    private val displayMetrics: DisplayMetrics get() = application.resources.displayMetrics
    private val screenWidth: Int get() = displayMetrics.widthPixels
    private val screenHeight: Int get() = displayMetrics.heightPixels
    private val ballSize: Int get() = dp(48)
    private val panelWidth: Int get() = dp(300)
    private val panelMaxHeight: Int get() = dp(440)
    private val closeThreshold: Int get() = (screenHeight * 0.75f).toInt()

    var onWorkflowSelected: ((HabitatWorkflow) -> Unit)? = null
    var onWorkflowStop: ((HabitatWorkflow) -> Unit)? = null
    var onDismiss: (() -> Unit)? = null

    private fun dp(v: Int): Int = (application.resources.displayMetrics.density * v).toInt()
    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    // ── PUBLIC ──

    fun showFloatWindow() { mainScope.launch { ensureOverlayPermissionAndShow() } }
    fun hideFloatWindow() { mainScope.launch { removeOverlay() } }

    fun destroy() {
        libObserverJob?.cancel()
        HabitatExecutionService.activeWorkflowIds().forEach { HabitatExecutionService.stop(it) }
        mainScope.launch { removeOverlay(); mainScope.cancel() }
    }

    fun refreshWorkflows() {
        mainScope.launch {
            withContext(Dispatchers.IO) { workflows = WorkflowRepository.getAll(application) }
        }
    }

    // ── SETUP ──

    private suspend fun ensureOverlayPermissionAndShow() {
        if (!Settings.canDrawOverlays(application)) { HabitatLogger.w(TAG, "overlay permission missing"); return }
        removeOverlay()
        withContext(Dispatchers.IO) { workflows = WorkflowRepository.getAll(application) }
        createOverlay()
        libObserverJob?.cancel()
        libObserverJob = mainScope.launch {
            HabitatStateStore.libraryVersion.collect { version ->
                if (version > 0) workflows = withContext(Dispatchers.IO) { WorkflowRepository.getAll(application) }
            }
        }
    }

    private fun createOverlay() {
        val view = ComposeView(application).apply {
            setContent {
                MaterialTheme(colorScheme = habitatColorScheme()) {
                    val runningStates by HabitatStateStore.runningStates.collectAsState()
                    val activeIds = runningStates.keys

                    FloatWindowUI(
                        isExpanded = isExpanded,
                        inCloseZone = inCloseZone,
                        workflows = workflows,
                        activeJobIds = activeIds,
                        onDismiss = { isExpanded = false; updateLayout() },
                        onWorkflowSelected = { wf ->
                            onWorkflowSelected?.invoke(wf) ?: executeHabitatWorkflow(wf)
                        },
                        onWorkflowStop = { wf ->
                            HabitatExecutionService.stop(wf.id)
                            onWorkflowStop?.invoke(wf)
                        },
                    )
                }
            }
        }

        val owner = ServiceLifecycleOwner()
        owner.onCreate()
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
        view.setViewTreeViewModelStoreOwner(owner)
        lifecycleOwner = owner

        val params = WindowManager.LayoutParams(
            ballSize, ballSize, overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - dp(70)
            y = screenHeight / 2 - ballSize / 2
        }
        layoutParams = params

        setupTouch(view, params)
        try { windowManager.addView(view, params); composeView = view }
        catch (e: Exception) { HabitatLogger.e(TAG, "创建悬浮窗失败", e); owner.onDestroy() }
    }

    // ── TOUCH (simplified: no resize, direct position update) ──

    private fun setupTouch(view: View, params: WindowManager.LayoutParams) {
        val wm = windowManager
        var downRawX = 0f
        var downRawY = 0f
        var downParamX = 0
        var downParamY = 0
        var moved = false
        var dragY = 0

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_OUTSIDE -> {
                    if (isExpanded) { isExpanded = false; updateLayout() }
                    true
                }

                MotionEvent.ACTION_DOWN -> {
                    if (isExpanded) {
                        if (event.rawY.toInt() <= params.y + dp(48)) {
                            downRawX = event.rawX; downRawY = event.rawY
                            downParamX = params.x; downParamY = params.y
                            moved = false
                            return@setOnTouchListener true
                        }
                        return@setOnTouchListener false
                    }
                    downRawX = event.rawX; downRawY = event.rawY
                    downParamX = params.x; downParamY = params.y
                    moved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isExpanded) {
                        val dx = event.rawX - downRawX; val dy = event.rawY - downRawY
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            moved = true
                            params.x = (downParamX + dx.toInt()).coerceIn(0, screenWidth - params.width)
                            params.y = (downParamY + dy.toInt()).coerceIn(statusBarHeight(), screenHeight - params.height)
                            try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
                        }
                        return@setOnTouchListener true
                    }

                    val dx = event.rawX - downRawX; val dy = event.rawY - downRawY
                    if (abs(dx) > 8 || abs(dy) > 8) {
                        moved = true
                        params.x = (downParamX + dx.toInt()).coerceIn(0, screenWidth - ballSize)
                        params.y = (downParamY + dy.toInt()).coerceIn(statusBarHeight(), screenHeight - ballSize)
                        dragY = event.rawY.toInt()
                        inCloseZone = (dragY + ballSize / 2) > closeThreshold
                        try { wm.updateViewLayout(view, params) } catch (_: Exception) {}
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (isExpanded) {
                        if (moved) {
                            updateLayout()
                            return@setOnTouchListener true
                        }
                        return@setOnTouchListener false
                    }

                    if (moved) {
                        inCloseZone = false
                        if (dragY > closeThreshold) {
                            hideFloatWindow()
                        } else {
                            animateToEdge(params)
                        }
                    } else {
                        isExpanded = true
                        updateLayout()
                    }
                    true
                }

                else -> false
            }
        }
    }

    // ── LAYOUT ──

    private fun updateLayout() {
        val v = composeView ?: return
        val p = layoutParams ?: return

        if (isExpanded) {
            val h = panelMaxHeight.coerceAtMost(screenHeight - statusBarHeight() - dp(16))
            p.width = panelWidth; p.height = h
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            p.x = ((screenWidth - panelWidth) / 2).coerceAtLeast(0)
            p.y = ((screenHeight - h) / 2).coerceAtLeast(statusBarHeight())
        } else {
            p.width = ballSize; p.height = ballSize
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            p.flags = p.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
        }
        try { windowManager.updateViewLayout(v, p) } catch (_: Exception) {}
    }

    // ── EDGE SNAP (animated) ──

    private fun animateToEdge(params: WindowManager.LayoutParams) {
        val v = composeView ?: return
        val startX = params.x
        val startY = params.y.coerceIn(statusBarHeight(), screenHeight - ballSize)
        val targetX = if (startX + ballSize / 2 < screenWidth / 2) dp(6) else screenWidth - ballSize - dp(6)

        if (startX == targetX && startY == params.y) return

        android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val f = animatedFraction
                params.x = (startX + ((targetX - startX) * f).toInt())
                params.y = (startY + ((startY - params.y.coerceIn(0, screenHeight - ballSize)) * f).toInt())
                try { windowManager.updateViewLayout(v, params) } catch (_: Exception) {}
            }
            start()
        }
    }

    private fun statusBarHeight(): Int {
        val id = application.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) application.resources.getDimensionPixelSize(id) else dp(24)
    }

    // ── EXECUTION ──

    private fun executeHabitatWorkflow(wf: HabitatWorkflow) {
        val factory = NodeHandlerFactory(AppAccessibilityProvider, ShizukuShellExecutor(application)).apply {
            applyAppHandlers(application)
        }
        HabitatExecutionService.start(wf.id, wf.jsonContent, application, factory)
    }

    // ── LIFECYCLE ──

    private fun removeOverlay() {
        composeView?.let { try { windowManager.removeView(it) } catch (_: Exception) {} }
        composeView = null; layoutParams = null
        lifecycleOwner?.onDestroy(); lifecycleOwner = null
        isExpanded = false
    }
}
