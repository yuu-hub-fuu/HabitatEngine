package com.ailun.habitat.execution

enum class ExecutionMode {
    DRY_RUN,     // Static scan only: GraphVerifier + RiskEngine, zero device interaction
    SHADOW_RUN,  // Observe screen, predict element existence, no actual actions
    SANDBOX_RUN, // File ops in temp dir, shell commands intercepted, network mocked
    LIVE_RUN,    // Full execution with real device interaction
}
