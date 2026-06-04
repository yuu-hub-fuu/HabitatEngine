package com.ailun.habitat

/** 工作流示例 JSON，供 Dashboard 和文档使用。 */
object HabitatSamples {
    const val MVP_THREE_NODE = """
{
  "start_node_id": "node_1",
  "nodes": {
    "node_1": {
      "id": "node_1",
      "type": "ACTION_TOAST",
      "params": { "message": "开始判断环境..." },
      "next": "node_2"
    },
    "node_2": {
      "id": "node_2",
      "type": "CONDITION_SWITCH",
      "params": { "expression": "is_daytime == true" },
      "branches": { "true": "node_3", "false": null }
    },
    "node_3": {
      "id": "node_3",
      "type": "ACTION_VIBRATE",
      "params": { "duration": 300, "amplitude": 128 }
    }
  }
}
"""

    const val SEARCH_FLOW = """
{
  "start_node_id": "s1",
  "nodes": {
    "s1": {
      "id": "s1",
      "type": "ACTION_LAUNCH_APP",
      "params": { "package_name": "com.android.settings" },
      "next": "s2"
    },
    "s2": {
      "id": "s2",
      "type": "ACTION_DELAY",
      "params": { "millis": 1500 },
      "next": "s3"
    },
    "s3": {
      "id": "s3",
      "type": "ACTION_INPUT_TEXT",
      "params": { "text": "WiFi", "mode": "shell" },
      "next": "s4"
    },
    "s4": {
      "id": "s4",
      "type": "ACTION_GLOBAL_KEY",
      "params": { "key": "back" }
    }
  }
}
"""

    const val SCREENSHOT_FLOW = """
{
  "start_node_id": "ts",
  "nodes": {
    "ts": {
      "id": "ts",
      "type": "ACTION_GET_TIME",
      "params": { "format": "yyyyMMdd_HHmmss", "output_var": "timestamp" },
      "next": "log_start"
    },
    "log_start": {
      "id": "log_start",
      "type": "ACTION_LOG",
      "params": { "message": "开始截屏..." },
      "next": "capture"
    },
    "capture": {
      "id": "capture",
      "type": "ACTION_SCREENSHOT",
      "params": { "format": "file", "output_var": "img_path" },
      "next": "check"
    },
    "check": {
      "id": "check",
      "type": "CONDITION_SWITCH",
      "params": { "expression": "screenshot_success == true" },
      "branches": { "true": "log_ok", "false": "fail" }
    },
    "log_ok": {
      "id": "log_ok",
      "type": "ACTION_LOG",
      "params": { "message": "截屏成功: ${'$'}{img_path}" },
      "next": "toast_ok"
    },
    "toast_ok": {
      "id": "toast_ok",
      "type": "ACTION_TOAST",
      "params": { "message": "截屏已保存" }
    },
    "fail": {
      "id": "fail",
      "type": "ACTION_LOG",
      "params": { "message": "截屏失败，请检查 Shizuku" },
      "next": "toast_fail"
    },
    "toast_fail": {
      "id": "toast_fail",
      "type": "ACTION_TOAST",
      "params": { "message": "截屏失败" }
    }
  }
}
"""

    const val SMS_CODE = """
{
  "start_node_id": "read",
  "nodes": {
    "read": {
      "id": "read",
      "type": "ACTION_READ_SMS",
      "params": { "filter_by": "latest", "extract_code": true, "max_scan": 20 },
      "next": "check"
    },
    "check": {
      "id": "check",
      "type": "CONDITION_SWITCH",
      "params": { "expression": "sms_found == true" },
      "branches": { "true": "copy", "false": "fail" }
    },
    "copy": {
      "id": "copy",
      "type": "ACTION_CLIPBOARD",
      "params": { "action": "set", "text": "${'$'}{verification_code}" },
      "next": "toast_ok"
    },
    "toast_ok": {
      "id": "toast_ok",
      "type": "ACTION_TOAST",
      "params": { "message": "验证码 ${'$'}{verification_code} 已复制到剪贴板" }
    },
    "fail": {
      "id": "fail",
      "type": "ACTION_TOAST",
      "params": { "message": "未找到验证码短信" }
    }
  }
}
"""

    const val HTTP_JSON = """
{
  "start_node_id": "h1",
  "nodes": {
    "h1": {
      "id": "h1",
      "type": "ACTION_HTTP_REQUEST",
      "params": { "url": "https://api.github.com/repos/ChaoMixian/Habitat", "method": "GET", "output_var": "api_response" },
      "next": "h2"
    },
    "h2": {
      "id": "h2",
      "type": "ACTION_PARSE_JSON",
      "params": { "json": "${'$'}{api_response}", "path": "stargazers_count", "output_var": "stars" },
      "next": "h3"
    },
    "h3": {
      "id": "h3",
      "type": "ACTION_TOAST",
      "params": { "message": "Habitat 有 ${'$'}{stars} 颗星" }
    }
  }
}
"""

    const val SYSTEM_CONTROL = """
{
  "start_node_id": "c1",
  "nodes": {
    "c1": {
      "id": "c1",
      "type": "ACTION_WIFI",
      "params": { "action": "on" },
      "next": "c2"
    },
    "c2": {
      "id": "c2",
      "type": "ACTION_DELAY",
      "params": { "millis": 2000 },
      "next": "c3"
    },
    "c3": {
      "id": "c3",
      "type": "ACTION_BRIGHTNESS",
      "params": { "action": "set", "value": 120 },
      "next": "c4"
    },
    "c4": {
      "id": "c4",
      "type": "ACTION_VOLUME",
      "params": { "action": "set", "stream": "music", "value": 50 },
      "next": "c5"
    },
    "c5": {
      "id": "c5",
      "type": "ACTION_TOAST",
      "params": { "message": "系统设置已调整" }
    }
  }
}
"""

    const val FILE_OPERATIONS = """
{
  "start_node_id": "f1",
  "nodes": {
    "f1": {
      "id": "f1",
      "type": "ACTION_FILE_OPERATION",
      "params": { "action": "write", "path": "/sdcard/habitat_test.txt", "content": "Habitat 自动化测试" },
      "next": "f2"
    },
    "f2": {
      "id": "f2",
      "type": "ACTION_FILE_OPERATION",
      "params": { "action": "read", "path": "/sdcard/habitat_test.txt", "output_var": "file_content" },
      "next": "f3"
    },
    "f3": {
      "id": "f3",
      "type": "ACTION_TOAST",
      "params": { "message": "文件内容: ${'$'}{file_content}" }
    }
  }
}
"""

    const val APP_CONTEXT = """
{
  "start_node_id": "a1",
  "nodes": {
    "a1": {
      "id": "a1",
      "type": "ACTION_GET_APP_INFO",
      "params": { "output_var": "current_app" },
      "next": "a2"
    },
    "a2": {
      "id": "a2",
      "type": "CONDITION_ADVANCED_SWITCH",
      "params": { "var": "current_app", "operator": "==", "value": "com.tencent.mm" },
      "branches": { "true": "a3", "false": "a4" }
    },
    "a3": {
      "id": "a3",
      "type": "ACTION_TOAST",
      "params": { "message": "当前在微信中" }
    },
    "a4": {
      "id": "a4",
      "type": "ACTION_TOAST",
      "params": { "message": "当前在其他应用" }
    }
  }
}
"""

    const val WECHAT_MONITOR = """
{
  "start_node_id": "init",
  "nodes": {
    "init": {
      "id": "init",
      "type": "ACTION_SET_VARIABLE",
      "params": { "name": "wechat_found", "value": false, "type": "bool" },
      "next": "loop_check"
    },
    "loop_check": {
      "id": "loop_check",
      "type": "CONDITION_SWITCH",
      "params": { "expression": "wechat_found == false" },
      "branches": { "true": "read_screen", "false": "done" }
    },
    "read_screen": {
      "id": "read_screen",
      "type": "ACTION_READ_SCREEN",
      "params": { "keyword": "微信" },
      "next": "check_found"
    },
    "check_found": {
      "id": "check_found",
      "type": "CONDITION_SWITCH",
      "params": { "expression": "screen_data == true" },
      "branches": { "true": "click_wechat", "false": "wait_retry" }
    },
    "click_wechat": {
      "id": "click_wechat",
      "type": "ACTION_CLICK",
      "params": { "target": "微信" },
      "next": "mark_found"
    },
    "mark_found": {
      "id": "mark_found",
      "type": "ACTION_SET_VARIABLE",
      "params": { "name": "wechat_found", "value": true, "type": "bool" },
      "next": "notify"
    },
    "notify": {
      "id": "notify",
      "type": "ACTION_TOAST",
      "params": { "message": "已点击微信" },
      "next": "loop_check"
    },
    "wait_retry": {
      "id": "wait_retry",
      "type": "ACTION_DELAY",
      "params": { "millis": 5000 },
      "next": "loop_check"
    },
    "done": {
      "id": "done",
      "type": "ACTION_TOAST",
      "params": { "message": "监控结束：已找到微信" }
    }
  }
}
"""

    /** 快捷访问：所有示例的列表 (名称 → JSON)。 */
    val all: Map<String, String> = mapOf(
        "MVP 三节点" to MVP_THREE_NODE,
        "搜索流程" to SEARCH_FLOW,
        "截屏流程" to SCREENSHOT_FLOW,
        "验证码提取" to SMS_CODE,
        "HTTP + JSON 解析" to HTTP_JSON,
        "系统控制" to SYSTEM_CONTROL,
        "文件操作" to FILE_OPERATIONS,
        "前台 App 检测" to APP_CONTEXT,
        "微信监控循环" to WECHAT_MONITOR,
    )
}
