package com.ailun.habitat.explorer

import com.ailun.habitat.perception.ScreenState

data class AppMap(
    val packageName: String,
    val pages: Map<String, PageState>,
    val transitions: List<TransitionEdge>,
    val exploredAt: Long = System.currentTimeMillis(),
)

data class PageState(
    val pageId: String,
    val screenState: ScreenState,
    val buttons: List<ButtonInfo>,
    val characteristics: List<String>,
    val depth: Int = 0,
)

data class ButtonInfo(
    val bounds: com.ailun.habitat.api.ContentRect,
    val text: String?,
    val contentDescription: String?,
    val actionLabel: String,
    val isDestructive: Boolean = false,
    val clickConfidence: Float = 1.0f,
)

data class TransitionEdge(
    val fromPageId: String,
    val toPageId: String,
    val triggerElement: String,
    val triggerAction: String,
    val sideEffects: List<String> = emptyList(),
)
