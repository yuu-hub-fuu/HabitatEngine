package com.ailun.habitat

import com.google.gson.annotations.SerializedName

/**
 * 邻接表中的一条节点记录（Gson 友好可变模型）。
 *
 * ### Enforced fields (executed by HabitatExecutor):
 * - [preCondition] — evaluated before handler execution
 * - [postCondition] — evaluated after handler success
 * - [requiredCapabilities] — checked against graph capabilities
 * - [requireConfirmation] — compiled by PlanCompiler into ACTION_CONFIRM nodes
 *
 * ### Reserved fields (schema-only, not yet enforced):
 * - [guards] — structured guard conditions (use [preCondition] for now)
 * - [rollbackNodeId] — for future TryCatch rollback support
 * - [compensateAction] — for future compensating transactions
 * - [capabilities] — for future node-level capability tokens
 * - [riskLevel] — for future per-node risk overrides
 */
class WorkflowNode {
    var id: String? = null
    var type: String? = null
    var params: Map<String, Any>? = null

    var next: String? = null

    /** 分支名 → 下一节点 ID，值可为 null 表示结束。 */
    var branches: Map<String, String?>? = null

    var label: String? = null
    var description: String? = null

    // ── v2 fields (all nullable for backward compat) ──

    /** Guard conditions that must be satisfied before this node executes. */
    var guards: List<Map<String, Any>>? = null

    /** Node ID to which execution rolls back on failure. */
    @SerializedName("rollback_node_id")
    var rollbackNodeId: String? = null

    /** Description of compensating action to undo side effects on failure. */
    @SerializedName("compensate_action")
    var compensateAction: Map<String, Any>? = null

    /** If true, compiler auto-inserts ACTION_CONFIRM before this node. */
    @SerializedName("require_confirmation")
    var requireConfirmation: Boolean = false

    /** Declared capabilities required by this node (for capability tokens). */
    var capabilities: List<String>? = null

    // ── pre/post conditions and risk (Phase 1) ──

    @SerializedName("pre_condition")
    var preCondition: String? = null

    @SerializedName("post_condition")
    var postCondition: String? = null

    @SerializedName("risk_level")
    var riskLevel: String? = null

    @SerializedName("required_capabilities")
    var requiredCapabilities: List<String>? = null
}
