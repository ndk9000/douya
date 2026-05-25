package com.douya.antidigitaladdiction.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.douya.antidigitaladdiction.ui.theme.DouyaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 设置界面
 * 配置监控阈值、白名单、干预级别等
 */
@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DouyaTheme {
                SettingsScreen(
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val scrollState = rememberScrollState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 监控设置
            SettingsSection(title = "监控设置") {
                ThresholdSettingItem(
                    title = "一级干预阈值",
                    description = "使用超过此时长发送提醒通知",
                    defaultValue = 30,
                    unit = "分钟"
                )
                
                ThresholdSettingItem(
                    title = "二级干预阈值",
                    description = "使用超过此时长启动捣蛋模式",
                    defaultValue = 60,
                    unit = "分钟"
                )
                
                ThresholdSettingItem(
                    title = "三级干预阈值",
                    description = "使用超过此时长通知监护人",
                    defaultValue = 120,
                    unit = "分钟"
                )
            }
            
            // 白名单设置
            SettingsSection(title = "应用白名单") {
                Text(
                    "白名单中的应用不会被计入沉迷时间",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                WhitelistAppItem(
                    appName = "学习强国",
                    packageName = "cn.xuexi.android",
                    isWhitelisted = true
                )
                
                WhitelistAppItem(
                    appName = "知乎",
                    packageName = "com.zhihu.android",
                    isWhitelisted = false
                )
                
                WhitelistAppItem(
                    appName = "哔哩哔哩",
                    packageName = "tv.danmaku.bili",
                    isWhitelisted = false
                )
            }
            
            // 睡眠保护
            SettingsSection(title = "睡眠保护") {
                SwitchSettingItem(
                    title = "启用睡眠保护",
                    description = "22:00-06:00 限制娱乐应用",
                    checked = true
                )
                
                TimeRangeSettingItem(
                    title = "睡眠时间段",
                    startTime = "22:00",
                    endTime = "06:00"
                )
            }
            
            // 通知设置
            SettingsSection(title = "通知设置") {
                SwitchSettingItem(
                    title = "一级干预通知",
                    description = "发送温和提醒通知",
                    checked = true
                )
                
                SwitchSettingItem(
                    title = "二级干预通知",
                    description = "捣蛋模式时发送通知",
                    checked = true
                )
                
                SwitchSettingItem(
                    title = "每日报告",
                    description = "每天发送使用报告",
                    checked = true
                )
            }
            
            // 关于
            SettingsSection(title = "关于") {
                AboutItem(
                    title = "版本",
                    value = "1.0.0"
                )
                
                AboutItem(
                    title = "订阅状态",
                    value = "免费版"
                )
                
                Button(
                    onClick = { /* TODO: 升级订阅 */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("升级到高级版")
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun ThresholdSettingItem(
    title: String,
    description: String,
    defaultValue: Int,
    unit: String
) {
    var value by remember { mutableStateOf(defaultValue) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, fontSize = 12.sp, color = Color.Gray)
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (value > 5) value -= 5 }
            ) {
                Icon(Icons.Default.Remove, contentDescription = "减少")
            }
            
            Text(
                "$value $unit",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            
            IconButton(
                onClick = { if (value < 480) value += 5 }
            ) {
                Icon(Icons.Default.Add, contentDescription = "增加")
            }
        }
    }
}

@Composable
fun WhitelistAppItem(
    appName: String,
    packageName: String,
    isWhitelisted: Boolean
) {
    var checked by remember { mutableStateOf(isWhitelisted) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(appName, fontWeight = FontWeight.Bold)
            Text(packageName, fontSize = 12.sp, color = Color.Gray)
        }
        
        Switch(
            checked = checked,
            onCheckedChange = { checked = it }
        )
    }
}

@Composable
fun SwitchSettingItem(
    title: String,
    description: String,
    checked: Boolean
) {
    var isChecked by remember { mutableStateOf(checked) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, fontSize = 12.sp, color = Color.Gray)
        }
        
        Switch(
            checked = isChecked,
            onCheckedChange = { isChecked = it }
        )
    }
}

@Composable
fun TimeRangeSettingItem(
    title: String,
    startTime: String,
    endTime: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Bold)
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                startTime,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Text("-")
            Text(
                endTime,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            
            IconButton(onClick = { /* TODO: 时间选择器 */ }) {
                Icon(Icons.Default.Schedule, contentDescription = "选择时间")
            }
        }
    }
}

@Composable
fun AboutItem(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(value, color = Color.Gray)
    }
}