package com.douya.antidigitaladdiction

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.douya.antidigitaladdiction.data.local.DailyStats
import com.douya.antidigitaladdiction.service.EnhancedMonitorService
import com.douya.antidigitaladdiction.ui.theme.DouyaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 豆芽主界面
 * 展示今日使用统计、沉迷指数、应用排行
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 检查必要权限
        checkRequiredPermissions()
        
        setContent {
            DouyaTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNavigateToSettings = { navigateToSettings() },
                    onNavigateToGuardian = { navigateToGuardianSettings() }
                )
            }
        }
    }

    private fun checkRequiredPermissions() {
        // 检查使用情况访问权限
        if (!hasUsageStatsPermission()) {
            Toast.makeText(this, "请开启使用情况访问权限", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        
        // 检查无障碍服务
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "请开启豆芽无障碍服务", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any { 
            it.resolveInfo.serviceInfo.packageName == packageName
        }
    }

    private fun navigateToSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun navigateToGuardianSettings() {
        startActivity(Intent(this, GuardianSettingsActivity::class.java))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToGuardian: () -> Unit
) {
    val todayStats by viewModel.todayStats.collectAsState()
    val appUsageList by viewModel.appUsageList.collectAsState()
    val serviceStatus by viewModel.serviceStatus.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "🌱 豆芽",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToGuardian) {
                        Icon(Icons.Default.Person, contentDescription = "监护人")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 服务状态卡片
            item {
                ServiceStatusCard(
                    isRunning = serviceStatus.isRunning,
                    lastHeartbeat = serviceStatus.lastHeartbeat
                )
            }
            
            // 今日统计概览
            item {
                TodayStatsCard(stats = todayStats)
            }
            
            // 沉迷指数
            item {
                AddictionScoreCard(score = todayStats?.addictionScore ?: 0f)
            }
            
            // 应用使用排行
            item {
                Text(
                    "今日应用排行",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            
            items(appUsageList) { usage ->
                AppUsageItem(
                    appName = usage.appName,
                    usageTime = usage.totalDuration,
                    isEntertainment = usage.isEntertainment
                )
            }
        }
    }
}

@Composable
fun ServiceStatusCard(isRunning: Boolean, lastHeartbeat: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isRunning) Color(0xFF4CAF50) else Color(0xFFF44336),
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    if (isRunning) "监控服务运行中" else "监控服务未运行",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    if (isRunning) "最后心跳: ${formatTime(lastHeartbeat)}" else "请点击开启服务",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun TodayStatsCard(stats: DailyStats?) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "今日使用统计",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "总时长",
                    value = formatDuration(stats?.totalScreenTimeMs ?: 0),
                    icon = Icons.Default.Schedule
                )
                StatItem(
                    label = "娱乐",
                    value = formatDuration(stats?.entertainmentTimeMs ?: 0),
                    icon = Icons.Default.Games,
                    color = Color(0xFFFF9800)
                )
                StatItem(
                    label = "解锁次数",
                    value = "${stats?.unlockCount ?: 0}次",
                    icon = Icons.Default.LockOpen
                )
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color = MaterialTheme.colorScheme.primary) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Text(
            label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun AddictionScoreCard(score: Float) {
    val (color, label, description) = when {
        score < 20 -> Triple(Color(0xFF4CAF50), "健康", "手机使用习惯良好，继续保持！")
        score < 40 -> Triple(Color(0xFF8BC34A), "良好", "使用适中，注意劳逸结合。")
        score < 60 -> Triple(Color(0xFFFFC107), "注意", "使用时间较长，建议适当休息。")
        score < 80 -> Triple(Color(0xFFFF9800), "警告", "沉迷风险较高，需要控制。")
        else -> Triple(Color(0xFFF44336), "严重", "已出现沉迷症状，建议立即干预。")
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 圆形进度指示器
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = score / 100f,
                    modifier = Modifier.fillMaxSize(),
                    color = color,
                    strokeWidth = 8.dp
                )
                Text(
                    "${score.toInt()}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    "沉迷指数: $label",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    description,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun AppUsageItem(appName: String, usageTime: Long, isEntertainment: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isEntertainment) Color(0xFFFFF3E0) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 应用图标占位
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isEntertainment) Icons.Default.Games else Icons.Default.Apps,
                    contentDescription = null,
                    tint = if (isEntertainment) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    appName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (isEntertainment) {
                    Text(
                        "娱乐应用",
                        fontSize = 12.sp,
                        color = Color(0xFFFF9800)
                    )
                }
            }
            
            Text(
                formatDuration(usageTime),
                fontWeight = FontWeight.Bold,
                color = if (isEntertainment) Color(0xFFFF9800) else Color.Unspecified
            )
        }
    }
}

fun formatDuration(ms: Long): String {
    val minutes = ms / 1000 / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    
    return when {
        hours > 0 -> "${hours}小时${remainingMinutes}分"
        minutes > 0 -> "${minutes}分钟"
        else -> "<1分钟"
    }
}

fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return "未知"
    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

// 数据类
data class ServiceStatus(
    val isRunning: Boolean = false,
    val lastHeartbeat: Long = 0L
)

data class AppUsageSummary(
    val appName: String,
    val totalDuration: Long,
    val isEntertainment: Boolean
)