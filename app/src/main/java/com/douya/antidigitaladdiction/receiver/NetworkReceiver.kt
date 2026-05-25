package com.douya.antidigitaladdiction.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.douya.antidigitaladdiction.service.MonitorService

/**
 * 网络状态变化接收器
 * 断网重连时检查服务状态
 */
class NetworkReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DouyaNetwork"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ConnectivityManager.CONNECTIVITY_ACTION) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)

            val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            if (isConnected) {
                Log.i(TAG, "网络已连接，检查监控服务状态")
                if (!MonitorService.isRunning) {
                    Log.w(TAG, "监控服务未运行，尝试重启")
                    val serviceIntent = Intent(context, MonitorService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            } else {
                Log.w(TAG, "网络已断开")
            }
        }
    }
}
