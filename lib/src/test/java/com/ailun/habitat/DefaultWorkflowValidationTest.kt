package com.ailun.habitat

import org.junit.Assert.*
import org.junit.Test

/**
 * Every default workflow shipped with the app MUST pass:
 * 1. HabitatJson.fromJson()  — parseable JSON
 * 2. GraphVerifier.verify()   — edge validity + reachability
 * 3. WorkflowGraphValidator   — structural integrity with registered types
 *
 * If any of these fail, new users see broken demos.
 */
class DefaultWorkflowValidationTest {

    private val registeredTypes = setOf(
        NodeHandlerFactory.ACTION_LOG,
        NodeHandlerFactory.ACTION_TOAST,
        NodeHandlerFactory.ACTION_VIBRATE,
        NodeHandlerFactory.ACTION_GET_TIME,
        NodeHandlerFactory.ACTION_SET_VARIABLE,
        NodeHandlerFactory.ACTION_MATH,
        NodeHandlerFactory.ACTION_TEXT_OPERATION,
        NodeHandlerFactory.ACTION_CLIPBOARD,
        NodeHandlerFactory.ACTION_READ_SCREEN,
        NodeHandlerFactory.CONDITION_SWITCH,
        NodeHandlerFactory.ACTION_SHELL,
        NodeHandlerFactory.ACTION_SCREENSHOT,
        NodeHandlerFactory.ACTION_HTTP_REQUEST,
        NodeHandlerFactory.ACTION_PARSE_JSON,
        NodeHandlerFactory.ACTION_AI_CHAT,
        NodeHandlerFactory.ACTION_LOOP,
        NodeHandlerFactory.ACTION_DELAY,
        NodeHandlerFactory.ACTION_LAUNCH_APP,
        NodeHandlerFactory.ACTION_FIND_ELEMENT,
        NodeHandlerFactory.ACTION_GLOBAL_KEY,
        NodeHandlerFactory.ACTION_WIFI,
        NodeHandlerFactory.ACTION_BLUETOOTH,
        NodeHandlerFactory.ACTION_NETWORK_STATUS,
        NodeHandlerFactory.ACTION_BRIGHTNESS,
        NodeHandlerFactory.ACTION_VOLUME,
        NodeHandlerFactory.ACTION_FILE_OPERATION,
        NodeHandlerFactory.ACTION_RANDOM,
        NodeHandlerFactory.ACTION_BASE64,
        NodeHandlerFactory.ACTION_SEND_NOTIFICATION,
        NodeHandlerFactory.ACTION_SHARE,
        NodeHandlerFactory.ACTION_TEXT_TO_SPEECH,
    )

    // ── Demo workflow JSONs ──
    // Kotlin raw strings still interpolate ${}. Use ${'$'}{var} to escape literal $.

    private val defaultJsons = listOf(
        "demo_hello" to """
{"start_node_id":"s","nodes":{"s":{"id":"s","type":"ACTION_GET_TIME","params":{"format":"HH:mm:ss","output_var":"t"},"next":"toast"},"toast":{"id":"toast","type":"ACTION_TOAST","params":{"message":"Habitat 启动于 ${'$'}{t}"},"next":"v"},"v":{"id":"v","type":"ACTION_VIBRATE","params":{"duration":120,"amplitude":60}}}}
""",
        "demo_variables" to """
{"start_node_id":"m1","nodes":{"m1":{"id":"m1","type":"ACTION_SET_VARIABLE","params":{"name":"a","value":42,"type":"int"},"next":"m2"},"m2":{"id":"m2","type":"ACTION_SET_VARIABLE","params":{"name":"b","value":8,"type":"int"},"next":"m3"},"m3":{"id":"m3","type":"ACTION_MATH","params":{"operation":"add","a":"${'$'}{a}","b":"${'$'}{b}","output_var":"sum"},"next":"m4"},"m4":{"id":"m4","type":"ACTION_MATH","params":{"operation":"multiply","a":"${'$'}{a}","b":"${'$'}{b}","output_var":"prod"},"next":"m5"},"m5":{"id":"m5","type":"ACTION_MATH","params":{"operation":"divide","a":"${'$'}{sum}","b":2,"output_var":"avg"},"next":"m6"},"m6":{"id":"m6","type":"ACTION_CLIPBOARD","params":{"action":"set","text":"${'$'}{a}+${'$'}{b}=${'$'}{sum}, ${'$'}{a}x${'$'}{b}=${'$'}{prod}, avg=${'$'}{avg}"},"next":"m7"},"m7":{"id":"m7","type":"ACTION_LOG","params":{"message":"计算完成: sum=${'$'}{sum}, prod=${'$'}{prod}, avg=${'$'}{avg}"},"next":"m8"},"m8":{"id":"m8","type":"ACTION_TOAST","params":{"message":"已复制到剪贴板: ${'$'}{a}+${'$'}{b}=${'$'}{sum}"}}}}
""",
        "demo_screen" to """
{"start_node_id":"r1","nodes":{"r1":{"id":"r1","type":"ACTION_READ_SCREEN","params":{"keyword":"设置","context_lines":3},"next":"r2"},"r2":{"id":"r2","type":"CONDITION_SWITCH","params":{"expression":"screen_data == true"},"branches":{"true":"r3","false":"r4"}},"r3":{"id":"r3","type":"ACTION_TOAST","params":{"message":"✅ 找到关键词「设置」"},"next":null},"r4":{"id":"r4","type":"ACTION_TOAST","params":{"message":"❌ 未找到关键词「设置」"},"next":null}}}
""",
        "demo_shizuku" to """
{"start_node_id":"s1","nodes":{"s1":{"id":"s1","type":"ACTION_LOG","params":{"message":"开始 Shizuku 系统操作测试"},"next":"s2"},"s2":{"id":"s2","type":"ACTION_SHELL","params":{"command":"getprop ro.product.model","mode":"auto"},"next":"s3"},"s3":{"id":"s3","type":"ACTION_LOG","params":{"message":"设备型号: ${'$'}{shell_output}"},"next":"s4"},"s4":{"id":"s4","type":"ACTION_SHELL","params":{"command":"dumpsys battery | grep level","mode":"auto"},"next":"s5"},"s5":{"id":"s5","type":"ACTION_LOG","params":{"message":"电池: ${'$'}{shell_output}"},"next":"s6"},"s6":{"id":"s6","type":"ACTION_SCREENSHOT","params":{"format":"base64","quality":50,"output_var":"img"},"next":"s7"},"s7":{"id":"s7","type":"CONDITION_SWITCH","params":{"expression":"screenshot_success == true"},"branches":{"true":"s8","false":"s9"}},"s8":{"id":"s8","type":"ACTION_TOAST","params":{"message":"✅ Shizuku 测试全部通过"},"next":null},"s9":{"id":"s9","type":"ACTION_TOAST","params":{"message":"⚠️ 截屏失败，请检查 Shizuku"},"next":null}}}
""",
        "demo_network" to """
{"start_node_id":"h1","nodes":{"h1":{"id":"h1","type":"ACTION_HTTP_REQUEST","params":{"url":"https://httpbin.org/get","method":"GET","output_var":"resp","timeout":10000},"next":"h2"},"h2":{"id":"h2","type":"CONDITION_SWITCH","params":{"expression":"http_success == true"},"branches":{"true":"h3","false":"h5"}},"h3":{"id":"h3","type":"ACTION_PARSE_JSON","params":{"json":"${'$'}{resp}","path":"headers.Host","output_var":"host"},"next":"h4"},"h4":{"id":"h4","type":"ACTION_TOAST","params":{"message":"HTTP 请求成功\nHost: ${'$'}{host}"},"next":null},"h5":{"id":"h5","type":"ACTION_TOAST","params":{"message":"HTTP 请求失败"},"next":null}}}
""",
        "demo_ai" to """
{"start_node_id":"a1","nodes":{"a1":{"id":"a1","type":"ACTION_SET_VARIABLE","params":{"name":"q","value":"用一句话解释什么是手机自动化","type":"string"},"next":"a2"},"a2":{"id":"a2","type":"ACTION_LOG","params":{"message":"发送 Prompt: ${'$'}{q}"},"next":"a3"},"a3":{"id":"a3","type":"ACTION_AI_CHAT","params":{"prompt":"${'$'}{q}","output_var":"answer"},"next":"a4"},"a4":{"id":"a4","type":"ACTION_LOG","params":{"message":"AI 回答: ${'$'}{answer}"},"next":"a5"},"a5":{"id":"a5","type":"ACTION_TOAST","params":{"message":"AI: ${'$'}{answer}"}}}}
""",
        "demo_loop" to """
{"start_node_id":"lp0","nodes":{"lp0":{"id":"lp0","type":"ACTION_SET_VARIABLE","params":{"name":"sum","value":0,"type":"int"},"next":"lp1"},"lp1":{"id":"lp1","type":"ACTION_LOOP","params":{"mode":"count","times":5,"counter_var":"i","body_node":"lp2"},"next":"lp5"},"lp2":{"id":"lp2","type":"ACTION_MATH","params":{"operation":"add","a":"${'$'}{sum}","b":"${'$'}{i}","output_var":"sum"},"next":"lp3"},"lp3":{"id":"lp3","type":"ACTION_LOG","params":{"message":"第 ${'$'}{i} 次循环, sum=${'$'}{sum}"},"next":"lp4"},"lp4":{"id":"lp4","type":"ACTION_DELAY","params":{"millis":200},"next":"lp1"},"lp5":{"id":"lp5","type":"ACTION_TOAST","params":{"message":"循环完成! 1~5 累加和 = ${'$'}{sum}"}}}}
""",
        "demo_trigger_notification" to """
{"trigger":{"type":"notification"},"start_node_id":"n1","nodes":{"n1":{"id":"n1","type":"ACTION_LOG","params":{"message":"收到通知: ${'$'}{trigger_package} - ${'$'}{trigger_title}"},"next":"n2"},"n2":{"id":"n2","type":"ACTION_TOAST","params":{"message":"通知: ${'$'}{trigger_title}\n来源: ${'$'}{trigger_package}"},"next":"n3"},"n3":{"id":"n3","type":"ACTION_CLIPBOARD","params":{"action":"set","text":"${'$'}{trigger_text}"}}}}
""",
        "demo_trigger_timer" to """
{"trigger":{"type":"timer","interval_ms":5000,"repeat":true},"start_node_id":"t1","nodes":{"t1":{"id":"t1","type":"ACTION_GET_TIME","params":{"format":"HH:mm:ss","output_var":"now"},"next":"t2"},"t2":{"id":"t2","type":"ACTION_SHELL","params":{"command":"dumpsys battery | grep level","mode":"auto"},"next":"t3"},"t3":{"id":"t3","type":"ACTION_LOG","params":{"message":"[${'$'}{now}] 电池: ${'$'}{shell_output}"},"next":"t4"},"t4":{"id":"t4","type":"ACTION_TOAST","params":{"message":"[${'$'}{now}] 电池: ${'$'}{shell_output}"}}}}
""",
        "demo_trigger_clipboard" to """
{"trigger":{"type":"clipboard"},"start_node_id":"c1","nodes":{"c1":{"id":"c1","type":"ACTION_LOG","params":{"message":"剪贴板变化: ${'$'}{trigger_text}"},"next":"c2"},"c2":{"id":"c2","type":"ACTION_TEXT_OPERATION","params":{"action":"trim","input":"${'$'}{trigger_text}","output_var":"clean"},"next":"c3"},"c3":{"id":"c3","type":"ACTION_TOAST","params":{"message":"剪贴板: ${'$'}{clean}"}}}}
""",
        "demo_interaction" to """
{"start_node_id":"i1","nodes":{"i1":{"id":"i1","type":"ACTION_LAUNCH_APP","params":{"package_name":"com.android.settings"},"next":"i2"},"i2":{"id":"i2","type":"ACTION_DELAY","params":{"millis":1500},"next":"i3"},"i3":{"id":"i3","type":"ACTION_FIND_ELEMENT","params":{"selector":"电池","search_mode":"text","timeout_ms":3000},"next":"i4"},"i4":{"id":"i4","type":"CONDITION_SWITCH","params":{"expression":"element_found == true"},"branches":{"true":"i5","false":"i7"}},"i5":{"id":"i5","type":"ACTION_LOG","params":{"message":"找到元素: ${'$'}{element_text}, bounds=${'$'}{element_bounds}"},"next":"i6"},"i6":{"id":"i6","type":"ACTION_DELAY","params":{"millis":800},"next":"i7"},"i7":{"id":"i7","type":"ACTION_GLOBAL_KEY","params":{"key":"back"},"next":"i8"},"i8":{"id":"i8","type":"ACTION_TOAST","params":{"message":"交互测试完成"}}}}
""",
        "demo_system_status" to """
{"start_node_id":"st1","nodes":{"st1":{"id":"st1","type":"ACTION_WIFI","params":{"action":"status"},"next":"st2"},"st2":{"id":"st2","type":"ACTION_BLUETOOTH","params":{"action":"status"},"next":"st3"},"st3":{"id":"st3","type":"ACTION_NETWORK_STATUS","params":{"action":"all"},"next":"st4"},"st4":{"id":"st4","type":"ACTION_BRIGHTNESS","params":{"action":"get"},"next":"st5"},"st5":{"id":"st5","type":"ACTION_VOLUME","params":{"action":"get","stream":"music"},"next":"st6"},"st6":{"id":"st6","type":"ACTION_LOG","params":{"message":"WiFi=${'$'}{wifi_enabled} BT=${'$'}{bluetooth_enabled} Net=${'$'}{network_type} Brightness=${'$'}{current_brightness} Vol=${'$'}{current_volume}"},"next":"st7"},"st7":{"id":"st7","type":"ACTION_TOAST","params":{"message":"WiFi:${'$'}{wifi_enabled} BT:${'$'}{bluetooth_enabled} Net:${'$'}{network_type}"}}}}
""",
        "demo_file_ops" to """
{"start_node_id":"f1","nodes":{"f1":{"id":"f1","type":"ACTION_GET_TIME","params":{"format":"yyyy-MM-dd HH:mm:ss","output_var":"ts"},"next":"f2"},"f2":{"id":"f2","type":"ACTION_FILE_OPERATION","params":{"action":"write","path":"/sdcard/habitat_demo.txt","content":"Habitat 测试文件\n创建于: ${'$'}{ts}"},"next":"f3"},"f3":{"id":"f3","type":"ACTION_FILE_OPERATION","params":{"action":"read","path":"/sdcard/habitat_demo.txt","output_var":"content"},"next":"f4"},"f4":{"id":"f4","type":"ACTION_LOG","params":{"message":"文件内容: ${'$'}{content}"},"next":"f5"},"f5":{"id":"f5","type":"ACTION_TOAST","params":{"message":"文件已写入: ${'$'}{content}"},"next":"f6"},"f6":{"id":"f6","type":"ACTION_FILE_OPERATION","params":{"action":"delete","path":"/sdcard/habitat_demo.txt"},"next":"f7"},"f7":{"id":"f7","type":"ACTION_TOAST","params":{"message":"清理完成"}}}}
""",
        "demo_utils" to """
{"start_node_id":"u1","nodes":{"u1":{"id":"u1","type":"ACTION_RANDOM","params":{"min":1000,"max":9999,"output_var":"code"},"next":"u2"},"u2":{"id":"u2","type":"ACTION_BASE64","params":{"action":"encode","input":"${'$'}{code}","output_var":"encoded"},"next":"u3"},"u3":{"id":"u3","type":"ACTION_LOG","params":{"message":"随机码: ${'$'}{code} → Base64: ${'$'}{encoded}"},"next":"u4"},"u4":{"id":"u4","type":"ACTION_SEND_NOTIFICATION","params":{"title":"Habitat 工具测试","message":"随机码: ${'$'}{code}"},"next":"u5"},"u5":{"id":"u5","type":"ACTION_TOAST","params":{"message":"随机码 ${'$'}{code} 已发送通知"},"next":"u6"},"u6":{"id":"u6","type":"ACTION_TEXT_TO_SPEECH","params":{"text":"随机码是 ${'$'}{code}","language":"zh","speed":1.0},"next":null}}}
""",
    )

    @Test
    fun everyDefaultWorkflowParsesFromJson() {
        for ((id, json) in defaultJsons) {
            val graph = try {
                HabitatJson.fromJson(json)
            } catch (e: Exception) {
                throw AssertionError("Default workflow '$id' failed JSON parse", e)
            }
            assertNotNull("Default workflow '$id' has null graph", graph)
            assertNotNull("Default workflow '$id' has null nodes", graph.nodes)
            assertFalse("Default workflow '$id' has empty nodes", graph.nodes!!.isEmpty())
            assertFalse("Default workflow '$id' has blank startNodeId", graph.startNodeId.isNullOrBlank())
        }
    }

    @Test
    fun everyDefaultWorkflowPassesStructureValidation() {
        for ((id, json) in defaultJsons) {
            val graph = HabitatJson.fromJson(json)
            val result = WorkflowGraphValidator.validate(graph, registeredTypes)
            assertTrue(
                "Default workflow '$id' has structural errors: ${result.errors.joinToString("; ") { it.message }}",
                result.isValid,
            )
        }
    }

    @Test
    fun everyDefaultWorkflowPassesGraphVerifier() {
        for ((id, json) in defaultJsons) {
            val graph = HabitatJson.fromJson(json)
            val verifyResult = GraphVerifier.verify(graph)
            assertFalse(
                "Default workflow '$id' has verification errors: ${
                    verifyResult.issues.filter { it.level == GraphIssue.Level.ERROR }
                        .joinToString("; ") { "[${it.nodeId}] ${it.message}" }
                }",
                verifyResult.hasError,
            )
            val warnings = verifyResult.issues.filter { it.level == GraphIssue.Level.WARNING }
            assertNotNull(warnings)
        }
    }

    @Test
    fun everyDefaultWorkflowHasStartNodeInNodes() {
        for ((id, json) in defaultJsons) {
            val graph = HabitatJson.fromJson(json)
            val startId = graph.startNodeId!!
            assertTrue(
                "Default workflow '$id' start_node_id '$startId' not in nodes",
                graph.nodes!!.containsKey(startId),
            )
        }
    }
}
