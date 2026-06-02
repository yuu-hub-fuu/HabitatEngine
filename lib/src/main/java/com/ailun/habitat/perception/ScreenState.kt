package com.ailun.habitat.perception

import com.ailun.habitat.api.ContentRect
import com.ailun.habitat.api.OcrResult
import com.ailun.habitat.api.ScreenshotData

data class ScreenState(
    val packageName: String,
    val activityName: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val screenshot: ScreenshotData? = null,
    val ocrResult: OcrResult? = null,
    val a11yTree: AccessibilityTree? = null,
    val controlBounds: List<ControlBounds> = emptyList(),
    val clickableElements: List<ClickableElement> = emptyList(),
) {
    /** Fuse OCR + a11y to find best-matching elements for a target selector. */
    fun findCandidates(selector: String): List<CandidateElement> {
        val results = mutableListOf<CandidateElement>()

        // Search clickable elements (a11y)
        for (elem in clickableElements) {
            val text = elem.text ?: ""
            val desc = elem.contentDescription ?: ""
            if (text.equals(selector, ignoreCase = true)) {
                results.add(CandidateElement(elem, MatchType.EXACT_TEXT, 1.0f, "a11y"))
            } else if (text.contains(selector, ignoreCase = true)) {
                results.add(CandidateElement(elem, MatchType.CONTAINS_TEXT, 0.8f, "a11y"))
            } else if (desc.contains(selector, ignoreCase = true)) {
                results.add(CandidateElement(elem, MatchType.DESCRIPTION_MATCH, 0.7f, "a11y"))
            }
        }

        // Search OCR blocks
        ocrResult?.textBlocks?.forEach { block ->
            if (block.text.contains(selector, ignoreCase = true)) {
                val syntheticElem = ClickableElement(
                    selector = selector,
                    text = block.text,
                    contentDescription = null,
                    bounds = block.boundingBox,
                    isClickable = true,
                    isEditable = false,
                    confidenceScore = block.confidence,
                )
                results.add(CandidateElement(syntheticElem, MatchType.OCR_MATCH, block.confidence * 0.7f, "ocr"))
            }
        }

        return results.sortedByDescending { it.confidenceScore }
    }
}

data class AccessibilityTree(
    val rootNode: AccessibilityNodeSnapshot,
    val nodeCount: Int,
)

data class AccessibilityNodeSnapshot(
    val className: String = "",
    val packageName: String = "",
    val text: String?,
    val contentDescription: String?,
    val viewIdResourceName: String?,
    val bounds: ContentRect,
    val isClickable: Boolean = false,
    val isLongClickable: Boolean = false,
    val isFocusable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isEnabled: Boolean = true,
    val children: List<AccessibilityNodeSnapshot> = emptyList(),
)

data class ControlBounds(
    val bounds: ContentRect,
    val className: String,
    val isClickable: Boolean,
    val resourceId: String?,
)

data class ClickableElement(
    val selector: String,
    val text: String?,
    val contentDescription: String?,
    val bounds: ContentRect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val confidenceScore: Float = 1.0f,
)

data class CandidateElement(
    val element: ClickableElement,
    val matchType: MatchType,
    val confidenceScore: Float,
    val sourceHint: String,
)

enum class MatchType { EXACT_TEXT, CONTAINS_TEXT, OCR_MATCH, DESCRIPTION_MATCH }
