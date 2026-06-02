package com.ailun.habitat.perception

data class ScreenStateDiff(
    val packageChanged: Boolean = false,
    val newElements: List<ClickableElement> = emptyList(),
    val removedElements: List<ClickableElement> = emptyList(),
    val textChanges: List<TextChange> = emptyList(),
    val structuralChanges: List<String> = emptyList(),
) {
    val anyChange: Boolean get() = packageChanged || newElements.isNotEmpty() ||
        removedElements.isNotEmpty() || textChanges.isNotEmpty()
}

data class TextChange(
    val selector: String,
    val beforeText: String?,
    val afterText: String?,
)
