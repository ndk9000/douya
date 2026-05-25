package com.douya.antidigitaladdiction.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.douya.antidigitaladdiction.service.MonitorService

/**
 * 定时任务接收器
 * 用于保活检查和数据汇总
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DouyaAlarm"
        private const val ALARM_REQUEST_CODE = 1001
        private const val INTERVAL_MINUTES = 15L // 15分钟检查一次

        /**
         * 设置定时任务
         */
        fun scheduleAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                action = "com.douya.action.KEEP_ALIVE"
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 使用精确的闹钟（Android 12+ 需要权限）
            val triggerTime = SystemClock.elapsedRealtime() + INTERVAL_MINUTES * 60 * 1000

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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

            Log.i(TAG, "定时任务已设置，间隔: ${INTERVAL_MINUTES}分钟")
        }

        /**
         * 取消定时任务
         */
        fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
                Log.i(TAG, "定时任务已取消")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.douya.action.KEEP_ALIVE" -> {
                Log.i(TAG, "定时保活检查触发")
                checkAndRestartService(context)
                // 重新设置下一次定时
                scheduleAlarm(context)
            }
            "com.douya.action.DAILY_REPORT" -> {
                Log.i(TAG, "每日报告任务触发")
                // TODO: 生成并发送每日报告
            }
        }
    }

    private fun checkAndRestartService(context: Context) {
        if (!MonitorService.isRunning) {
            Log.w(TAG, "监控服务未运行，尝试重启")
            val serviceIntent = Intent(context, MonitorService::class.java).apply {
                putExtra("from_alarm", true)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            Log.d(TAG, "监控服务运行正常")
        }
    }
}
