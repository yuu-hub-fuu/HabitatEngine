package com.ailun.habitat.app.handlers

import android.graphics.Color
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.nextResult
import com.ailun.habitat.app.ServiceLifecycleOwner
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * [ACTION_DYNAMIC_ISLAND]：在屏幕顶部显示一个类似 iOS 灵动岛的动画胶囊。
 */
class DynamicIslandNodeHandler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val message = node.params?.get("message")?.toString()?.trim()
            ?: return node.nextResult()

        val interpolatedMessage = context.interpolate(message)
        val durationMs = (node.params?.get("duration_ms") as? Number)?.toLong() ?: 3000L

        withContext(Dispatchers.Main) {
            showDynamicIsland(context.appContext, interpolatedMessage, durationMs)
        }

        return node.nextResult()
    }

    private fun showDynamicIsland(
        appContext: android.content.Context,
        message: String,
        durationMs: Long,
    ) {
        val windowManager = appContext.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager

        val metrics = appContext.resources.displayMetrics
        val islandWidth = (metrics.widthPixels * 0.65f).toInt()
        val islandHeight = (metrics.density * 36).toInt()

        val root = FrameLayout(appContext).apply {
            setBackgroundColor(Color.TRANSPARENT)
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }

        val composeView = ComposeView(appContext).apply {
            setContent {
                DynamicIslandContent(message, durationMs)
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            alpha = 0f
        }

        val lifecycleOwner = ServiceLifecycleOwner()
        lifecycleOwner.onCreate()
        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
        composeView.setViewTreeViewModelStoreOwner(lifecycleOwner)

        root.addView(composeView)

        val params = WindowManager.LayoutParams(
            islandWidth,
            islandHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (metrics.density * 8).toInt()
        }

        try {
            windowManager.addView(root, params)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    root.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction {
                            try {
                                windowManager.removeView(root)
                            } catch (_: Exception) {}
                            lifecycleOwner.onDestroy()
                        }
                        .start()
                } catch (_: Exception) {
                    try { windowManager.removeView(root) } catch (_: Exception) {}
                    lifecycleOwner.onDestroy()
                }
            }, durationMs)
        } catch (e: Exception) {
            android.util.Log.e("DynamicIsland", "add view failed", e)
            lifecycleOwner.onDestroy()
        }
    }
}

@Composable
private fun DynamicIslandContent(
    message: String,
    durationMs: Long,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        delay(durationMs - 400)
        visible = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(300)) +
                    expandIn(
                        expandFrom = Alignment.TopCenter,
                        animationSpec = tween(300)
                    ),
            exit = fadeOut(animationSpec = tween(300)) +
                    shrinkOut(
                        shrinkTowards = Alignment.TopCenter,
                        animationSpec = tween(300)
                    )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(ComposeColor(0xFF1C1C1E)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message,
                    color = ComposeColor.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}
