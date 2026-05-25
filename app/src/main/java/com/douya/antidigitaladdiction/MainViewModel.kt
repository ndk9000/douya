package com.douya.antidigitaladdiction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.douya.antidigitaladdiction.data.local.AppUsageDao
import com.douya.antidigitaladdiction.data.local.DailyStatsDao
import com.douya.antidigitaladdiction.service.EnhancedMonitorService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 主界面 ViewModel
 * 管理今日统计数据和应用使用排行
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val appUsageDao: AppUsageDao,
    private val dailyStatsDao: DailyStatsDao
) : ViewModel() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val today = dateFormat.format(Date())

    // 服务状态
    private val _serviceStatus = MutableStateFlow(ServiceStatus())
    val serviceStatus: StateFlow<ServiceStatus> = _serviceStatus.asStateFlow()

    // 今日统计
    private val _todayStats = MutableStateFlow<DailyStats?>(null)
    val todayStats: StateFlow<DailyStats?> = _todayStats.asStateFlow()

    // 应用使用排行
    private val _appUsageList = MutableStateFlow<List<AppUsageSummary>>(emptyList())
    val appUsageList: StateFlow<List<AppUsageSummary>> = _appUsageList.asStateFlow()

    init {
        loadTodayStats()
        loadAppUsageList()
        startServiceStatusMonitoring()
    }

    /**
     * 加载今日统计数据
     */
    private fun loadTodayStats() {
        viewModelScope.launch {
            try {
                val stats = dailyStatsDao.getStatsByDate(today)
                _todayStats.value = stats
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 加载应用使用排行
     */
    private fun loadAppUsageList() {
        viewModelScope.launch {
            try {
                val summaryList = appUsageDao.getUsageSummaryByDate(today)
                _appUsageList.value = summaryList.map { summary ->
                    AppUsageSummary(
                        appName = summary.appName,
                        totalDuration = summary.totalDuration,
                        isEntertainment = summary.packageName in AppAccessibilityService.ENTERTAINMENT_APPS
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 监控服务状态
     */
    private fun startServiceStatusMonitoring() {
        viewModelScope.launch {
            while (true) {
                _serviceStatus.value = ServiceStatus(
                    isRunning = EnhancedMonitorService.isRunning,
                    lastHeartbeat = EnhancedMonitorService.lastHeartbeatTime
                )
                kotlinx.coroutines.delay(5000) // 每5秒检查一次
            }
        }
    }

    /**
     * 刷新数据
     */
    fun refreshData() {
        loadTodayStats()
        loadAppUsageList()
    }
}
