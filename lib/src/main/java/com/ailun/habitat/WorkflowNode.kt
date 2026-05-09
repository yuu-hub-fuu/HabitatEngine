package com.ailun.habitat

/**
 * 邻接表中的一条节点记录（Gson 友好可变模型）。
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
}
