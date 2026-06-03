package com.ailun.habitat.app.bridge

import android.content.Context
import com.ailun.habitat.NodeHandlerFactory
import com.ailun.habitat.app.ai.LLMServiceProvider
import com.ailun.habitat.app.confirmation.ComposeConfirmationProvider
import com.ailun.habitat.capability.RiskEngine
import com.ailun.habitat.confirmation.ConfirmationManager

/**
 * Single source of truth for building a fully-wired [NodeHandlerFactory].
 *
 * All execution entry points — dashboard test-run, trigger dispatch, float-window tap,
 * mounted-workflow launch — must call [build] so that:
 * - AI nodes get the real [ILLMService] (LiteRT-LM)
 * - High-risk nodes are gated by [ConfirmationManager] with a Compose dialog provider
 * - Accessibility and shell executors are always injected
 *
 * Without this central factory, each call site builds an incomplete factory and the
 * underlying `:lib` refactors (LLM injection, fail-closed confirmation) remain
 * unusable at runtime.
 */
object RuntimeFactoryProvider {

    /**
     * Build a complete [NodeHandlerFactory] for the given [context].
     *
     * Callers should NOT cache the result — the factory binds references to
     * service instances that may change across app restarts.
     */
    fun build(context: Context): NodeHandlerFactory {
        val appContext = context.applicationContext
        return NodeHandlerFactory(
            a11y = AppAccessibilityProvider,
            shell = ShizukuShellExecutor(appContext),
            confirmationManager = ConfirmationManager(
                provider = ComposeConfirmationProvider(appContext),
                riskEngine = RiskEngine(),
            ),
            llmService = LLMServiceProvider.get(appContext),
        ).apply { applyAppHandlers(appContext) }
    }
}
