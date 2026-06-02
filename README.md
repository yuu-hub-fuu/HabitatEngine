# Habitat — JSON 驱动的 Android 自动化工作流引擎

> 轻量、可独立部署、40+ 节点类型的 Android 自动化引擎

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-purple)]()
[![Android](https://img.shields.io/badge/Android-29+-green)]()
[![Nodes](https://img.shields.io/badge/Nodes-40+-blue)]()

---

## 快速开始

### 最简单的工作流

```json
{
  "start_node_id": "hello",
  "nodes": {
    "hello": {
      "id": "hello",
      "type": "ACTION_TOAST",
      "params": { "message": "Hello, Habitat!" }
    }
  }
}
```

### 代码中执行

```kotlin
// Habitat 引擎库：com.ailun.habitat（独立 :habitat Gradle 模块）
// 宿主 App 需要提供桥接实现（IAccessibilityProvider + IShellExecutor）

val factory = NodeHandlerFactory(
    a11y = AppAccessibilityProvider,          // Habitat 自有无障碍桥接
    shell = ShizukuShellExecutor(context),   // Habitat 自有 Shell 执行器
)
val executor = HabitatExecutor(factory)
val graph = HabitatJson.fromJson(jsonString)

executor.execute(graph, context.applicationContext) { log ->
    println(log)
}
```

---

## 节点类型速览 (40+)

### 交互 (6)
| 类型 | 功能 |
|------|------|
| `ACTION_CLICK` | 点击坐标/无障碍元素 |
| `ACTION_SWIPE` | 直线滑动 |
| `ACTION_LONG_PRESS` | 长按 |
| `ACTION_INPUT_TEXT` | 输入文字 |
| `ACTION_GLOBAL_KEY` | 全局按键 (back/home/recents) |
| `ACTION_FIND_ELEMENT` | 查找 UI 元素 |

### 系统控制 (9)
| 类型 | 功能 |
|------|------|
| `ACTION_SHELL` | 执行 Shell 命令 |
| `ACTION_LAUNCH_APP` | 启动应用 |
| `ACTION_FORCE_STOP_APP` | 强制停止应用 |
| `ACTION_SCREEN_WAKE` | 锁屏唤醒/休眠 |
| `ACTION_WIFI` | WiFi 开关 |
| `ACTION_BLUETOOTH` | 蓝牙开关 |
| `ACTION_VOLUME` | 音量控制 |
| `ACTION_BRIGHTNESS` | 亮度控制 |
| `ACTION_FLASHLIGHT` | 手电筒 |

### 数据 (8)
| 类型 | 功能 |
|------|------|
| `ACTION_SET_VARIABLE` | 设置变量 |
| `ACTION_TEXT_OPERATION` | 文本处理 (替换/分割/大小写) |
| `ACTION_MATH` | 数学运算 |
| `ACTION_CLIPBOARD` | 剪贴板读写 |
| `ACTION_PARSE_JSON` | JSON 解析 |
| `ACTION_PARSE_XML` | XML 解析 |
| `ACTION_BASE64` | Base64 编解码 |
| `ACTION_FILE_OPERATION` | 文件读写 |

### 逻辑 (6)
| 类型 | 功能 |
|------|------|
| `CONDITION_SWITCH` | 条件分支 |
| `CONDITION_ADVANCED_SWITCH` | 高级条件 (== / != / > / < / contains) |
| `ACTION_DELAY` | 延迟等待 |
| `ACTION_LOOP` | 循环 |
| `ACTION_TRY_CATCH` | 异常捕获 |
| `ACTION_LOG` | 日志输出 |

### 感知 (5)
| 类型 | 功能 |
|------|------|
| `ACTION_READ_SCREEN` | 无障碍读屏 |
| `ACTION_READ_SMS` | 读取短信/提取验证码 |
| `ACTION_GET_APP_INFO` | 获取前台应用 |
| `ACTION_FOREGROUND_APP` | 前台应用 (增强版) |
| `ACTION_APP_SEARCH` | 搜索已安装应用 |

### 网络 & 通信 (4)
| 类型 | 功能 |
|------|------|
| `ACTION_HTTP_REQUEST` | HTTP 请求 |
| `ACTION_CALL_PHONE` | 拨打电话 |
| `ACTION_SHARE` | 系统分享 |
| `ACTION_SEND_NOTIFICATION` | 发送通知 |

### 媒体 & UI (6)
| 类型 | 功能 |
|------|------|
| `ACTION_SCREENSHOT` | 截屏 (Base64/文件) |
| `ACTION_TEXT_TO_SPEECH` | 文字转语音 |
| `ACTION_TOAST` | Toast 消息 |
| `ACTION_VIBRATE` | 振动 |
| `ACTION_DYNAMIC_ISLAND` | 灵动岛悬浮通知 |
| `ACTION_AI_CHAT` | LLM 推理 |

完整参数参考 → [HABITAT_API.md](HABITAT_API.md)

---

## 工作流示例

### 验证码自动填入
```json
{
  "start_node_id": "read",
  "nodes": {
    "read": {
      "type": "ACTION_READ_SMS",
      "params": { "filter_by": "both", "sender": "验证码", "extract_code": true },
      "next": "check"
    },
    "check": {
      "type": "CONDITION_SWITCH",
      "params": { "expression": "sms_found == true" },
      "branches": { "true": "copy", "false": "fail" }
    },
    "copy": {
      "type": "ACTION_CLIPBOARD",
      "params": { "action": "set", "text": "${verification_code}" },
      "next": "input"
    },
    "input": {
      "type": "ACTION_INPUT_TEXT",
      "params": { "text": "${verification_code}", "mode": "a11y" }
    },
    "fail": {
      "type": "ACTION_TOAST",
      "params": { "message": "未找到验证码" }
    }
  }
}
```

### 一键系统设置
```json
{
  "start_node_id": "step1",
  "nodes": {
    "step1": { "type": "ACTION_WIFI", "params": { "action": "on" }, "next": "step2" },
    "step2": { "type": "ACTION_BRIGHTNESS", "params": { "action": "set", "value": 80 }, "next": "step3" },
    "step3": { "type": "ACTION_VOLUME", "params": { "action": "set", "stream": "music", "value": 40 }, "next": "done" },
    "done": { "type": "ACTION_TOAST", "params": { "message": "已配置完成" } }
  }
}
```

更多示例 → [HabitatJson.kt](HabitatJson.kt) 的常量

---

## 可视化编辑器 & DAG 流程图

### 编辑器布局

Habitat 编辑器采用 Compose 实现，支持三种操作模式：

| 区域 | 功能 |
|------|------|
| 名称 / 描述 | 工作流元数据 |
| JSON 编辑 | 手写 JSON 流程图 |
| **流程图 (DAG)** | 可视化有向图（Canvas 渲染） |

通过「**编辑 JSON** / **查看流程图**」按钮在 JSON 文本编辑和 DAG 可视化之间切换。

### DAG 流程图特性

- **自动布局**：分层拓扑布局算法（Sugiyama 风格），自动排列节点和边
- **节点着色**：按功能类别区分颜色（交互 / 系统 / 数据 / 逻辑 / 网络 / 感知）
- **边类型**：
  - 灰色实线：顺序边 (`next`)
  - 绿色实线：条件 True 分支
  - 红色实线：条件 False 分支
  - 橙色虚线：循环回边
- **触控交互**：
  - 单指拖拽平移
  - 双指缩放（0.3× ~ 3.0×）
  - 双击复原
  - 点击节点可编辑参数
- **视图裁剪**：内容严格约束在容器边界内，不溢出覆盖导航控件
- **刷新机制**：修改 JSON 后点击「刷新流程图」即时更新 DAG

### 新增节点字段

`WorkflowNode` 新增两个可选字段，用于在 DAG 中显示自定义标签：

```json
{
  "start_node_id": "n1",
  "nodes": {
    "n1": {
      "id": "n1",
      "type": "ACTION_TOAST",
      "params": { "message": "Hello" },
      "label": "欢迎提示",
      "description": "向用户显示一条欢迎消息"
    }
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `label` | `String?` | DAG 节点上显示的自定义标签（优先级 > 类型名），如果为空则显示类型名 |
| `description` | `String?` | 节点的详细描述文本，在 DAG 中作为副标题显示 |

两个字段均为可空，旧版 JSON 无此字段时自动为 `null`，完全向前兼容。

### 实现架构

```
HabitatEditorScreen (Compose)
  ├── OutlinedTextField × 3 (名称 / 描述 / JSON)
  ├── 切换按钮 (编辑 JSON ↔ 查看流程图)
  ├── AndroidView → DagView (Canvas 自定义 View)
  │     ├── DagLayoutEngine (自动布局)
  │     └── HabitatDagConverter (WorkflowGraph → DagGraph)
  └── 执行日志 (LazyColumn)
```

相关源码：
- `HabitatDashboard.kt` — 编辑器 UI（Compose）
- `HabitatDagConverter.kt` — 图模型转换
- `dag/DagView.kt` — Canvas DAG 渲染器（缩放/平移/裁剪/点击）
- `dag/DagLayoutEngine.kt` — 分层自动布局算法
- `dag/DagModel.kt` — 通用图渲染数据模型（DagNode / DagEdge / DagGraph）

---

## 本地 AI (LiteRT-LM)

Habitat 内置了基于 Google LiteRT-LM 的本地大模型推理能力，无需联网即可运行 AI 工作流。

### 模型要求

- 模型格式：`.litertlm`（LiteRT-LM 专用格式）
- 推荐模型：Gemma 4 2B（约 2.4 GB）
- 默认路径：`/storage/emulated/0/Download/llm/`
- 需要「所有文件访问权限」才能读取模型文件

### LLM 控制台

在「我的」标签页中打开 LLM 控制台，可以：
- 配置模型文件路径
- 加载 / 重新加载模型
- 手动输入 Prompt 进行推理测试
- 查看模型就绪状态

### ACTION_AI_CHAT 节点

在工作流 JSON 中使用 `ACTION_AI_CHAT` 节点调用本地 AI：

```json
{
  "type": "ACTION_AI_CHAT",
  "params": {
    "prompt": "请分析以下屏幕内容并给出摘要：${screen_content}",
    "output_var": "llm_result"
  },
  "next": "next_node_id"
}
```

| 参数 | 必需 | 说明 |
|------|------|------|
| `prompt` | 是 | 输入提示词，支持 `${var}` 变量插值 |
| `output_var` | 否 | 结果存储变量名，默认 `llm_result` |

**注意**：`ACTION_AI_CHAT` 节点需要在 LLM 控制台中先加载模型才能正常工作。如果模型未加载，节点将跳过执行（静默返回 `next`）。

### 实现架构

```
ILLMService (接口, :lib 模块)
  └── LiteRtLLMServiceImpl (:app)
        └── com.google.ai.edge.litertlm.Engine (LiteRT-LM SDK)
              └── Gemma 4 2B (CPU 后端, Vivo 设备兼容)
```

### 示例：屏幕监控 + AI 摘要

```json
{
  "start_node_id": "loop",
  "nodes": {
    "loop": {
      "type": "ACTION_LOOP",
      "params": { "mode": "count", "times": 10, "body_node": "read_screen" }
    },
    "read_screen": {
      "type": "ACTION_READ_SCREEN",
      "params": {},
      "next": "ai_summary"
    },
    "ai_summary": {
      "type": "ACTION_AI_CHAT",
      "params": {
        "prompt": "用一句话总结屏幕内容：${screen_text}",
        "output_var": "summary"
      },
      "next": "notify"
    },
    "notify": {
      "type": "ACTION_SEND_NOTIFICATION",
      "params": {
        "title": "屏幕摘要",
        "message": "${summary}"
      }
    }
  }
}
```

---

## 项目结构

```
habitat/
  api/                      # 抽象接口（解耦的关键）
    IAccessibilityProvider.kt
    IShellExecutor.kt
  bridge/                   # 桥接实现
    AppAccessibilityProvider.kt
    ShizukuShellExecutor.kt
  ai/                       # LLM 推理
    ILLMService.kt
    LLMServiceProvider.kt
    LiteRtLLMServiceImpl.kt

  # 核心引擎
  WorkflowGraph.kt          # 工作流图数据模型
  WorkflowNode.kt           # 节点数据模型
  WorkflowContext.kt        # 执行上下文（变量、插值、日志）
  HabitatExecutor.kt        # 工作流执行器
  HabitatJson.kt            # JSON 解析 + 示例工作流
  NodeHandlerFactory.kt     # 节点处理器注册表
  INodeHandler.kt           # 节点处理器接口

  # 处理器
  handlers/                 # 40+ 节点处理器（按功能分组）

  # UI
  HabitatDashboard.kt       # Compose 主界面（含编辑器 + DAG 可视化）
  HabitatDagConverter.kt    # WorkflowGraph → DagNode/DagEdge 转换
  FloatWindowManager.kt     # 悬浮球管理器
  FloatWindowUI.kt          # 悬浮球 Compose UI
  HabitatFloatService.kt    # 悬浮球前台服务

  # DAG 可视化（Canvas 渲染）
  dag/DagView.kt            # 自定义 Canvas View（节点/边渲染、缩放、平移）
  dag/DagLayoutEngine.kt    # 分层拓扑布局算法
  dag/DagModel.kt           # 图渲染数据模型 (DagNode / DagEdge / DagGraph)

  # 工具
  HabitatAccessibility.kt   # 无障碍手势工具
  HabitatJson.kt            # JSON 解析
  HabitatWorkflow.kt        # 持久化模型
  WorkflowRepository.kt     # 持久化存储
  ServiceLifecycleOwner.kt  # Compose 生命周期桥接
```

---

## 架构设计

### 模块化结构

Habitat 是一个独立的 Android Library 模块（`:habitat`，命名空间 `com.ailun.habitat`）：

| 模块 | 命名空间 | 内容 |
|------|---------|------|
| `:habitat` | `com.ailun.habitat` | 纯引擎：handlers、executor、models、JSON、API 接口 |
| `:app` | `com.ailun.habitat.app` | 宿主桥接：UI、FloatWindow、bridge 实现 |

### 依赖解耦

Habitat 通过接口层实现了与宿主应用的解耦：

```
INodeHandler  ←  handler 只依赖接口
    ↑
    ├── IAccessibilityProvider  (getService / foregroundPackage / foregroundActivity)
    └── IShellExecutor          (exec)
            ↑
    bridge/ (宿主应用实现，位于 :app 模块)
    ├── AppAccessibilityProvider  ← 自维护无障碍服务引用
    └── ShizukuShellExecutor      ← 桥接 HabitatShellManager
```

**独立部署时**：只需实现 `IAccessibilityProvider` 和 `IShellExecutor` 两个接口，替换 `bridge/` 中的实现即可。

### 执行流程

```
JSON → HabitatJson.fromJson() → WorkflowGraph
     → HabitatExecutor.execute()
       → loop:
         ├─ 获取当前节点
         ├─ NodeHandlerFactory.get(type) → INodeHandler
         ├─ handler.handle(node, context) → nextNodeId
         └─ 继续下一节点
```

### 变量系统

- `${var_name}` — 引用上下文变量，引擎自动插值替换
- `context.variables` — ConcurrentHashMap，线程安全
- `context.interpolate(text)` — 执行时替换 `${var}`

---

## 常见问题

| 问题 | 原因/方案 |
|------|----------|
| 工作流无法启动 | 检查 `start_node_id` 是否存在于 `nodes` 中 |
| 变量为 null | 检查变量名是否已通过 `ACTION_SET_VARIABLE` 设置 |
| 循环无限执行 | 检查循环条件，设置合理的 `max_iterations` |
| 无障碍操作无效 | 确认无障碍服务已开启且在运行 |
| Shell 命令无输出 | 检查 Shizuku/Root 权限状态 |
| WiFi/蓝牙操作无效 | Android 10+ 限制，可能需要系统权限 |

---

## 权限要求

| 功能 | 所需权限 |
|------|---------|
| 点击/滑动/读屏/输入 | 无障碍服务 |
| 读短信 | READ_SMS |
| 发通知 | POST_NOTIFICATIONS |
| 振动 | VIBRATE |
| Shell (系统级) | Shizuku 或 Root |
| 截屏 | 无障碍服务 |
| 手电筒 | CAMERA |
| 拨号 | 无需权限 (用 ACTION_DIAL) |

---

## 扩展 Handler

```kotlin
class MyHandler : INodeHandler {
    override suspend fun handle(node: WorkflowNode, ctx: WorkflowContext): String? {
        val param = node.params?.get("key")?.toString() ?: return node.next
        // 你的逻辑
        ctx.variables["result"] = "done"
        return node.next
    }
}

// 注册
factory.register("MY_ACTION", MyHandler())
```

---

**Habitat** · 40+ 节点 · 独立项目
