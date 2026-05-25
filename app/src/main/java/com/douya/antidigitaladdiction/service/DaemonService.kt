package com.douya.antidigitaladdiction.service

import android.app.*
import android.content.*
import android.os.*
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.douya.antidigitaladdiction.R
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 双进程守护服务
 * 运行在独立进程 :daemon 中，负责监控主服务状态并在必要时重启
 * 
 * 学习 Kimi Claw 的"Gateway 端持续连接"思想，本地实现双进程互相守护
 */
@Singleton
class DaemonService @Inject constructor(
    private val context: Context
) : Service() {

    companion object {
        private const val TAG = "DouyaDaemon"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "douya_daemon_channel"
        private const val WAKE_LOCK_TAG = "DouyaDaemon::WakeLock"
        
        // 消息类型
        const val MSG_REGISTER_CLIENT = 1
        const val MSG_HEARTBEAT = 2
        const val MSG_REQUEST_RESTART = 3
        const val MSG_SERVICE_DIED = 4
        
        // 检查间隔
        private const val CHECK_INTERVAL_MS = 30000L  // 30秒检查一次主服务
        private const val HEARTBEAT_TIMEOUT_MS = 120000L // 2分钟无心跳认为死亡
        
        @Volatile
        var isRunning = false
            private set
    }

    private val daemonScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var checkJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    // 主服务连接
    private var mainServiceMessenger: Messenger? = null
    private val daemonMessenger = Messenger(IncomingHandler())
    private var lastMainHeartbeat = 0L

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_REGISTER_CLIENT -> {
                    // 主服务注册
                    mainServiceMessenger = msg.replyTo
                    Log.i(TAG, "主服务已注册")
                }
                MSG_HEARTBEAT -> {
                    // 收到主服务心跳
                    lastMainHeartbeat = System.currentTimeMillis()
                    Log.v(TAG, "收到主服务心跳")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "守护进程创建")
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "守护进程启动")
        
        startForeground(NOTIFICATION_ID, createNotification())
        isRunning = true
        
        // 绑定主服务
        bindMainService()
        
        // 启动监控循环
        startMonitoring()
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = daemonMessenger.binder

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "守护进程被销毁，尝试重启")
        
        isRunning = false
        checkJob?.cancel()
        daemonScope.cancel()
        
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        
        // 尝试重启自己
        val restartIntent = Intent(context, DaemonService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(restartIntent)
        } else {
            context.startService(restartIntent)
        }
    }

    /**
     * 绑定主服务
     */
    private fun bindMainService() {
        try {
            val intent = Intent(context, EnhancedMonitorService::class.java)
            context.bindService(intent, mainServiceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "绑定主服务失败", e)
        }
    }

    private val mainServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            mainServiceMessenger = Messenger(service)
            Log.i(TAG, "主服务已连接")
            
            // 向主服务注册
            val msg = Message.obtain(null, MSG_REGISTER_CLIENT)
            msg.replyTo = daemonMessenger
            try {
                mainServiceMessenger?.send(msg)
            } catch (e: Exception) {
                Log.e(TAG, "向主服务注册失败", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mainServiceMessenger = null
            Log.w(TAG, "主服务断开")
        }
    }

    /**
     * 监控主服务状态
     */
    private fun startMonitoring() {
        checkJob = daemonScope.launch {
            while (isActive) {
                try {
                    val now = System.currentTimeMillis()
                    val timeSinceLastHeartbeat = now - lastMainHeartbeat
                    
                    // 检查主服务是否存活
                    if (lastMainHeartbeat > 0 && timeSinceLastHeartbeat > HEARTBEAT_TIMEOUT_MS) {
                        Log.e(TAG, "主服务心跳超时 ${timeSinceLastHeartbeat}ms，准备重启")
                        restartMainService()
                    }
                    
                    // 发送心跳给主服务
                    mainServiceMessenger?.let { messenger ->
                        val msg = Message.obtain(null, MSG_HEARTBEAT)
                        try {
                            messenger.send(msg)
                        } catch (e: Exception) {
                            Log.e(TAG, "发送心跳给主服务失败", e)
                            // 主服务可能已死，尝试重启
                            restartMainService()
                        }
                    }
                    
                    // 如果主服务未连接，尝试绑定
                    if (mainServiceMessenger == null) {
                        Log.w(TAG, "主服务未连接，尝试绑定")
                        bindMainService()
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "监控循环异常", e)
                }
                
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    /**
     * 重启主服务
     */
    private fun restartMainService() {
        Log.w(TAG, "尝试重启主服务")
        
        // 发送重启请求给主服务（如果还能通信）
        mainServiceMessenger?.let { messenger ->
            val msg = Message.obtain(null, MSG_REQUEST_RESTART)
            try {
                messenger.send(msg)
            } catch (e: Exception) {
                Log.e(TAG, "请求主服务重启失败", e)
            }
        }
        
        // 直接启动主服务
        val intent = Intent(context, EnhancedMonitorService::class.java).apply {
            putExtra("from_daemon", true)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        
        // 重置心跳时间
        lastMainHeartbeat = System.currentTimeMillis()
    }

    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("🌱 豆芽守护进程")
            .setContentText("确保防沉迷服务持续运行")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "豆芽守护进程",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "确保豆芽防沉迷服务持续运行"
                setShowBadge(false)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            ).apply {
                setReferenceCounted(false)
                acquire(30 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取唤醒锁失败", e)
        }
    }
}
