package com.ailun.habitat.perception

import com.ailun.habitat.api.*

class PerceptionEngine(
    private val screenshotProvider: IScreenshotProvider? = null,
    private val ocrProvider: IOcrProvider? = null,
    private val a11yProvider: IAccessibilityProvider? = null,
) {
    suspend fun capture(): ScreenState {
        val screenshot = if (screenshotProvider?.isReady() == true) {
            screenshotProvider.capture()
        } else null

        val ocr = if (screenshot != null && ocrProvider?.isReady() == true) {
            ocrProvider.recognize(screenshot)
        } else null

        val packageName = a11yProvider?.foregroundPackage ?: "unknown"
        val activityName = a11yProvider?.foregroundActivity ?: "unknown"

        val service = a11yProvider?.getService()
        val a11yTree = service?.let { buildAccessibilityTree(it.rootInActiveWindow) }
        val elements = a11yTree?.let { collectClickableElements(it) } ?: emptyList()
        val bounds = a11yTree?.let { collectControlBounds(it) } ?: emptyList()

        return ScreenState(
            packageName = packageName,
            activityName = activityName,
            screenshot = screenshot,
            ocrResult = ocr,
            a11yTree = a11yTree,
            controlBounds = bounds,
            clickableElements = elements,
        )
    }

    suspend fun findElements(state: ScreenState, selector: String): List<CandidateElement> {
        return state.findCandidates(selector)
    }

    suspend fun findBestMatch(state: ScreenState, selector: String): CandidateElement? {
        return findElements(state, selector).firstOrNull()
    }

    fun diffState(before: ScreenState, after: ScreenState): ScreenStateDiff {
        val packageChanged = before.packageName != after.packageName

        val beforeSelectors = before.clickableElements.map { it.selector }.toSet()
        val afterSelectors = after.clickableElements.map { it.selector }.toSet()

        val newElements = after.clickableElements.filter { it.selector !in beforeSelectors }
        val removedElements = before.clickableElements.filter { it.selector !in afterSelectors }

        val textChanges = mutableListOf<TextChange>()
        for (elem in before.clickableElements) {
            val afterElem = after.clickableElements.find { it.selector == elem.selector }
            if (afterElem != null && elem.text != afterElem.text) {
                textChanges.add(TextChange(elem.selector, elem.text, afterElem.text))
            }
        }

        val structuralChanges = mutableListOf<String>()
        if (packageChanged) structuralChanges.add("Package changed: ${before.packageName} → ${after.packageName}")
        if (newElements.isNotEmpty()) structuralChanges.add("${newElements.size} new elements appeared")
        if (removedElements.isNotEmpty()) structuralChanges.add("${removedElements.size} elements disappeared")
        textChanges.forEach { structuralChanges.add("'${it.selector}' text: '${it.beforeText}' → '${it.afterText}'") }

        return ScreenStateDiff(packageChanged, newElements, removedElements, textChanges, structuralChanges)
    }

    private fun buildAccessibilityTree(
        node: android.view.accessibility.AccessibilityNodeInfo?,
    ): AccessibilityTree? {
        if (node == null) return null
        var count = 0
        fun snapshot(n: android.view.accessibility.AccessibilityNodeInfo): AccessibilityNodeSnapshot {
            count++
            val screenRect = android.graphics.Rect()
            n.getBoundsInScreen(screenRect)
            val children = (0 until n.childCount).mapNotNull { i ->
                n.getChild(i)?.let { snapshot(it) }
            }
            return AccessibilityNodeSnapshot(
                className = n.className?.toString() ?: "",
                packageName = n.packageName?.toString() ?: "",
                text = n.text?.toString(),
                contentDescription = n.contentDescription?.toString(),
                viewIdResourceName = n.viewIdResourceName,
                bounds = ContentRect(screenRect.left, screenRect.top, screenRect.right, screenRect.bottom),
                isClickable = n.isClickable,
                isLongClickable = n.isLongClickable,
                isFocusable = n.isFocusable,
                isEditable = n.isEditable,
                isScrollable = n.isScrollable,
                isEnabled = n.isEnabled,
                children = children,
            )
        }
        return AccessibilityTree(snapshot(node), count)
    }

    private fun collectClickableElements(tree: AccessibilityTree): List<ClickableElement> {
        val elements = mutableListOf<ClickableElement>()
        fun walk(node: AccessibilityNodeSnapshot) {
            val selector = node.text ?: node.contentDescription ?: node.viewIdResourceName ?: ""
            if (selector.isNotEmpty() && (node.isClickable || node.isEditable || node.isFocusable)) {
                elements.add(ClickableElement(
                    selector = selector,
                    text = node.text,
                    contentDescription = node.contentDescription,
                    bounds = node.bounds,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    confidenceScore = if (node.isEnabled) 0.9f else 0.4f,
                ))
            }
            node.children.forEach { walk(it) }
        }
        walk(tree.rootNode)
        return elements
    }

    private fun collectControlBounds(tree: AccessibilityTree): List<ControlBounds> {
        val bounds = mutableListOf<ControlBounds>()
        fun walk(node: AccessibilityNodeSnapshot) {
            bounds.add(ControlBounds(
                bounds = node.bounds,
                className = node.className,
                isClickable = node.isClickable,
                resourceId = node.viewIdResourceName,
            ))
            node.children.forEach { walk(it) }
        }
        walk(tree.rootNode)
        return bounds
    }
}
