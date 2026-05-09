package com.ailun.habitat.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.ailun.habitat.app.bridge.ShizukuShellExecutor
import com.ailun.habitat.app.HabitatLogger
import com.ailun.habitat.app.habitatColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

class HabitatSettingsActivity : ComponentActivity() {

    private val permissionRefresh = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onResume() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = habitatColorScheme()) {
                HabitatSettingsScreen(
                    onBack = { finish() },
                    onRequestPermission = { intent -> permissionRefresh.launch(intent) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Recomposition triggered via permissionRefresh callback
    }
}

data class PermissionItem(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val isGranted: suspend (Context) -> Boolean,
    val createRequestIntent: (Context) -> Intent?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitatSettingsScreen(
    onBack: () -> Unit,
    onRequestPermission: (Intent) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val permissionItems = remember {
        listOf(
            PermissionItem(
                id = "accessibility",
                name = "无障碍服务",
                description = "用于读取屏幕内容、查找界面元素、模拟点击和手势操作",
                icon = Icons.Default.CheckCircle,
                isGranted = { ctx ->
                    withContext(Dispatchers.IO) {
                        try {
                            val enabledServices = Settings.Secure.getString(
                                ctx.contentResolver,
                                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                            )
                            enabledServices?.contains(ctx.packageName) == true
                        } catch (_: Exception) { false }
                    }
                },
                createRequestIntent = { ctx ->
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                },
            ),
            PermissionItem(
                id = "overlay",
                name = "悬浮窗权限",
                description = "用于在工作流执行时显示悬浮控制面板，支持拖拽和快速操作",
                icon = Icons.Default.CheckCircle,
                isGranted = { ctx -> Settings.canDrawOverlays(ctx) },
                createRequestIntent = { ctx ->
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}"),
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                },
            ),
            PermissionItem(
                id = "shizuku",
                name = "Shizuku 服务",
                description = "提供 Shell 级别的系统命令执行能力（WiFi 控制、屏幕截图等），无需 Root",
                icon = Icons.Default.CheckCircle,
                isGranted = { _ -> Shizuku.pingBinder() },
                createRequestIntent = { ctx ->
                    try {
                        ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                    } catch (_: Exception) { null }
                },
            ),
            PermissionItem(
                id = "notification_listener",
                name = "通知读取权限",
                description = "用于监听系统通知作为工作流的触发器（如收到验证码短信时自动执行）",
                icon = Icons.Default.CheckCircle,
                isGranted = { ctx ->
                    withContext(Dispatchers.IO) {
                        try {
                            val enabledListeners = Settings.Secure.getString(
                                ctx.contentResolver,
                                "enabled_notification_listeners",
                            )
                            enabledListeners?.contains(ctx.packageName) == true
                        } catch (_: Exception) { false }
                    }
                },
                createRequestIntent = { ctx ->
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                },
            ),
            PermissionItem(
                id = "battery",
                name = "电池优化白名单",
                description = "防止系统在后台自动停止工作流执行和悬浮窗服务",
                icon = Icons.Default.CheckCircle,
                isGranted = { ctx ->
                    withContext(Dispatchers.IO) {
                        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                        pm?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false
                    }
                },
                createRequestIntent = { ctx ->
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${ctx.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                },
            ),
            PermissionItem(
                id = "storage",
                name = "所有文件访问权限",
                description = "Android 11+ 需要此权限才能读写工作流文件和导出截图/日志",
                icon = Icons.Default.CheckCircle,
                isGranted = { ctx ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        android.os.Environment.isExternalStorageManager()
                    } else true
                },
                createRequestIntent = { ctx ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${ctx.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    } else null
                },
            ),
        )
    }

    var permissionStates by remember {
        mutableStateOf<Map<String, Boolean>>(emptyMap())
    }

    LaunchedEffect(Unit) {
        permissionStates = permissionItems.associate { item ->
            item.id to item.isGranted(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("权限管理", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Habitat 引擎需要以下权限才能正常工作",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            // Summary card
            item {
                val grantedCount = permissionStates.count { it.value }
                val totalCount = permissionItems.size
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (grantedCount == totalCount)
                            Color(0xFF4CAF50).copy(alpha = 0.12f)
                        else Color(0xFFFF9800).copy(alpha = 0.12f),
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (grantedCount == totalCount)
                                        Color(0xFF4CAF50) else Color(0xFFFF9800)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$grantedCount/$totalCount",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                if (grantedCount == totalCount) "所有权限已就绪"
                                else "还需授权 ${totalCount - grantedCount} 项",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "点击下方项目跳转到系统设置进行授权",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // One-click grant via Shizuku/Root
            item {
                TextButton(
                    onClick = {
                        scope.launch {
                            autoGrantPermissions(context, permissionItems)
                            permissionStates = permissionItems.associate { item ->
                                item.id to item.isGranted(context)
                            }
                            Toast.makeText(context, "已尝试自动授权", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Warning, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("通过 Shell/Shizuku 一键授权")
                }
            }

            // Permission items
            items(permissionItems) { item ->
                val isGranted = permissionStates[item.id] ?: false
                PermissionCard(
                    item = item,
                    isGranted = isGranted,
                    onRequest = {
                        val intent = item.createRequestIntent(context)
                        if (intent != null) {
                            onRequestPermission(intent)
                        } else {
                            Toast.makeText(context, "${item.name}：无法跳转，请手动前往系统设置", Toast.LENGTH_LONG).show()
                        }
                    },
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun PermissionCard(
    item: PermissionItem,
    isGranted: Boolean,
    onRequest: () -> Unit,
) {
    val bgColor by animateColorAsState(
        if (isGranted) Color(0xFF4CAF50).copy(alpha = 0.10f)
        else Color(0xFFFF9800).copy(alpha = 0.10f),
        label = "cardBg",
    )
    val statusColor by animateColorAsState(
        if (isGranted) Color(0xFF4CAF50) else Color(0xFFFF9800),
        label = "statusColor",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!isGranted) onRequest() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusColor.copy(alpha = 0.15f),
                    ) {
                        Text(
                            if (isGranted) "已授权" else "未授权",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isGranted) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "需要授权",
                    tint = statusColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private suspend fun autoGrantPermissions(context: Context, items: List<PermissionItem>) {
    val executor = ShizukuShellExecutor(context)
    withContext(Dispatchers.IO) {
        for (item in items) {
            try {
                when (item.id) {
                    "overlay" -> {
                        executor.exec("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow", true)
                    }
                    "battery" -> {
                        executor.exec("dumpsys deviceidle whitelist +${context.packageName}", true)
                    }
                    "notification_listener" -> {
                        executor.exec("cmd notification allow_listener ${context.packageName}", true)
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                HabitatLogger.w("HabitatSettings", "Auto-grant failed for ${item.id}: ${e.message}")
            }
        }
    }
}
