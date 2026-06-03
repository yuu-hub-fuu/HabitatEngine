package com.ailun.habitat

import com.ailun.habitat.ai.ILLMService
import com.ailun.habitat.api.IAccessibilityProvider
import com.ailun.habitat.api.IShellExecutor
import com.ailun.habitat.confirmation.ConfirmationManager
import com.ailun.habitat.expression.ExpressionEngine
import com.ailun.habitat.handlers.*

import java.util.concurrent.ConcurrentHashMap

class NodeHandlerFactory(
    private val a11y: IAccessibilityProvider? = null,
    private val shell: IShellExecutor? = null,
    confirmationManager: ConfirmationManager? = null,
    private val llmService: ILLMService? = null,
) {

    private val registry = ConcurrentHashMap<String, INodeHandler>()

    /** Shared expression engine used by switch and loop handlers. */
    val expressionEngine = ExpressionEngine()

    /** Confirmation manager for ACTION_CONFIRM. Can be set after construction. */
    var confirmationManager: ConfirmationManager? = confirmationManager
        private set

    init {
        // ── 逻辑控制 ──
        register(CONDITION_SWITCH, SwitchNodeHandler())
        register(CONDITION_ADVANCED_SWITCH, NodeAdvancedSwitchHandler())
        register(ACTION_DELAY, NodeDelayHandler())
        register(ACTION_LOOP, NodeLoopHandler())
        register(ACTION_TRY_CATCH, NodeTryCatchHandler())
        register(ACTION_SWITCH, NodeSwitchHandler())
        register(ACTION_REGEX, NodeRegexHandler())
        register(ACTION_WAIT_FOR, NodeWaitForHandler(a11y))
        register(ACTION_FOR_EACH, NodeLoopHandler())
        register(ACTION_LOG, NodeLogHandler())

        // ── 变量与数据 ──
        register(ACTION_SET_VARIABLE, NodeSetVariableHandler())
        register(ACTION_TEXT_OPERATION, NodeTextOperationHandler())
        register(ACTION_MATH, NodeMathHandler())
        register(ACTION_CLIPBOARD, NodeClipboardHandler())
        register(ACTION_PARSE_JSON, NodeParseJsonHandler())
        register(ACTION_PARSE_XML, NodeParseXmlHandler())
        register(ACTION_BASE64, NodeBase64Handler())
        register(ACTION_FILE_OPERATION, NodeFileOperationHandler())

        // ── 交互 ──
        register(ACTION_CLICK, NodeClickHandler(a11y))
        register(ACTION_SWIPE, NodeSwipeHandler(a11y))
        register(ACTION_LONG_PRESS, NodeLongPressHandler(a11y, shell))
        register(ACTION_INPUT_TEXT, NodeInputTextHandler(a11y, shell))
        register(ACTION_GLOBAL_KEY, NodeGlobalKeyHandler(a11y, shell))
        register(ACTION_FIND_ELEMENT, NodeFindElementHandler(a11y, shell))

        // ── 系统控制 ──
        register(ACTION_SHELL, NodeShellHandler(shell))
        register(ACTION_LAUNCH_APP, NodeLaunchAppHandler())
        register(ACTION_FORCE_STOP_APP, NodeForceStopAppHandler(shellExecutor = shell))
        register(ACTION_SCREEN_WAKE, NodeScreenWakeHandler(shellExecutor = shell))
        register(ACTION_WIFI, NodeWifiHandler())
        register(ACTION_BLUETOOTH, NodeBluetoothHandler())
        register(ACTION_VOLUME, NodeVolumeHandler())
        register(ACTION_BRIGHTNESS, NodeBrightnessHandler())
        register(ACTION_FLASHLIGHT, NodeFlashlightHandler())
        register(ACTION_CALL_PHONE, NodeCallHandler())
        register(ACTION_SHARE, NodeShareHandler())

        // ── 媒体 ──
        register(ACTION_SCREENSHOT, NodeScreenshotHandler(shell))
        register(ACTION_TEXT_TO_SPEECH, NodeTtsHandler())

        // ── 网络 ──
        register(ACTION_HTTP_REQUEST, NodeHttpHandler())
        register(ACTION_NETWORK_STATUS, NodeNetworkStatusHandler())

        // ── 工具 ──
        register(ACTION_GET_TIME, NodeGetTimeHandler())
        register(ACTION_RANDOM, NodeRandomHandler())

        // ── 感知 ──
        register(ACTION_READ_SCREEN, NodeReadScreenHandler(a11y))
        register(ACTION_READ_SMS, NodeReadSmsHandler(shell))
        register(ACTION_GET_APP_INFO, NodeAppInfoHandler(a11y))
        register(ACTION_FOREGROUND_APP, NodeForegroundAppHandler(a11y, shell))
        register(ACTION_APP_SEARCH, NodeAppSearchHandler())

        // ── UI ──
        register(ACTION_TOAST, ToastNodeHandler())
        register(ACTION_VIBRATE, VibrateNodeHandler())

        // ── 安全 ──
        register(ACTION_CONFIRM, NodeConfirmHandler())

        // ── 技能 ──
        register(ACTION_CALL_SKILL, NodeCallSkillHandler(subGraphExecutor = { graph, ctx ->
            HabitatExecutor(this).execute(graph, ctx).join()
        }))

        // ── AI ──
        register(ACTION_AI_CHAT, NodeLLMHandler(llmService = llmService))
    }

    fun register(type: String, handler: INodeHandler) { registry[type] = handler }
    fun get(type: String?): INodeHandler? = type?.let { registry[it] }
    fun registeredTypes(): Set<String> = registry.keys.toSet()

    companion object {
        const val CONDITION_SWITCH = "CONDITION_SWITCH"
        const val CONDITION_ADVANCED_SWITCH = "CONDITION_ADVANCED_SWITCH"
        const val ACTION_DELAY = "ACTION_DELAY"
        const val ACTION_LOOP = "ACTION_LOOP"
        const val ACTION_TRY_CATCH = "ACTION_TRY_CATCH"
        const val ACTION_LOG = "ACTION_LOG"
        const val ACTION_SET_VARIABLE = "ACTION_SET_VARIABLE"
        const val ACTION_TEXT_OPERATION = "ACTION_TEXT_OPERATION"
        const val ACTION_MATH = "ACTION_MATH"
        const val ACTION_CLIPBOARD = "ACTION_CLIPBOARD"
        const val ACTION_PARSE_JSON = "ACTION_PARSE_JSON"
        const val ACTION_PARSE_XML = "ACTION_PARSE_XML"
        const val ACTION_BASE64 = "ACTION_BASE64"
        const val ACTION_FILE_OPERATION = "ACTION_FILE_OPERATION"
        const val ACTION_CLICK = "ACTION_CLICK"
        const val ACTION_SWIPE = "ACTION_SWIPE"
        const val ACTION_LONG_PRESS = "ACTION_LONG_PRESS"
        const val ACTION_INPUT_TEXT = "ACTION_INPUT_TEXT"
        const val ACTION_GLOBAL_KEY = "ACTION_GLOBAL_KEY"
        const val ACTION_FIND_ELEMENT = "ACTION_FIND_ELEMENT"
        const val ACTION_SHELL = "ACTION_SHELL"
        const val ACTION_LAUNCH_APP = "ACTION_LAUNCH_APP"
        const val ACTION_FORCE_STOP_APP = "ACTION_FORCE_STOP_APP"
        const val ACTION_SCREEN_WAKE = "ACTION_SCREEN_WAKE"
        const val ACTION_WIFI = "ACTION_WIFI"
        const val ACTION_BLUETOOTH = "ACTION_BLUETOOTH"
        const val ACTION_VOLUME = "ACTION_VOLUME"
        const val ACTION_BRIGHTNESS = "ACTION_BRIGHTNESS"
        const val ACTION_FLASHLIGHT = "ACTION_FLASHLIGHT"
        const val ACTION_CALL_PHONE = "ACTION_CALL_PHONE"
        const val ACTION_SHARE = "ACTION_SHARE"
        const val ACTION_SCREENSHOT = "ACTION_SCREENSHOT"
        const val ACTION_TEXT_TO_SPEECH = "ACTION_TEXT_TO_SPEECH"
        const val ACTION_HTTP_REQUEST = "ACTION_HTTP_REQUEST"
        const val ACTION_NETWORK_STATUS = "ACTION_NETWORK_STATUS"
        const val ACTION_GET_TIME = "ACTION_GET_TIME"
        const val ACTION_RANDOM = "ACTION_RANDOM"
        const val ACTION_READ_SCREEN = "ACTION_READ_SCREEN"
        const val ACTION_READ_SMS = "ACTION_READ_SMS"
        const val ACTION_GET_APP_INFO = "ACTION_GET_APP_INFO"
        const val ACTION_FOREGROUND_APP = "ACTION_FOREGROUND_APP"
        const val ACTION_APP_SEARCH = "ACTION_APP_SEARCH"
        const val ACTION_TOAST = "ACTION_TOAST"
        const val ACTION_VIBRATE = "ACTION_VIBRATE"
        const val ACTION_SEND_NOTIFICATION = "ACTION_SEND_NOTIFICATION"
        const val ACTION_DYNAMIC_ISLAND = "ACTION_DYNAMIC_ISLAND"
        const val ACTION_CONFIRM = "ACTION_CONFIRM"
        const val ACTION_AI_CHAT = "ACTION_AI_CHAT"
        const val ACTION_CALL_SKILL = "ACTION_CALL_SKILL"
        const val ACTION_REGEX = "ACTION_REGEX"
        const val ACTION_WAIT_FOR = "ACTION_WAIT_FOR"
        const val ACTION_SWITCH = "ACTION_SWITCH"
        const val ACTION_FOR_EACH = "ACTION_FOR_EACH"
    }
}
