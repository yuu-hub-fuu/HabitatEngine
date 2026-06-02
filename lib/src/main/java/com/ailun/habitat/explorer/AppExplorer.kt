package com.ailun.habitat.explorer

import com.ailun.habitat.perception.PerceptionEngine
import com.ailun.habitat.perception.ScreenState
import com.ailun.habitat.skill.SkillRegistry

class AppExplorer(
    private val perceptionEngine: PerceptionEngine,
    private val skillRegistry: SkillRegistry = SkillRegistry,
) {
    /** Words that suggest destructive actions — excluded in safe mode. */
    private val destructiveKeywords = setOf(
        "delete", "删除", "remove", "移除", "logout", "退出", "登出",
        "uninstall", "卸载", "clear data", "清除数据", "reset", "重置",
        "format", "格式化", "confirm", "确认删除",
    )

    suspend fun explore(
        packageName: String,
        maxDepth: Int = 5,
        safeMode: Boolean = true,
    ): AppMap {
        val pages = mutableMapOf<String, PageState>()
        val transitions = mutableListOf<TransitionEdge>()
        val visited = mutableSetOf<String>()

        suspend fun bfs(currentPageId: String, depth: Int, fromButton: String? = null) {
            if (depth > maxDepth) return

            val state = perceptionEngine.capture()

            // Generate a page ID from activity name or hash
            val pageId = state.activityName.ifEmpty { "page_${pages.size}" }
            if (pageId in visited) return
            visited.add(pageId)

            // Analyze page
            val buttons = state.clickableElements.map { elem ->
                val text = elem.text ?: elem.contentDescription ?: ""
                ButtonInfo(
                    bounds = elem.bounds,
                    text = elem.text,
                    contentDescription = elem.contentDescription,
                    actionLabel = if (text.length > 30) text.take(30) + "..." else text,
                    isDestructive = destructiveKeywords.any { kw ->
                        text.contains(kw, ignoreCase = true)
                    },
                    clickConfidence = elem.confidenceScore,
                )
            }.filter { it.actionLabel.isNotBlank() }

            val characteristics = buildList {
                if (state.ocrResult?.fullText?.contains("设置", ignoreCase = true) == true)
                    add("settings_page")
                if (state.ocrResult?.fullText?.contains("搜索", ignoreCase = true) == true)
                    add("search_page")
                if (state.ocrResult?.fullText?.contains("确认", ignoreCase = true) == true)
                    add("confirmation_dialog")
                if (buttons.isEmpty()) add("empty_page")
            }

            val pageState = PageState(pageId, state, buttons, characteristics, depth)
            pages[pageId] = pageState

            // Record transition from previous page
            if (fromButton != null && transitions.none { it.toPageId == pageId }) {
                // Simplification: find the last page that's not this one
                val fromPage = pages.values.lastOrNull { it.pageId != pageId }
                if (fromPage != null) {
                    transitions.add(TransitionEdge(
                        fromPageId = fromPage.pageId,
                        toPageId = pageId,
                        triggerElement = fromButton,
                        triggerAction = "click",
                    ))
                }
            }

            // Explore safe buttons (BFS)
            if (depth < maxDepth) {
                val exploreButtons = if (safeMode) {
                    buttons.filter { !it.isDestructive }
                } else {
                    buttons
                }
                for (button in exploreButtons.take(5)) { // Limit to 5 per page
                    // We can't actually click during exploration without execution context
                    // This records what we WOULD explore
                    transitions.add(TransitionEdge(
                        fromPageId = pageId,
                        toPageId = "unexplored_${pages.size}",
                        triggerElement = button.actionLabel,
                        triggerAction = "click",
                        sideEffects = characteristics,
                    ))
                }
            }
        }

        // Start BFS exploration
        bfs("start", 0, null)
        return AppMap(packageName, pages.toMap(), transitions)
    }
}
