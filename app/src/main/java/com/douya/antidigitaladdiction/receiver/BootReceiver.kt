package com.douya.antidigitaladdiction.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.douya.antidigitaladdiction.service.MonitorService

/**
 * 开机启动接收器
 * 设备重启后自动启动监控服务
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DouyaBoot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "设备启动完成，启动豆芽监控服务")
                startMonitorService(context)
            }
            "android.intent.action.QUICKBOOT_POWERON" -> {
                // 部分厂商快速启动
                Log.i(TAG, "快速启动完成，启动豆芽监控服务")
                startMonitorService(context)
            }
        }
    }

    private fun startMonitorService(context: Context) {
        val serviceIntent = Intent(context, MonitorService::class.java).apply {
            putExtra("from_boot", true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
