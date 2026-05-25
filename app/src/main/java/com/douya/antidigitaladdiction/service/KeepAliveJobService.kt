package com.douya.antidigitaladdiction.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*

/**
 * JobScheduler 保活服务
 * 学习 Kimi Claw 的系统级调度保活
 * 
 * 利用 Android 系统 JobScheduler 定期唤醒服务
 * 在 Doze 模式下仍能通过 setOverrideDeadline 强制执行
 */
class KeepAliveJobService : JobService() {

    companion object {
        private const val TAG = "DouyaKeepAliveJob"
        private const val JOB_ID = 1001
        private const val INTERVAL_MS = 15 * 60 * 1000L // 15分钟（JobScheduler 最小间隔）

        /**
         * 调度保活任务
         */
        fun schedule(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            
            // 检查是否已存在
            val existingJob = jobScheduler.getPendingJob(JOB_ID)
            if (existingJob != null) {
                Log.d(TAG, "保活任务已存在")
                return
            }
            
            val componentName = ComponentName(context, KeepAliveJobService::class.java)
            
            val jobInfoBuilder = JobInfo.Builder(JOB_ID, componentName).apply {
                // 设置最小延迟
                setMinimumLatency(INTERVAL_MS)
                
                // 设置最大延迟（确保一定会执行）
                setOverrideDeadline(INTERVAL_MS + 60000)
                
                // 需要设备充电时执行（省电）
                setRequiresCharging(false)
                
                // 需要设备空闲时执行
                setRequiresDeviceIdle(false)
                
                // 持久化（重启后保留）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setPersisted(true)
                }
                
                // 网络要求（不需要网络）
                setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                
                // 预估数据传输量
                setEstimatedNetworkBytes(0, 0)
            }
            
            val result = jobScheduler.schedule(jobInfoBuilder.build())
            
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.i(TAG, "保活任务调度成功，间隔: ${INTERVAL_MS / 1000 / 60}分钟")
            } else {
                Log.e(TAG, "保活任务调度失败")
            }
        }
        
        /**
         * 取消保活任务
         */
        fun cancel(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(JOB_ID)
            Log.i(TAG, "保活任务已取消")
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.i(TAG, "JobScheduler 保活任务触发")
        
        // 在后台检查并重启服务
        serviceScope.launch {
            try {
                ensureServicesRunning()
            } catch (e: Exception) {
                Log.e(TAG, "保活任务异常", e)
            } finally {
                jobFinished(params, false)
            }
        }
        
        // 返回 true 表示异步执行
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.w(TAG, "JobScheduler 保活任务被停止")
        
        // 重新调度
        schedule(this)
        
        // 返回 true 表示需要重试
        return true
    }

    /**
     * 确保所有服务都在运行
     */
    private fun ensureServicesRunning() {
        // 检查主监控服务
        if (!EnhancedMonitorService.isRunning) {
            Log.w(TAG, "主监控服务未运行，尝试启动")
            val intent = Intent(this, EnhancedMonitorService::class.java).apply {
                putExtra("from_job_scheduler", true)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        
        // 检查守护进程
        if (!DaemonService.isRunning) {
            Log.w(TAG, "守护进程未运行，尝试启动")
            val intent = Intent(this, DaemonService::class.java)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        
        // 重新调度下一次任务
        schedule(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}