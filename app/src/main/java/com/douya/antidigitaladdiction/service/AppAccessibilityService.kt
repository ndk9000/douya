package com.douya.antidigitaladdiction.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.douya.antidigitaladdiction.data.local.AppCategory
import com.douya.antidigitaladdiction.data.local.AppUsageRecord
import com.douya.antidigitaladdiction.di.AppModule
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 核心无障碍服务
 * 监控应用切换，记录使用时长
 * 这是整个"豆芽"应用的核心监控引擎
 */
@AndroidEntryPoint
class AppAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "DouyaAccessibility"
        
        // 已知娱乐应用包名列表（可扩展）
        val ENTERTAINMENT_APPS = setOf(
            "com.ss.android.ugc.aweme",      // 抖音
            "com.smile.gifmaker",            // 快手
            "tv.danmaku.bili",               // B站
            "com.tencent.mm",                // 微信
            "com.tencent.mobileqq",          // QQ
            "com.sina.weibo",                // 微博
            "com.xiaomi.hm.health",          // 小红书
            "com.ss.android.article.news",   // 今日头条
            "com.tencent.qqlive",            // 腾讯视频
            "com.youku.phone",               // 优酷
            "com.qiyi.video",                // 爱奇艺
            "com.netease.cloudmusic",        // 网易云音乐
            "com.kugou.android",             // 酷狗
            "com.tencent.qqmusic",           // QQ音乐
            "com.happyelements.android",     // 开心消消乐
            "com.tencent.tmgp.pubgmhd",      // 和平精英
            "com.tencent.tmgp.sgame",        // 王者荣耀
            "com.miHoYo.Yuanshen",           // 原神
            "com.netease.onmyoji",           // 阴阳师
            "com.supercell.clashofclans",    // 部落冲突
            "com.supercell.clashroyale"      // 皇室战争
        )
        
        // 教育类白名单
        val EDUCATION_APPS = setOf(
            "com.zhihu.android",             // 知乎
            "com.wanmeizhensuo.zhenshuo",    // 真说
            "com.duolingo",                  // 多邻国
            "com.xueqiu.android"             // 雪球
        )
    }

    @Inject
    lateinit var repository: AppModule.UsageRepository
    
    @Inject
    lateinit var interventionManager: InterventionManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var currentPackage: String = ""
    private var currentAppName: String = ""
    private var sessionStartTime: Long = 0
    private var isEntertainment: Boolean = false
    private var appCategory: String = "other"

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "无障碍服务已连接")
        
        // 配置服务参数
        serviceInfo = serviceInfo.apply {
            // 监听窗口状态变化（应用切换）
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            
            // 监听所有应用
            packageNames = null
            
            // 反馈类型
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            
            // 不获取窗口内容（减少性能开销）
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            
            // 设置通知超时
            notificationTimeout = 100
        }
        
        // 启动监控服务
        startService(Intent(this, MonitorService::class.java))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleAppSwitch(event)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍服务被中断")
    }

    /**
     * 处理应用切换事件
     */
    private fun handleAppSwitch(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        
        // 忽略系统应用和自身
        if (isSystemApp(packageName) || packageName == packageName) {
            return
        }
        
        // 如果切换到同一个应用，忽略
        if (packageName == currentPackage) {
            return
        }
        
        // 记录上一个应用的使用时长
        if (currentPackage.isNotEmpty() && sessionStartTime > 0) {
            val duration = System.currentTimeMillis() - sessionStartTime
            if (duration > 1000) { // 忽略小于1秒的切换
                saveUsageRecord(currentPackage, currentAppName, sessionStartTime, duration, isEntertainment, appCategory)
            }
        }
        
        // 开始记录新应用
        currentPackage = packageName
        currentAppName = getAppName(packageName)
        sessionStartTime = System.currentTimeMillis()
        isEntertainment = ENTERTAINMENT_APPS.contains(packageName)
        appCategory = when {
            ENTERTAINMENT_APPS.contains(packageName) -> "entertainment"
            EDUCATION_APPS.contains(packageName) -> "education"
            else -> "other"
        }
        
        Log.d(TAG, "切换到应用: $packageName (${currentAppName}), 娱乐类: $isEntertainment")
        
        // 触发干预检查
        if (isEntertainment) {
            serviceScope.launch {
                interventionManager.checkIntervention(packageName, currentAppName)
            }
        }
    }

    /**
     * 保存使用记录到数据库
     */
    private fun saveUsageRecord(
        packageName: String,
        appName: String,
        startTime: Long,
        duration: Long,
        isEntertainment: Boolean,
        category: String
    ) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = dateFormat.format(Date())
        
        val record = AppUsageRecord(
            packageName = packageName,
            appName = appName,
            date = date,
            startTime = startTime,
            endTime = startTime + duration,
            durationMs = duration,
            isEntertainment = isEntertainment,
            category = category
        )
        
        serviceScope.launch {
            try {
                repository.insertUsageRecord(record)
                Log.d(TAG, "记录保存成功: $packageName, 时长: ${duration / 1000}秒")
            } catch (e: Exception) {
                Log.e(TAG, "记录保存失败", e)
            }
        }
    }

    /**
     * 判断是否为系统应用
     */
    private fun isSystemApp(packageName: String): Boolean {
        return packageName.startsWith("android") ||
               packageName.startsWith("com.android") ||
               packageName.startsWith("com.google.android") ||
               packageName.contains("launcher") ||
               packageName.contains("systemui") ||
               packageName.contains("inputmethod")
    }

    /**
     * 获取应用名称（简化版，实际应查询PackageManager）
     */
    private fun getAppName(packageName: String): String {
        return when (packageName) {
            "com.ss.android.ugc.aweme" -> "抖音"
            "com.smile.gifmaker" -> "快手"
            "tv.danmaku.bili" -> "哔哩哔哩"
            "com.tencent.mm" -> "微信"
            "com.tencent.mobileqq" -> "QQ"
            "com.sina.weibo" -> "微博"
            else -> packageName.substringAfterLast(".")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 保存最后一个应用的使用记录
        if (currentPackage.isNotEmpty() && sessionStartTime > 0) {
            val duration = System.currentTimeMillis() - sessionStartTime
            saveUsageRecord(currentPackage, currentAppName, sessionStartTime, duration, isEntertainment, appCategory)
        }
        Log.i(TAG, "无障碍服务已销毁")
    }
}
