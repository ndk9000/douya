package com.douya.antidigitaladdiction.service

import android.app.*
import android.content.*
import android.os.*
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.douya.antidigitaladdiction.MainActivity
import com.douya.antidigitaladdiction.R
import com.douya.antidigitaladdiction.receiver.AlarmReceiver
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 增强版核心监控服务 - 学习 Kimi Claw 的保活机制
 * 
 * 新增特性：
 * 1. 双进程守护（本地 + 远程进程）
 * 2. 多层级保活：前台服务 + AlarmManager + JobScheduler + 双进程
 * 3. 健康检查心跳（类似 Kimi Claw 的 WebSocket 心跳）
 * 4. 死亡后自动重启（START_STICKY + 广播唤醒）
 * 5. 锁屏保活（PARTIAL_WAKE_LOCK）
 */
@Singleton
class EnhancedMonitorService @Inject constructor(
    private val context: Context
) : Service() {

    companion object {
        private const val TAG = "DouyaEnhancedMonitor"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "douya_monitor_channel"
        private const val WAKE_LOCK_TAG = "DouyaMonitor::WakeLock"
        
        // 心跳间隔（学习 Kimi Claw 的 30 秒心跳）
        private const val HEARTBEAT_INTERVAL_MS = 30000L
        private const val HEALTH_CHECK_INTERVAL_MS = 60000L
        private const val KEEP_ALIVE_INTERVAL_MS = 120000L
        
        // 服务状态
        @Volatile
        var isRunning = false
            private set
        
        @Volatile
        var lastHeartbeatTime = 0L
            private set
        
        // 重启次数（防止无限重启）
        private var restartCount = 0
        private const val MAX_RESTART_COUNT = 10
        private const val RESTART_RESET_TIME_MS = 3600000L // 1小时重置
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var heartbeatJob: Job? = null
    private var healthCheckJob: Job? = null
    private var keepAliveJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    // 守护进程连接（双进程守护）
    private var daemonMessenger: Messenger? = null
    private val serviceMessenger = Messenger(IncomingHandler())
    
    private var lastRestartTime = 0L

    /**
     * 处理守护进程消息的 Handler
     */
    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                DaemonService.MSG_HEARTBEAT -> {
                    // 收到守护进程心跳，说明守护进程存活
                    Log.v(TAG, "收到守护进程心跳")
                }
                DaemonService.MSG_REQUEST_RESTART -> {
                    // 守护进程请求重启
                    Log.w(TAG, "守护进程请求重启服务")
                    restartService()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "增强监控服务创建")
        createNotificationChannel()
        acquireWakeLock()
        bindDaemonService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "增强监控服务启动，flags=$flags, startId=$startId")
        
        // 启动前台服务（最高优先级保活）
        startForeground(NOTIFICATION_ID, createNotification())
        isRunning = true
        lastHeartbeatTime = System.currentTimeMillis()
        
        // 启动所有保活机制
        startHeartbeat()
        startHealthCheck()
        startKeepAlive()
        
        // 设置 AlarmManager 定时唤醒（学习 Kimi Claw）
        scheduleNextWakeup()
        
        return START_STICKY  // 被杀后自动重启
    }

    override fun onBind(intent: Intent?): IBinder? = serviceMessenger.binder

    override fun onUnbind(intent: Intent?): Boolean {
        // 重新绑定守护进程
        bindDaemonService()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "增强监控服务被销毁")
        
        isRunning = false
        
        // 取消所有协程
        heartbeatJob?.cancel()
        healthCheckJob?.cancel()
        keepAliveJob?.cancel()
        serviceScope.cancel()
        
        // 释放唤醒锁
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        
        // 解绑守护进程
        daemonMessenger?.let {
            try {
                unbindService(daemonConnection)
            } catch (e: Exception) {
                Log.e(TAG, "解绑守护进程失败", e)
            }
        }
        
        // 尝试重启服务（学习 Kimi Claw 的自动重连）
        if (shouldRestart()) {
            restartService()
        }
    }

    /**
     * 绑定守护进程服务（双进程守护）
     */
    private fun bindDaemonService() {
        try {
            val intent = Intent(context, DaemonService::class.java)
            context.bindService(intent, daemonConnection, Context.BIND_AUTO_CREATE)
            Log.i(TAG, "绑定守护进程")
        } catch (e: Exception) {
            Log.e(TAG, "绑定守护进程失败", e)
        }
    }

    private val daemonConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            daemonMessenger = Messenger(service)
            Log.i(TAG, "守护进程已连接")
            
            // 向守护进程注册
            val msg = Message.obtain(null, DaemonService.MSG_REGISTER_CLIENT)
            msg.replyTo = serviceMessenger
            try {
                daemonMessenger?.send(msg)
            } catch (e: Exception) {
                Log.e(TAG, "向守护进程注册失败", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            daemonMessenger = null
            Log.w(TAG, "守护进程断开，尝试重连")
            
            // 延迟重连（指数退避，学习 Kimi Claw）
            serviceScope.launch {
                delay(5000)
                bindDaemonService()
            }
        }
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "豆芽守护服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保护青少年健康使用手机，防止沉迷"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建前台通知（学习 Kimi Claw 的低打扰设计）
     */
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🌱 豆芽正在守护你")
            .setContentText("监控手机使用，防止沉迷")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)  // 锁屏不显示
            .build()
    }

    /**
     * 心跳机制（学习 Kimi Claw 的 WebSocket 心跳）
     * 即使没有网络，也保持本地心跳，确保服务存活
     */
    private fun startHeartbeat() {
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                try {
                    lastHeartbeatTime = System.currentTimeMillis()
                    
                    // 发送本地心跳（记录到日志和数据库）
                    Log.v(TAG, "心跳: ${formatTime(lastHeartbeatTime)}")
                    
                    // 向守护进程发送心跳
                    daemonMessenger?.let { messenger ->
                        val msg = Message.obtain(null, DaemonService.MSG_HEARTBEAT)
                        try {
                            messenger.send(msg)
                        } catch (e: Exception) {
                            Log.e(TAG, "向守护进程发送心跳失败", e)
                        }
                    }
                    
                    // 更新数据库中的服务状态
                    updateServiceStatus()
                    
                } catch (e: Exception) {
                    Log.e(TAG, "心跳异常", e)
                }
                
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }

    /**
     * 健康检查（类似 Kimi Claw 的连接状态检查）
     */
    private fun startHealthCheck() {
        healthCheckJob = serviceScope.launch {
            while (isActive) {
                try {
                    // 检查无障碍服务状态
                    val accessibilityEnabled = isAccessibilityServiceEnabled()
                    if (!accessibilityEnabled) {
                        Log.w(TAG, "无障碍服务未启用，发送提醒")
                        sendAccessibilityReminderNotification()
                    }
                    
                    // 检查守护进程连接
                    if (daemonMessenger == null) {
                        Log.w(TAG, "守护进程未连接，尝试重连")
                        bindDaemonService()
                    }
                    
                    // 检查唤醒锁
                    if (wakeLock?.isHeld == false) {
                        Log.w(TAG, "唤醒锁已释放，重新获取")
                        acquireWakeLock()
                    }
                    
                    // 检查内存使用（防止OOM）
                    val runtime = Runtime.getRuntime()
                    val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
                    val maxMem = runtime.maxMemory() / 1024 / 1024
                    if (usedMem > maxMem * 0.8) {
                        Log.w(TAG, "内存使用过高: ${usedMem}MB / ${maxMem}MB")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "健康检查异常", e)
                }
                
                delay(HEALTH_CHECK_INTERVAL_MS)
            }
        }
    }

    /**
     * 保活检查（学习 Kimi Claw 的自动重连机制）
     */
    private fun startKeepAlive() {
        keepAliveJob = serviceScope.launch {
            while (isActive) {
                try {
                    // 检查服务是否还在前台
                    if (!isRunning) {
                        Log.w(TAG, "服务状态异常，准备重启")
                        restartService()
                        return@launch
                    }
                    
                    // 检查心跳是否超时（超过2分钟无心跳认为死亡）
                    val timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeatTime
                    if (timeSinceLastHeartbeat > 120000) {
                        Log.e(TAG, "心跳超时 ${timeSinceLastHeartbeat}ms，服务可能卡死")
                        restartService()
                        return@launch
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "保活检查异常", e)
                }
                
                delay(KEEP_ALIVE_INTERVAL_MS)
            }
        }
    }

    /**
     * 设置 AlarmManager 定时唤醒（学习 Kimi Claw 的定时唤醒）
     */
    private fun scheduleNextWakeup() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = "com.douya.action.KEEP_ALIVE"
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            AlarmReceiver.ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val triggerTime = SystemClock.elapsedRealtime() + AlarmReceiver.KEEP_ALIVE_INTERVAL_MS
        
        // 使用 setExactAndAllowWhileIdle 确保Doze模式下也能唤醒
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
        
        Log.d(TAG, "已设置下次唤醒: ${formatTime(triggerTime)}")
    }

    /**
     * 获取唤醒锁
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
                acquire(30 * 60 * 1000L) // 30分钟超时，比Kimi Claw更保守
            }
            Log.i(TAG, "唤醒锁已获取")
        } catch (e: Exception) {
            Log.e(TAG, "获取唤醒锁失败", e)
        }
    }

    /**
     * 是否应该重启服务
     */
    private fun shouldRestart(): Boolean {
        val now = System.currentTimeMillis()
        
        // 重置重启计数
        if (now - lastRestartTime > RESTART_RESET_TIME_MS) {
            restartCount = 0
        }
        
        return restartCount < MAX_RESTART_COUNT
    }

    /**
     * 重启服务
     */
    private fun restartService() {
        restartCount++
        lastRestartTime = System.currentTimeMillis()
        
        Log.w(TAG, "尝试重启服务 (第${restartCount}次)")
        
        val intent = Intent(applicationContext, EnhancedMonitorService::class.java).apply {
            putExtra("from_restart", true)
            putExtra("restart_count", restartCount)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    /**
     * 发送无障碍服务提醒
     */
    private fun sendAccessibilityReminderNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java
        ) ?: return
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle("豆芽需要无障碍权限")
            .setContentText("请点击开启无障碍服务，否则无法监控应用使用")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(2001, notification)
    }

    /**
     * 更新服务状态到数据库
     */
    private fun updateServiceStatus() {
        // TODO: 记录服务心跳到数据库，供家长端查询
    }

    /**
     * 检查无障碍服务是否启用
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) 
            as android.view.accessibility.AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        return enabledServices.any { 
            it.resolveInfo.serviceInfo.packageName == packageName &&
            it.resolveInfo.serviceInfo.name == AppAccessibilityService::class.java.name
        }
    }

    private fun formatTime(timeMs: Long): String {
        return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(timeMs))
    }
}
