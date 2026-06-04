package com.ailun.habitat.handlers

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.ailun.habitat.INodeHandler
import com.ailun.habitat.NodeResult
import com.ailun.habitat.WorkflowContext
import com.ailun.habitat.WorkflowNode
import org.json.JSONArray
import org.json.JSONObject

class NodeAppSearchHandler : INodeHandler {

    override suspend fun handle(node: WorkflowNode, context: WorkflowContext): NodeResult {
        val params = node.params ?: return NodeResult.failure(node.next, "Missing params")

        val rawQuery = params["query"]?.toString()?.trim()
        val query = rawQuery?.let { context.interpolate(it) }?.lowercase()
        val includeSystem = params["include_system"]?.toString()?.equals("true", true) == true
        val outputVar = params["output_var"]?.toString()?.trim()?.ifEmpty { null } ?: "app_list"
        val maxResults = (params["max_results"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 100
        val sortBy = params["sort_by"]?.toString()?.trim()?.lowercase() ?: "name"

        try {
            val pm = context.appContext.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            val results = apps
                .filter { app ->
                    if (!includeSystem && (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@filter false
                    if (!query.isNullOrBlank()) {
                        val packageName = app.packageName.lowercase()
                        val appLabel = pm.getApplicationLabel(app).toString().lowercase()
                        packageName.contains(query) || appLabel.contains(query)
                    } else true
                }
                .sortedWith(Comparator { a, b ->
                    when (sortBy) {
                        "package" -> a.packageName.compareTo(b.packageName)
                        "install_time" -> {
                            val tA = try { pm.getPackageInfo(a.packageName, 0).firstInstallTime }
                                catch (_: Exception) { 0L }
                            val tB = try { pm.getPackageInfo(b.packageName, 0).firstInstallTime }
                                catch (_: Exception) { 0L }
                            tB.compareTo(tA)
                        }
                        else -> pm.getApplicationLabel(a).toString().lowercase()
                            .compareTo(pm.getApplicationLabel(b).toString().lowercase())
                    }
                })
                .take(maxResults)
                .map { app ->
                    JSONObject().apply {
                        put("package", app.packageName)
                        put("name", pm.getApplicationLabel(app).toString())
                        put("is_system", (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                        try {
                            val pi = pm.getPackageInfo(app.packageName, 0)
                            put("version_name", pi.versionName ?: "")
                            put("version_code", pi.versionCode)
                        } catch (_: Exception) {
                            put("version_name", ""); put("version_code", 0)
                        }
                    }
                }

            val appListJson = JSONArray(results).toString()
            Log.i(TAG, "App search: query='${query ?: ""}', found ${results.size} apps")

            val outVars = mutableMapOf<String, Any?>(
                "app_list" to appListJson, "app_count" to results.size,
                "app_search_success" to true,
            )
            if (outputVar != "app_list") outVars[outputVar] = appListJson
            return NodeResult.success(node.next, outVars)
        } catch (e: Exception) {
            Log.e(TAG, "App search failed: ${e.message}", e)
            return NodeResult.failure(node.next, "App search failed: ${e.message}",
                mapOf("app_search_success" to false, "app_list" to "[]",
                    "app_count" to 0, "app_search_error" to (e.message ?: "Unknown")))
        }
    }

    companion object { private const val TAG = "HabitatAppSearch" }
}
