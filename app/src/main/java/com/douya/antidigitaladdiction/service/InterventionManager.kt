package com.douya.antidigitaladdiction.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.douya.antidigitaladdiction.MainActivity
import com.douya.antidigitaladdiction.R
import com.douya.antidigitaladdiction.data.local.AppConfigDao
import com.douya.antidigitaladdiction.data.local.DailyStatsDao
import com.douya.antidigitaladdiction.data.local.GuardianDao
import com.douya.antidigitaladdiction.data.local.GuardianInfo
import com.douya.antidigitaladdiction.data.local.SubscriptionDao
import com.douya.antidigitaladdiction.data.local.SubscriptionTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 干预管理器 - 三级干预系统的核心
 * 
 * 第一阶段：通知提醒（温和）
 * 第二阶段：捣蛋模式（降低娱乐感）
 * 第三阶段：监护人介入（通知家长）
 */
@Singleton
class InterventionManager @Inject constructor(
    private val context: Context,
    private val appConfigDao: AppConfigDao,
    private val dailyStatsDao: DailyStatsDao,
    private val guardianDao: GuardianDao,
    private val subscriptionDao: SubscriptionDao
) {

    companion object {
        private const val TAG = "DouyaIntervention"
        
        // 通知渠道ID
        private const val CHANNEL_LEVEL1 = "douya_level1"
        private const val CHANNEL_LEVEL2 = "douya_level2"
        private const val CHANNEL_LEVEL3 = "douya_level3"
        
        // 通知ID
        private const val NOTIF_LEVEL1_BASE = 2001
        private const val NOTIF_LEVEL2_BASE = 3001
        private const val NOTIF_LEVEL3_BASE = 4001
        
        // 干预阈值（分钟）
        private const val LEVEL1_THRESHOLD = 30   // 30分钟触发一级
        private const val LEVEL2_THRESHOLD = 60   // 60分钟触发二级
        private const val LEVEL3_THRESHOLD = 120  // 120分钟触发三级
        
        // 捣蛋模式参数
        private const val DIM_MIN_BRIGHTNESS = 30  // 最低亮度百分比
        private const val OVERLAY_DURATION_MS = 5000L  // 遮罩显示时长
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var currentLevel = 0  // 当前干预级别
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private var originalBrightness = -1

    init {
        createNotificationChannels()
    }

    /**
     * 检查是否需要干预
     */
    suspend fun checkIntervention(packageName: String, appName: String) {
        val config = appConfigDao.getConfig(packageName) ?: return
        
        // 白名单应用不干预
        if (config.isWhitelisted) return
        
        // 获取今日使用时长
        val todayUsage = getTodayUsageMinutes(packageName)
        val limit = config.dailyLimitMinutes
        
        Log.d(TAG, "检查干预: $appName, 已使用 ${todayUsage}分钟, 限制 $limit 分钟")
        
        when {
            todayUsage >= LEVEL3_THRESHOLD -> {
                if (currentLevel < 3) {
                    triggerLevel3()
                }
            }
            todayUsage >= LEVEL2_THRESHOLD -> {
                if (currentLevel < 2) {
                    triggerLevel2()
                }
            }
            todayUsage >= LEVEL1_THRESHOLD -> {
                if (currentLevel < 1) {
                    triggerLevel1(appName, todayUsage)
                }
            }
        }
    }

    /**
     * 第一阶段干预：温和通知提醒
     */
    suspend fun triggerLevel1(appName: String = "", usageMinutes: Int = 0) {
        Log.i(TAG, "触发一级干预 - 通知提醒")
        currentLevel = 1
        
        // 更新数据库统计
        updateInterventionCount(1)
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        
        val title = when {
            usageMinutes > 0 -> "已使用 $appName ${usageMinutes}分钟了"
            else -> "该休息一下了"
        }
        
        val message = when {
            usageMinutes >= 60 -> "你已经使用很久了，站起来活动一下吧！"
            usageMinutes >= 45 -> "时间过得真快，喝杯水，看看远方。"
            else -> "适度娱乐，健康生活。你已经使用一段时间了。"
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_LEVEL1)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_check,
                "我知道了",
                PendingIntent.getBroadcast(
                    context, 0,
                    Intent("com.douya.ACTION_ACKNOWLEDGE"),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        
        notificationManager.notify(NOTIF_LEVEL1_BASE, notification)
        
        // 轻微震动提醒
        vibrate(200)
    }

    /**
     * 第二阶段干预：捣蛋模式
     * 降低娱乐感，但不完全阻断
     */
    suspend fun triggerLevel2() {
        Log.w(TAG, "触发二级干预 - 捣蛋模式")
        currentLevel = 2
        
        // 更新数据库统计
        updateInterventionCount(2)
        
        // 检查订阅等级（二级干预需要付费）
        val subscription = subscriptionDao.getSubscription()
        if (subscription?.tier == SubscriptionTier.FREE) {
            Log.i(TAG, "免费用户，二级干预降级为一级")
            triggerLevel1()
            return
        }
        
        // 1. 发送更强通知
        sendLevel2Notification()
        
        // 2. 降低屏幕亮度（在主线程执行）
        mainHandler.post {
            dimScreen()
        }
        
        // 3. 显示半透明遮罩（干扰视觉）
        mainHandler.post {
            showNaughtyOverlay()
        }
        
        // 4. 播放提示音
        playReminderSound()
        
        // 5. 震动
        vibrate(500)
        
        // 6. 延迟后恢复亮度
        mainHandler.postDelayed({
            restoreBrightness()
            removeOverlay()
        }, OVERLAY_DURATION_MS)
    }

    /**
     * 第三阶段干预：监护人介入
     */
    suspend fun triggerLevel3() {
        Log.e(TAG, "触发三级干预 - 监护人介入")
        currentLevel = 3
        
        // 更新数据库统计
        updateInterventionCount(3)
        
        // 检查订阅等级（三级干预需要高级版）
        val subscription = subscriptionDao.getSubscription()
        if (subscription?.tier?.ordinal ?: 0 < SubscriptionTier.PREMIUM.ordinal) {
            Log.i(TAG, "非高级用户，三级干预降级为二级")
            triggerLevel2()
            return
        }
        
        // 1. 发送紧急通知给用户
        sendLevel3NotificationToUser()
        
        // 2. 通知监护人
        notifyGuardian()
        
        // 3. 强制显示全屏提醒（必须确认）
        mainHandler.post {
            showFullScreenIntervention()
        }
        
        // 4. 强烈震动
        vibrate(1000)
    }

    // ========== 捣蛋模式具体实现 ==========

    /**
     * 降低屏幕亮度
     */
    private fun dimScreen() {
        try {
            // 保存原始亮度
            originalBrightness = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
            
            // 设置降低后的亮度（30%）
            val newBrightness = (255 * DIM_MIN_BRIGHTNESS / 100).coerceIn(1, 255)
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                newBrightness
            )
            
            Log.d(TAG, "屏幕亮度已降低: $originalBrightness -> $newBrightness")
        } catch (e: Exception) {
            Log.e(TAG, "调整亮度失败", e)
        }
    }

    /**
     * 恢复屏幕亮度
     */
    private fun restoreBrightness() {
        if (originalBrightness > 0) {
            try {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    originalBrightness
                )
                Log.d(TAG, "屏幕亮度已恢复: $originalBrightness")
            } catch (e: Exception) {
                Log.e(TAG, "恢复亮度失败", e)
            }
        }
    }

    /**
     * 显示捣蛋遮罩
     */
    private fun showNaughtyOverlay() {
        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }
            
            // 使用半透明灰色遮罩
            val overlayView = View(context).apply {
                setBackgroundColor(0xAA000000.toInt()) // 半透明黑
            }
            
            windowManager?.addView(overlayView, params)
            this.overlayView = overlayView
            
            Log.d(TAG, "捣蛋遮罩已显示")
        } catch (e: Exception) {
            Log.e(TAG, "显示遮罩失败", e)
        }
    }

    /**
     * 移除遮罩
     */
    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
                Log.d(TAG, "捣蛋遮罩已移除")
            } catch (e: Exception) {
                Log.e(TAG, "移除遮罩失败", e)
            }
            overlayView = null
        }
    }

    /**
     * 播放提醒音效
     */
    private fun playReminderSound() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, maxVolume / 2, 0)
            
            // 使用系统默认通知音
            val notification = android.media.RingtoneManager.getDefaultUri(
                android.media.RingtoneManager.TYPE_NOTIFICATION
            )
            val player = MediaPlayer().apply {
                setDataSource(context, notification)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                prepare()
                start()
            }
            
            // 播放完成后释放资源
            player.setOnCompletionListener { it.release() }
            
        } catch (e: Exception) {
            Log.e(TAG, "播放音效失败", e)
        }
    }

    /**
     * 震动提醒
     */
    private fun vibrate(milliseconds: Long) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(milliseconds)
            }
        } catch (e: Exception) {
            Log.e(TAG, "震动失败", e)
        }
    }

    /**
     * 显示全屏强制干预界面
     */
    private fun showFullScreenIntervention() {
        try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                },
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
            )
            
            val view = LayoutInflater.from(context).inflate(R.layout.overlay_intervention, null)
            
            view.findViewById<TextView>(R.id.tv_title)?.text = "该休息了！"
            view.findViewById<TextView>(R.id.tv_message)?.text = 
                "你今天已经使用手机很长时间了。\n\n为了你的健康，建议：\n" +
                "1. 站起来活动一下\n" +
                "2. 看看远处的绿色植物\n" +
                "3. 做几个深呼吸\n\n" +
                "监护人已收到通知。"
            
            view.findViewById<Button>(R.id.btn_acknowledge)?.setOnClickListener {
                windowManager.removeView(view)
                // 记录用户确认
                serviceScope.launch {
                    // 保存确认记录
                }
            }
            
            windowManager.addView(view, params)
            
        } catch (e: Exception) {
            Log.e(TAG, "显示全屏干预失败", e)
        }
    }

    // ========== 通知相关 ==========

    private fun sendLevel2Notification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(context, CHANNEL_LEVEL2)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle("⚠️ 该休息了！")
            .setContentText("你已经使用很长时间了，请放下手机休息一下。")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()
        
        notificationManager.notify(NOTIF_LEVEL2_BASE, notification)
    }

    private fun sendLevel3NotificationToUser() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(context, CHANNEL_LEVEL3)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle("🚨 严重提醒")
            .setContentText("你的手机使用时间已严重超标，监护人已收到通知。")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .build()
        
        notificationManager.notify(NOTIF_LEVEL3_BASE, notification)
    }

    /**
     * 通知监护人
     */
    private suspend fun notifyGuardian() {
        try {
            val guardian = guardianDao.getPrimaryGuardian()
            if (guardian == null) {
                Log.w(TAG, "未设置监护人，跳过通知")
                return
            }
            
            // 获取今日使用统计
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val today = dateFormat.format(Date())
            val stats = dailyStatsDao.getStatsByDate(today)
            
            val message = buildString {
                append("【豆芽提醒】")
                append("您的孩子今日手机使用时长：")
                append("${(stats?.totalScreenTimeMs ?: 0) / 1000 / 60}分钟\n")
                append("其中娱乐类应用：${(stats?.entertainmentTimeMs ?: 0) / 1000 / 60}分钟\n")
                append("沉迷指数：${stats?.addictionScore ?: 0}/100\n")
                append("已达到需要关注的程度，建议适当引导。")
            }
            
            // 发送短信通知
            guardian.phoneNumber?.let { phone ->
                sendSMS(phone, message)
            }
            
            // 发送邮件通知（如果有邮箱）
            guardian.email?.let { email ->
                // TODO: 实现邮件发送（需要SMTP配置）
            }
            
            Log.i(TAG, "监护人通知已发送: ${guardian.name}")
            
        } catch (e: Exception) {
            Log.e(TAG, "通知监护人失败", e)
        }
    }

    /**
     * 发送短信（需要 SEND_SMS 权限）
     */
    private fun sendSMS(phoneNumber: String, message: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.telephony.SmsManager.getDefault()
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }
            
            // 长短信分片发送
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(
                phoneNumber, null, parts, null, null
            )
            
            Log.i(TAG, "短信已发送至: $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "发送短信失败", e)
        }
    }

    // ========== 辅助方法 ==========

    private suspend fun getTodayUsageMinutes(packageName: String): Int {
        // 从数据库查询今日使用时长
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())
        // TODO: 实现具体查询逻辑
        return 0
    }

    private suspend fun updateInterventionCount(level: Int) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())
        
        // TODO: 更新数据库中的干预计数
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_LEVEL1,
                    "温和提醒",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "第一阶段干预：友好提醒"
                },
                NotificationChannel(
                    CHANNEL_LEVEL2,
                    "捣蛋模式",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "第二阶段干预：降低娱乐感"
                    setBypassDnd(true)
                },
                NotificationChannel(
                    CHANNEL_LEVEL3,
                    "紧急干预",
                    NotificationManager.IMPORTANCE_MAX
                ).apply {
                    description = "第三阶段干预：监护人介入"
                    setBypassDnd(true)
                    enableVibration(true)
                }
            )
            
            notificationManager.createNotificationChannels(channels)
        }
    }
}
