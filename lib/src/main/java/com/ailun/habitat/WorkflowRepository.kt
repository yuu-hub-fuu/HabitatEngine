package com.ailun.habitat

import android.content.Context
import android.util.Log
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

object WorkflowRepository {

    private const val PREFS_NAME = "habitat_workflow_library"
    private const val KEY_WORKFLOWS = "workflows_json"
    private const val KEY_FLOAT_MOUNTED_WORKFLOW_ID = "float_mounted_workflow_id"
    private const val TAG = "WorkflowRepository"

    private val gson = GsonBuilder().create()
    private val lock = Any()

    fun getAll(context: Context): List<HabitatWorkflow> {
        synchronized(lock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_WORKFLOWS, null)
            if (raw.isNullOrBlank()) {
                val seeded = defaultWorkflows()
                saveAllInternal(prefs, seeded)
                return seeded
            }
            val type = object : TypeToken<MutableList<HabitatWorkflow>>() {}.type
            val list: MutableList<HabitatWorkflow>? = try {
                gson.fromJson(raw, type)
            } catch (e: Exception) {
                Log.e(TAG, "解析工作流数据失败，回退到默认列表", e)
                null
            }
            return list ?: defaultWorkflows().also { saveAllInternal(prefs, it) }
        }
    }

    fun saveAll(context: Context, workflows: List<HabitatWorkflow>) {
        synchronized(lock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            saveAllInternal(prefs, workflows)
        }
    }

    fun upsert(context: Context, workflow: HabitatWorkflow) {
        synchronized(lock) {
            val list = getAll(context).toMutableList()
            val i = list.indexOfFirst { it.id == workflow.id }
            if (i >= 0) list[i] = workflow else list.add(workflow)
            saveAll(context, list)
        }
    }

    fun delete(context: Context, id: String) {
        synchronized(lock) {
            val list = getAll(context).filterNot { it.id == id }
            saveAll(context, list)
            if (getFloatMountedWorkflowId(context) == id) {
                setFloatMountedWorkflowId(context, null)
            }
        }
    }

    fun getFloatMountedWorkflowId(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FLOAT_MOUNTED_WORKFLOW_ID, null)

    fun setFloatMountedWorkflowId(context: Context, id: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FLOAT_MOUNTED_WORKFLOW_ID, id)
            .commit()
    }

    fun getFloatMountedWorkflow(context: Context): HabitatWorkflow? {
        val id = getFloatMountedWorkflowId(context) ?: return null
        return getAll(context).find { it.id == id }
    }

    private fun saveAllInternal(prefs: android.content.SharedPreferences, workflows: List<HabitatWorkflow>) {
        val json = gson.toJson(workflows)
        try {
            prefs.edit()
                .putString(KEY_WORKFLOWS, json)
                .commit()
        } catch (e: Exception) {
            Log.e(TAG, "保存工作流数据失败", e)
        }
    }

    private fun defaultWorkflows(): List<HabitatWorkflow> = listOf(
        HabitatWorkflow(
            id = "demo_hello",
            name = "你好Habitat",
            description = "Toast 提示 + 振动反馈",
            jsonContent = """
{"start_node_id":"s","nodes":{"s":{"id":"s","type":"ACTION_TOAST","params":{"message":"Hello Habitat!"},"next":"v"},"v":{"id":"v","type":"ACTION_VIBRATE","params":{"duration":150,"amplitude":100}}}}
""".trimIndent(),
        ),
        HabitatWorkflow(
            id = "demo_readscreen",
            name = "屏幕感知",
            description = "读屏查关键字 → 条件分支 → 反馈（需无障碍）",
            jsonContent = """
{"start_node_id":"r1","nodes":{"r1":{"id":"r1","type":"ACTION_READ_SCREEN","params":{"keyword":"设置"},"next":"r2"},"r2":{"id":"r2","type":"CONDITION_SWITCH","params":{"expression":"screen_data == true"},"branches":{"true":"r3","false":"r4"}},"r3":{"id":"r3","type":"ACTION_TOAST","params":{"message":"找到了关键字"},"next":null},"r4":{"id":"r4","type":"ACTION_TOAST","params":{"message":"未找到关键字"},"next":null}}}
""".trimIndent(),
        ),
        HabitatWorkflow(
            id = "demo_math",
            name = "数学运算",
            description = "10+7 → 剪贴板 → 条件判断 → Toast",
            jsonContent = """
{"start_node_id":"m1","nodes":{"m1":{"id":"m1","type":"ACTION_MATH","params":{"operation":"add","a":10,"b":7,"output_var":"sum"},"next":"m2"},"m2":{"id":"m2","type":"ACTION_MATH","params":{"operation":"multiply","a":2,"b":5,"output_var":"mul"},"next":"m3"},"m3":{"id":"m3","type":"ACTION_CLIPBOARD","params":{"action":"set","text":"${'$'}{sum} x ${'$'}{mul}"},"next":"m4"},"m4":{"id":"m4","type":"ACTION_DELAY","params":{"millis":300},"next":"m5"},"m5":{"id":"m5","type":"CONDITION_ADVANCED_SWITCH","params":{"var":"sum","operator":">","value":"15"},"branches":{"true":"m_ok","false":"m_no"}},"m_ok":{"id":"m_ok","type":"ACTION_TOAST","params":{"message":"sum > 15 通过"},"next":null},"m_no":{"id":"m_no","type":"ACTION_TOAST","params":{"message":"sum <= 15 失败"},"next":null}}}
""".trimIndent(),
        ),
        HabitatWorkflow(
            id = "demo_sms",
            name = "验证码读取",
            description = "读取最新验证码短信 → 复制到剪贴板",
            jsonContent = """
{"start_node_id":"sm1","nodes":{"sm1":{"id":"sm1","type":"ACTION_READ_SMS","params":{"filter_by":"latest","extract_code":true,"max_scan":10},"next":"sm2"},"sm2":{"id":"sm2","type":"CONDITION_SWITCH","params":{"expression":"sms_found == true"},"branches":{"true":"sm3","false":"sm4"}},"sm3":{"id":"sm3","type":"ACTION_CLIPBOARD","params":{"action":"set","text":"${'$'}{verification_code}"},"next":"sm5"},"sm4":{"id":"sm4","type":"ACTION_TOAST","params":{"message":"未找到验证码"},"next":null},"sm5":{"id":"sm5","type":"ACTION_TOAST","params":{"message":"验证码已复制到剪贴板"},"next":null}}}
""".trimIndent(),
        ),
        HabitatWorkflow(
            id = "demo_wifi",
            name = "WiFi 开关",
            description = "打开WiFi → 等2秒 → 查状态 → Toast反馈",
            jsonContent = """
{"start_node_id":"w1","nodes":{"w1":{"id":"w1","type":"ACTION_WIFI","params":{"action":"on"},"next":"w2"},"w2":{"id":"w2","type":"ACTION_DELAY","params":{"millis":2000},"next":"w3"},"w3":{"id":"w3","type":"ACTION_WIFI","params":{"action":"status"},"next":"w4"},"w4":{"id":"w4","type":"CONDITION_SWITCH","params":{"expression":"wifi_enabled == true"},"branches":{"true":"w5","false":"w6"}},"w5":{"id":"w5","type":"ACTION_TOAST","params":{"message":"WiFi 已开启"},"next":null},"w6":{"id":"w6","type":"ACTION_TOAST","params":{"message":"WiFi 开启失败"},"next":null}}}
""".trimIndent(),
        ),
        HabitatWorkflow(
            id = "demo_screenshot",
            name = "截屏记录",
            description = "截屏 → 检查结果 → 条件反馈",
            jsonContent = """
{"start_node_id":"ss1","nodes":{"ss1":{"id":"ss1","type":"ACTION_GET_TIME","params":{"format":"yyyyMMdd_HHmmss","output_var":"ts"},"next":"ss2"},"ss2":{"id":"ss2","type":"ACTION_LOG","params":{"message":"开始截屏..."},"next":"ss3"},"ss3":{"id":"ss3","type":"ACTION_SCREENSHOT","params":{"format":"file","output_var":"img_path"},"next":"ss_check"},"ss_check":{"id":"ss_check","type":"CONDITION_SWITCH","params":{"expression":"screenshot_success == true"},"branches":{"true":"ss_ok","false":"ss_fail"}},"ss_ok":{"id":"ss_ok","type":"ACTION_TOAST","params":{"message":"截屏成功，已保存"},"next":null},"ss_fail":{"id":"ss_fail","type":"ACTION_TOAST","params":{"message":"截屏失败，请检查 Shizuku"},"next":null}}}
""".trimIndent(),
        ),
        HabitatWorkflow(
            id = "demo_http",
            name = "网络请求",
            description = "HTTP GET → 解析 JSON → Toast 结果",
            jsonContent = """
{"start_node_id":"h1","nodes":{"h1":{"id":"h1","type":"ACTION_HTTP_REQUEST","params":{"url":"https://httpbin.org/get","method":"GET","output_var":"resp"},"next":"h2"},"h2":{"id":"h2","type":"CONDITION_SWITCH","params":{"expression":"http_success == true"},"branches":{"true":"h3","false":"h4"}},"h3":{"id":"h3","type":"ACTION_TOAST","params":{"message":"HTTP 请求成功"},"next":null},"h4":{"id":"h4","type":"ACTION_TOAST","params":{"message":"HTTP 请求失败"},"next":null}}}
""".trimIndent(),
        ),
        HabitatWorkflow(
            id = "demo_loop",
            name = "循环计数",
            description = "变量初始化 → 循环3次 → 完成提示",
            jsonContent = """
{"start_node_id":"lp0","nodes":{"lp0":{"id":"lp0","type":"ACTION_SET_VARIABLE","params":{"name":"i","value":0,"type":"int"},"next":"lp1"},"lp1":{"id":"lp1","type":"ACTION_MATH","params":{"operation":"add","a":"i","b":1,"output_var":"i"},"next":"lp2"},"lp2":{"id":"lp2","type":"ACTION_LOG","params":{"message":"循环中..."},"next":"lp3"},"lp3":{"id":"lp3","type":"CONDITION_ADVANCED_SWITCH","params":{"var":"i","operator":"<","value":"4"},"branches":{"true":"lp1","false":"lp4"}},"lp4":{"id":"lp4","type":"ACTION_TOAST","params":{"message":"循环完成"},"next":null}}}
""".trimIndent(),
        ),
        HabitatWorkflow(
            id = "demo_screenshot_v2",
            name = "截屏工具",
            description = "截屏 → 检查结果 → 成功/失败反馈（需 Shizuku）",
            jsonContent = """
{"start_node_id":"ts","nodes":{"ts":{"id":"ts","type":"ACTION_GET_TIME","params":{"format":"yyyyMMdd_HHmmss","output_var":"timestamp"},"next":"log_start"},"log_start":{"id":"log_start","type":"ACTION_LOG","params":{"message":"开始截屏..."},"next":"capture"},"capture":{"id":"capture","type":"ACTION_SCREENSHOT","params":{"format":"file","output_var":"img_path"},"next":"check"},"check":{"id":"check","type":"CONDITION_SWITCH","params":{"expression":"screenshot_success == true"},"branches":{"true":"log_ok","false":"fail"}},"log_ok":{"id":"log_ok","type":"ACTION_LOG","params":{"message":"截屏成功: ${'$'}{img_path}"},"next":"toast_ok"},"toast_ok":{"id":"toast_ok","type":"ACTION_TOAST","params":{"message":"截屏已保存"}},"fail":{"id":"fail","type":"ACTION_LOG","params":{"message":"截屏失败，请检查 Shizuku"},"next":"toast_fail"},"toast_fail":{"id":"toast_fail","type":"ACTION_TOAST","params":{"message":"截屏失败"}}}}
""".trimIndent(),
        ),
        HabitatWorkflow(
            id = "demo_wechat_monitor",
            name = "微信监控",
            description = "每5秒扫屏 → 发现微信自动点击进入（需无障碍）",
            jsonContent = """
{"start_node_id":"init","nodes":{"init":{"id":"init","type":"ACTION_SET_VARIABLE","params":{"name":"wechat_found","value":false,"type":"bool"},"next":"loop_check"},"loop_check":{"id":"loop_check","type":"CONDITION_SWITCH","params":{"expression":"wechat_found == false"},"branches":{"true":"read_screen","false":"done"}},"read_screen":{"id":"read_screen","type":"ACTION_READ_SCREEN","params":{"keyword":"微信"},"next":"check_found"},"check_found":{"id":"check_found","type":"CONDITION_SWITCH","params":{"expression":"screen_data == true"},"branches":{"true":"click_wechat","false":"wait_retry"}},"click_wechat":{"id":"click_wechat","type":"ACTION_CLICK","params":{"target":"微信"},"next":"mark_found"},"mark_found":{"id":"mark_found","type":"ACTION_SET_VARIABLE","params":{"name":"wechat_found","value":true,"type":"bool"},"next":"notify"},"notify":{"id":"notify","type":"ACTION_TOAST","params":{"message":"已点击微信"},"next":"loop_check"},"wait_retry":{"id":"wait_retry","type":"ACTION_DELAY","params":{"millis":5000},"next":"loop_check"},"done":{"id":"done","type":"ACTION_TOAST","params":{"message":"监控结束：已找到微信"}}}}
""".trimIndent(),
        ),
        HabitatWorkflow(
            id = "demo_sms_code",
            name = "获取验证码",
            description = "读取最新短信 → 提取验证码 → 复制到剪贴板",
            jsonContent = """
{"start_node_id":"read","nodes":{"read":{"id":"read","type":"ACTION_READ_SMS","params":{"filter_by":"latest","extract_code":true,"max_scan":20},"next":"check"},"check":{"id":"check","type":"CONDITION_SWITCH","params":{"expression":"sms_found == true"},"branches":{"true":"copy","false":"fail"}},"copy":{"id":"copy","type":"ACTION_CLIPBOARD","params":{"action":"set","text":"${'$'}{verification_code}"},"next":"toast_ok"},"toast_ok":{"id":"toast_ok","type":"ACTION_TOAST","params":{"message":"验证码 ${'$'}{verification_code} 已复制到剪贴板"}},"fail":{"id":"fail","type":"ACTION_TOAST","params":{"message":"未找到验证码短信"}}}}
""".trimIndent(),
        ),
    )
}
