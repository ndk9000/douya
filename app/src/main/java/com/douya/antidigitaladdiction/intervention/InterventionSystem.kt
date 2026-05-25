package com.douya.antidigitaladdiction.intervention

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.douya.antidigitaladdiction.MainActivity
import com.douya.antidigitaladdiction.data.local.AppCategory
import com.douya.antidigitaladdiction.data.local.DailyStats
import java.time.LocalDate
import kotlin.random.Random

/**
 * 通知管理器 - 第一阶段干预（觉察提醒）
 */
class InterventionNotificationManager(private val context: Context) {
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    companion object {
        const val CHANNEL_ID = "douya_intervention"
        const val CHANNEL_NAME = "豆芽干预提醒"
        const val NOTIFICATION_ID_BASE = 1000
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "豆芽防沉迷应用的温和提醒通知"
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 发送使用时长提醒
     */
    fun sendUsageReminder(packageName: String, appName: String, usageMinutes: Long) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("show_stats", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val messages = getReminderMessages(appName, usageMinutes)
        val message = messages.random()
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("豆芽提醒 🌱")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        
        val notificationId = NOTIFICATION_ID_BASE + packageName.hashCode()
        notificationManager.notify(notificationId, notification)
    }
    
    /**
     * 发送每日总结通知
     */
    fun sendDailySummary(stats: DailyStats) {
        val totalMinutes = stats.totalUsageSeconds / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        
        val title = when {
            stats.addictionIndex < 20 -> "今日表现很棒！🌟"
            stats.addictionIndex < 40 -> "今天使用控制不错 👍"
            stats.addictionIndex < 60 -> "今日使用时长: ${hours}小时${minutes}分钟"
            else -> "今天手机使用有点多哦 📱"
        }
        
        val message = buildString {
            append("总使用: ${hours}小时${minutes}分钟\n")
            if (stats.mostUsedApp.isNotEmpty()) {
                append("最常使用: ${stats.mostUsedApp}")
            }
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_BASE + 999, notification)
    }
    
    /**
     * 发送连续使用警告
     */
    fun sendContinuousUsageWarning(appName: String, continuousMinutes: Int) {
        val messages = when {
            continuousMinutes >= 120 -> listOf(
                "已经连续使用${appName}2小时了，眼睛需要休息！",
                "2小时了！站起来活动一下吧 🚶",
                "长时间使用${appName}，建议休息15分钟"
            )
            continuousMinutes >= 60 -> listOf(
                "已经连续使用${appName}1小时了",
                "1小时了，喝杯水休息一下吧 💧",
                "${appName}使用1小时，建议远眺放松眼睛"
            )
            else -> listOf(
                "${appName}使用${continuousMinutes}分钟了",
                "适当休息，保护视力 👀"
            )
        }
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("连续使用提醒 ⏰")
            .setContentText(messages.random())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID_BASE + 998, notification)
    }
    
    /**
     * 获取提醒消息模板
     */
    private fun getReminderMessages(appName: String, usageMinutes: Long): List<String> {
        val hours = usageMinutes / 60
        val minutes = usageMinutes % 60
        
        return when {
            usageMinutes >= 180 -> listOf(
                "${appName}已经使用${hours}小时${minutes}分钟了，休息一下？",
                "使用${appName}${hours}小时了，记得保护眼睛",
                "${hours}小时${minutes}分钟，尝试放下手机做点什么？",
                "${appName}使用时间较长，建议休息10分钟"
            )
            usageMinutes >= 120 -> listOf(
                "${appName}使用${hours}小时${minutes}分钟了",
                "已经${hours}小时了，喝杯水吧 💧",
                "使用${appName}${hours}小时，建议活动一下"
            )
            usageMinutes >= 60 -> listOf(
                "${appName}使用1小时了",
                "1小时了，眼睛需要休息 👀",
                "使用${appName}1小时，建议远眺放松"
            )
            else -> listOf(
                "${appName}使用${minutes}分钟了",
                "适当控制使用时间哦"
            )
        }
    }
    
    /**
     * 取消所有干预通知
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
}

/**
 * 捣蛋引擎 - 第二阶段干预（趣味降低体验）
 */
class MischiefEngine(private val context: Context) {
    
    private val random = Random.Default
    private var mischiefLevel = 0  // 捣蛋等级，随连续超时递增
    
    /**
     * 执行捣蛋行为
     */
    fun executeMischief(appCategory: AppCategory, continuousMinutes: Int) {
        mischiefLevel = when {
            continuousMinutes >= 180 -> 3  // 3小时，高强度
            continuousMinutes >= 120 -> 2  // 2小时，中强度
            continuousMinutes >= 60 -> 1   // 1小时，低强度
            else -> 0
        }
        
        if (mischiefLevel == 0) return
        
        // 根据应用类型选择捣蛋策略
        when (appCategory) {
            AppCategory.SHORT_VIDEO, AppCategory.GAME, AppCategory.SOCIAL -> {
                // 对高风险应用使用更强捣蛋
                executeHighRiskMischief()
            }
            AppCategory.VIDEO, AppCategory.NEWS, AppCategory.SHOPPING -> {
                // 中等风险应用使用温和捣蛋
                executeMediumRiskMischief()
            }
            else -> {
                // 低风险应用轻微提醒
                executeLowRiskMischief()
            }
        }
    }
    
    /**
     * 高风险应用捣蛋策略
     */
    private fun executeHighRiskMischief() {
        val actions = listOf(
            ::showTransparentOverlay,
            ::reduceBrightness,
            ::showFunnyMessage,
            ::triggerGentleVibration
        )
        
        // 根据捣蛋等级执行多个动作
        val numActions = minOf(mischiefLevel, actions.size)
        actions.shuffled().take(numActions).forEach { it.invoke() }
    }
    
    /**
     * 中等风险应用捣蛋策略
     */
    private fun executeMediumRiskMischief() {
        val actions = listOf(
            ::showFunnyMessage,
            ::triggerGentleVibration
        )
        
        if (random.nextFloat() < 0.5f) {
            actions.random().invoke()
        }
    }
    
    /**
     * 低风险应用捣蛋策略
     */
    private fun executeLowRiskMischief() {
        if (random.nextFloat() < 0.3f) {
            showFunnyMessage()
        }
    }
    
    /**
     * 显示半透明遮罩
     */
    private fun showTransparentOverlay() {
        // 启动透明覆盖Activity
        val intent = Intent(context, MischiefOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("mischief_type", "overlay")
            putExtra("alpha", 0.3f + (mischiefLevel * 0.1f))
        }
        context.startActivity(intent)
    }
    
    /**
     * 降低屏幕亮度
     */
    private fun reduceBrightness() {
        val intent = Intent(context, MischiefOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("mischief_type", "brightness")
            putExtra("brightness_level", 0.5f - (mischiefLevel * 0.1f))
        }
        context.startActivity(intent)
    }
    
    /**
     * 显示趣味提示消息
     */
    private fun showFunnyMessage() {
        val messages = listOf(
            "📱 手机说它累了，想休息一会儿",
            "🌱 豆芽提醒：该让眼睛透透气了",
            "⏰ 时间飞逝，已经这么久了！",
            "🎯 挑战：放下手机10分钟，你能做到吗？",
            "💡 提示：现实世界也很精彩哦",
            "🎮 游戏角色也需要休息，你也是！",
            "📚 不如看一页书？就一页！",
            "🚶 站起来走走，对身体更好",
            "🎵 听首歌，让眼睛休息一下吧",
            "🌟 你比手机更重要，照顾好自己"
        )
        
        val intent = Intent(context, MischiefOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("mischief_type", "message")
            putExtra("message", messages.random())
        }
        context.startActivity(intent)
    }
    
    /**
     * 触发轻微震动
     */
    private fun triggerGentleVibration() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(
                    android.os.VibrationEffect.createOneShot(
                        200L * mischiefLevel,
                        android.os.VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(200L * mischiefLevel)
            }
        }
    }
    
    /**
     * 重置捣蛋等级
     */
    fun resetMischiefLevel() {
        mischiefLevel = 0
    }
}

/**
 * 捣蛋覆盖Activity（需要在AndroidManifest中声明）
 */
class MischiefOverlayActivity : android.app.Activity() {
    // 实现半透明覆盖、亮度调整、消息展示
    // 具体实现需要配合布局文件
}
