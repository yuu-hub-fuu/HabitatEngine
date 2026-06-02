package com.ailun.habitat.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ailun.habitat.R
import kotlinx.coroutines.launch

// ── Habitat brand palette ──
private val Teal600 = Color(0xFF0D9488)
private val Teal700 = Color(0xFF0F766E)
private val Teal50 = Color(0xFFF0FDFA)
private val Amber500 = Color(0xFFF59E0B)
private val Amber50 = Color(0xFFFFFBEB)
private val Slate50 = Color(0xFFF8FAFC)
private val Slate100 = Color(0xFFF1F5F9)
private val Slate600 = Color(0xFF475569)
private val Slate800 = Color(0xFF1E293B)

class HabitatOnboardingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = habitatColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    OnboardingScreen(onFinish = { completeOnboarding() })
                }
            }
        }
    }

    private fun completeOnboarding() {
        val prefs = getSharedPreferences(HabitatMainActivity.PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean("is_first_run", false).apply()
        startActivity(Intent(this, HabitatMainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}

// ── Onboarding Screen ──

@Composable
private fun OnboardingScreen(onFinish: () -> Unit) {
    var currentPage by remember { mutableIntStateOf(0) }
    val totalPages = 4

    Column(modifier = Modifier.fillMaxSize()) {
        StepProgressBar(currentPage = currentPage, totalSteps = totalPages)

        AnimatedContent(
            targetState = currentPage,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(animationSpec = tween(400), initialOffsetX = { dir * it / 4 }) + fadeIn(tween(300)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(400), targetOffsetX = { -dir * it / 4 }) + fadeOut(tween(200)))
            },
            label = "page"
        ) { page ->
            when (page) {
                0 -> WelcomePage(onNext = { currentPage = 1 })
                1 -> ShellSetupPage(onNext = { currentPage = 2 })
                2 -> PermissionsSetupPage(onNext = { currentPage = 3 })
                3 -> CompletionPage(onFinish = onFinish)
            }
        }

        if (currentPage < 3) {
            BottomNavBar(currentPage = currentPage, onNext = { currentPage++ }, onSkip = { currentPage = 3 })
        }
    }
}

// ── Step Progress ──

@Composable
private fun StepProgressBar(currentPage: Int, totalSteps: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(totalSteps) { i ->
                Box(
                    modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(when { i == currentPage -> Teal600; i < currentPage -> Teal600.copy(alpha = 0.5f); else -> Slate100 })
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("${currentPage + 1}/$totalSteps", style = MaterialTheme.typography.labelSmall, color = Slate600)
    }
}

// ── Welcome ──

@Composable
private fun WelcomePage(onNext: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "p").animateFloat(1f, 1.06f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "s")

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Spacer(Modifier.weight(0.6f))
        Box(modifier = Modifier.size(130.dp).scale(pulse).clip(CircleShape).background(Brush.radialGradient(listOf(Teal700, Teal600, Color(0xFF14B8A6)))), contentAlignment = Alignment.Center) {
            Text("H", fontSize = 52.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(40.dp))
        Text("欢迎来到 Habitat", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Slate800)
        Spacer(Modifier.height(12.dp))
        Text("智能自动化脚本引擎 — 轻松编排你的手机操作流程", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = Slate600)
        Spacer(Modifier.weight(0.4f))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            FeatureBadge(Icons.Rounded.FlashOn, "自动化")
            FeatureBadge(Icons.Rounded.TouchApp, "模拟操作")
            FeatureBadge(Icons.Rounded.Security, "系统级")
        }
        Spacer(Modifier.height(48.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Teal600)) {
            Text("开始配置", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun FeatureBadge(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Teal50), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Teal600, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Slate600)
    }
}

// ── Shell Setup ──

@Composable
private fun ShellSetupPage(onNext: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(HabitatMainActivity.PREFS_NAME, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var selectedMode by remember { mutableStateOf("none") }
    var isVerified by remember { mutableStateOf(false) }
    var autoEnableAcc by remember { mutableStateOf(false) }
    var forceKeepAlive by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Icon(Icons.Rounded.Terminal, null, modifier = Modifier.size(52.dp), tint = Teal600)
        Spacer(Modifier.height(12.dp))
        Text("选择 Shell 模式", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Slate800)
        Text("Habitat 可通过 Shizuku 或 Root 执行系统级操作", style = MaterialTheme.typography.bodyMedium, color = Slate600, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))

        ShellCard(Icons.Rounded.IntegrationInstructions, "Shizuku（推荐）", "安全高效，通过 Shizuku API 授权", selectedMode == "shizuku", Teal600) { selectedMode = "shizuku"; isVerified = false }
        Spacer(Modifier.height(10.dp))
        ShellCard(Icons.Rounded.Build, "Root 权限", "完整的系统访问能力", selectedMode == "root", Amber500) { selectedMode = "root"; isVerified = false }
        Spacer(Modifier.height(10.dp))
        ShellCard(Icons.Rounded.Block, "暂不配置", "仅使用无障碍服务", selectedMode == "none", Slate600) { selectedMode = "none"; isVerified = true }

        Spacer(Modifier.height(20.dp))
        AnimatedVisibility(visible = selectedMode != "none" && !isVerified, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            OutlinedButton(onClick = {
                if (selectedMode == "shizuku") {
                    if (HabitatShellManager.isShizukuActive()) isVerified = true
                    else Toast.makeText(context, "Shizuku 未运行或未授权", Toast.LENGTH_SHORT).show()
                } else if (selectedMode == "root") {
                    if (HabitatShellManager.isRootAvailable()) isVerified = true
                    else Toast.makeText(context, "未检测到 Root 权限", Toast.LENGTH_SHORT).show()
                }
            }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Rounded.VerifiedUser, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("验证连接")
            }
        }

        AnimatedVisibility(visible = isVerified && selectedMode != "none", enter = expandVertically(tween(400)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Teal50)) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null, tint = Teal600); Spacer(Modifier.width(8.dp)); Text("连接验证成功", fontWeight = FontWeight.SemiBold, color = Teal700) }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Teal600.copy(alpha = 0.15f))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { autoEnableAcc = !autoEnableAcc }.padding(vertical = 2.dp)) {
                        Checkbox(checked = autoEnableAcc, onCheckedChange = { autoEnableAcc = it }, colors = CheckboxDefaults.colors(checkedColor = Teal600)); Spacer(Modifier.width(4.dp)); Text("自动维持无障碍服务开启", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (selectedMode == "shizuku") {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { forceKeepAlive = !forceKeepAlive }.padding(vertical = 2.dp)) {
                            Checkbox(checked = forceKeepAlive, onCheckedChange = { forceKeepAlive = it }, colors = CheckboxDefaults.colors(checkedColor = Teal600)); Spacer(Modifier.width(4.dp)); Text("保持后台服务存活", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        val canProceed = selectedMode == "none" || isVerified
        Button(onClick = {
            prefs.edit().putString("default_shell_mode", selectedMode).putBoolean("autoEnableAccessibility", autoEnableAcc).putBoolean("forceKeepAliveEnabled", forceKeepAlive).apply()
            onNext()
        }, enabled = canProceed, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = if (canProceed) Teal600 else Slate100)) {
            Text(if (selectedMode == "none") "跳过，继续下一步" else "保存并继续", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ShellCard(icon: ImageVector, title: String, subtitle: String, isSelected: Boolean, accentColor: Color, onClick: () -> Unit) {
    val borderColor by animateColorAsState(if (isSelected) accentColor else Color.Transparent, label = "b")
    val bgColor by animateColorAsState(if (isSelected) accentColor.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface, label = "bg")
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = bgColor), border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) borderColor else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accentColor.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Slate600) }
            RadioButton(selected = isSelected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = accentColor))
        }
    }
}

// ── Permissions ──

private data class OnboardingPerm(
    val id: String, val name: String, val desc: String,
    val isGranted: (Context) -> Boolean,
    val requestIntent: (Context) -> Intent?
)

@Composable
private fun PermissionsSetupPage(onNext: () -> Unit) {
    val context = LocalContext.current
    val permissions = remember {
        listOf(
            OnboardingPerm("notifications", "通知权限", "监听系统通知作为工作流触发器",
                isGranted = { ctx -> try { Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")?.contains(ctx.packageName) == true } catch (_: Exception) { false } },
                requestIntent = { ctx -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } }
            ),
            OnboardingPerm("battery", "电池优化白名单", "防止系统在后台停止工作流执行",
                isGranted = { ctx -> (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isIgnoringBatteryOptimizations(ctx.packageName) ?: false },
                requestIntent = { ctx -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${ctx.packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } }
            ),
            OnboardingPerm("overlay", "悬浮窗权限", "显示悬浮控制面板",
                isGranted = { ctx -> Settings.canDrawOverlays(ctx) },
                requestIntent = { ctx -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } }
            )
        )
    }

    var grantedMap by remember { mutableStateOf(permissions.associate { it.id to it.isGranted(context) }) }
    var clickCount by remember { mutableIntStateOf(0) }; var lastClick by remember { mutableLongStateOf(0L) }
    val allGranted = grantedMap.all { it.value }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        grantedMap = permissions.associate { it.id to it.isGranted(context) }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Amber50).clickable {
            val now = System.currentTimeMillis(); clickCount = if (now - lastClick > 2000) 1 else clickCount + 1; lastClick = now; if (clickCount >= 5) onNext()
        }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Shield, null, tint = Amber500, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(12.dp))
        Text("需要授权", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Slate800)
        Text("以下权限是 Habitat 正常运行所必需的", style = MaterialTheme.typography.bodyMedium, color = Slate600, textAlign = TextAlign.Center)
        if (allGranted) { Surface(shape = RoundedCornerShape(8.dp), color = Teal50) { Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { Icon(Icons.Default.CheckCircle, null, tint = Teal600, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("全部已授权", style = MaterialTheme.typography.labelMedium, color = Teal700) } } }
        Spacer(Modifier.height(28.dp))

        permissions.forEach { perm ->
            val granted = grantedMap[perm.id] ?: false
            val statusColor by animateColorAsState(if (granted) Teal600 else Amber500, label = "sc")
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (granted) Teal50.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface), border = if (!granted) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) { Text(perm.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold); Text(perm.desc, style = MaterialTheme.typography.bodySmall, color = Slate600) }
                    if (!granted) {
                        FilledTonalButton(onClick = { perm.requestIntent(context)?.let { launcher.launch(it) } }, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp), modifier = Modifier.height(36.dp), shape = RoundedCornerShape(10.dp)) { Text("去授权", fontSize = 13.sp, color = statusColor, fontWeight = FontWeight.SemiBold) }
                    } else { Icon(Icons.Default.CheckCircle, null, tint = Teal600, modifier = Modifier.size(22.dp)) }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(onClick = onNext, enabled = allGranted, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = if (allGranted) Teal600 else Slate100)) {
            if (allGranted) { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("全部完成，继续", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            else Text("请先完成所有授权", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ── Completion ──

@Composable
private fun CompletionPage(onFinish: () -> Unit) {
    val scaleAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { scaleAnim.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }

    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(120.dp).scale(scaleAnim.value).clip(CircleShape).background(Brush.radialGradient(listOf(Teal600, Teal700))), contentAlignment = Alignment.Center) { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(56.dp)) }
        Spacer(Modifier.height(36.dp))
        Text("引擎就绪！", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = Slate800)
        Spacer(Modifier.height(14.dp))
        Text("所有配置已完成。\n现在就开始探索吧。", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = Slate600, lineHeight = 24.sp)
        Spacer(Modifier.height(56.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Teal600)) {
            Text("进入 Habitat", fontSize = 18.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(8.dp)); Icon(Icons.Default.KeyboardArrowRight, null, modifier = Modifier.size(22.dp))
        }
    }
}

// ── Bottom nav ──

@Composable
private fun BottomNavBar(currentPage: Int, onNext: () -> Unit, onSkip: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onSkip) { Text("跳过", color = Slate600) }
            Button(onClick = onNext, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Teal600)) {
                Text("继续", fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(6.dp)); Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(18.dp))
            }
        }
    }
}
