package com.douya.antidigitaladdiction.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.douya.antidigitaladdiction.MainActivity
import com.douya.antidigitaladdiction.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * 核心监控服务 - 前台服务保活
 * 确保"豆芽"在后台持续运行，记录应用使用情况
 */
@AndroidEntryPoint
class MonitorService : Service() {

    companion object {
        private const val TAG = "DouyaMonitor"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "douya_monitor_channel"
        private const val WAKE_LOCK_TAG = "DouyaMonitor::WakeLock"
        
        // 保活检查间隔（毫秒）
        private const val KEEP_ALIVE_INTERVAL = 60000L // 1分钟
        private const val HEARTBEAT_INTERVAL = 30000L    // 30秒心跳
        
        // 服务状态
        var isRunning = false
            private set
    }

    @Inject
    lateinit var usageAnalyzer: UsageAnalyzer
    
    @Inject
    lateinit var interventionManager: InterventionManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null
    private var heartbeatJob: Job? = null
    private var keepAliveJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "监控服务创建")
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "监控服务启动")
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
        isRunning = true
        
        // 启动监控循环
        startMonitoring()
        
        // 启动心跳
        startHeartbeat()
        
        // 启动保活检查
        startKeepAlive()
        
        // 返回 START_STICKY - 被杀后自动重启
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "监控服务被销毁，准备重启")
        
        isRunning = false
        
        // 取消所有协程
        monitorJob?.cancel()
        heartbeatJob?.cancel()
        keepAliveJob?.cancel()
        serviceScope.cancel()
        
        // 释放唤醒锁
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        
        // 尝试重启服务
        val restartIntent = Intent(applicationContext, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }

    /**
     * 创建通知渠道（Android 8.0+ 必需）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "豆芽监控服务",
                NotificationManager.IMPORTANCE_LOW  // 低重要性，减少打扰
            ).apply {
                description = "保护青少年健康使用手机"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建前台通知
     */
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("豆芽正在保护你")
            .setContentText("监控手机使用情况，防止沉迷")
            .setSmallIcon(R.drawable.ic_notification)  // 需要添加图标资源
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)  // 持续通知，不能滑动删除
            .setContentIntent(pendingIntent)
            .build()
    }

    /**
     * 启动监控循环
     */
    private fun startMonitoring() {
        monitorJob = serviceScope.launch {
            while (isActive) {
                try {
                    // 分析今日使用情况
                    val todayStats = usageAnalyzer.getTodayStats()
                    
                    // 检查沉迷程度
                    val addictionScore = usageAnalyzer.calculateAddictionScore()
                    
                    Log.d(TAG, "今日使用时长: ${todayStats.totalScreenTimeMs / 1000 / 60}分钟, 沉迷指数: $addictionScore")
                    
                    // 根据沉迷程度触发干预
                    when {
                        addictionScore >= 80 -> {
                            Log.w(TAG, "沉迷指数过高(${addictionScore})，触发三级干预")
                            interventionManager.triggerLevel3()
                        }
                        addictionScore >= 60 -> {
                            Log.w(TAG, "沉迷指数较高(${addictionScore})，触发二级干预")
                            interventionManager.triggerLevel2()
                        }
                        addictionScore >= 40 -> {
                            Log.i(TAG, "沉迷指数中等(${addictionScore})，触发一级干预")
                            interventionManager.triggerLevel1()
                        }
                    }
                    
                    // 更新今日统计
                    usageAnalyzer.updateDailyStats()
                    
                } catch (e: Exception) {
                    Log.e(TAG, "监控循环异常", e)
                }
                
                // 每30秒检查一次
                delay(30000)
            }
        }
    }

    /**
     * 启动心跳 - 保持WebSocket/网络连接
     */
    private fun startHeartbeat() {
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                try {
                    // 发送心跳包（如果未来需要服务器通信）
                    // 目前仅用于保持服务活跃
                    Log.v(TAG, "心跳: ${System.currentTimeMillis()}")
                    
                    // 检查无障碍服务是否运行
                    if (!isAccessibilityServiceEnabled()) {
                        Log.w(TAG, "无障碍服务未运行，发送提醒")
                        // 可以在这里发送通知提醒用户开启
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "心跳异常", e)
                }
                
                delay(HEARTBEAT_INTERVAL)
            }
        }
    }

    /**
     * 启动保活机制
     */
    private fun startKeepAlive() {
        keepAliveJob = serviceScope.launch {
            while (isActive) {
                try {
                    // 检查服务是否还在运行
                    if (!isRunning) {
                        Log.w(TAG, "服务状态异常，尝试恢复")
                        // 重新启动服务
                        val intent = Intent(applicationContext, MonitorService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    }
                    
                    // 检查唤醒锁
                    if (wakeLock?.isHeld == false) {
                        Log.w(TAG, "唤醒锁已释放，重新获取")
                        acquireWakeLock()
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "保活检查异常", e)
                }
                
                delay(KEEP_ALIVE_INTERVAL)
            }
        }
    }

    /**
     * 获取唤醒锁 - 防止CPU休眠
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10分钟超时
            }
            Log.i(TAG, "唤醒锁已获取")
        } catch (e: Exception) {
            Log.e(TAG, "获取唤醒锁失败", e)
        }
    }

    /**
     * 检查无障碍服务是否启用
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any { 
            it.resolveInfo.serviceInfo.packageName == packageName &&
            it.resolveInfo.serviceInfo.name == AppAccessibilityService::class.java.name
        }
    }
}
