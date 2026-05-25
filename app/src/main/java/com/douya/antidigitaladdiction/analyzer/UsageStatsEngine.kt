package com.douya.antidigitaladdiction.analyzer

import com.douya.antidigitaladdiction.data.local.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Duration
import kotlin.math.min

/**
 * 使用时长统计引擎
 */
object UsageStatsEngine {
    
    /**
     * 计算单日统计
     */
    fun calculateDailyStats(
        date: LocalDate,
        records: List<AppUsageRecord>
    ): DailyStats {
        if (records.isEmpty()) {
            return DailyStats(date = date)
        }
        
        // 总时长
        val totalSeconds = records.sumOf { it.durationSeconds }
        
        // 最常使用应用
        val appUsageMap = records.groupBy { it.packageName }
            .mapValues { it.value.sumOf { r -> r.durationSeconds } }
        val mostUsed = appUsageMap.maxByOrNull { it.value }
        
        // 高峰时段统计
        val hourDistribution = records.flatMap { record ->
            val startHour = record.startTime.hour
            val endHour = record.endTime?.hour ?: startHour
            (startHour..endHour).map { it to record.durationSeconds }
        }.groupBy { it.first }
            .mapValues { it.value.sumOf { p -> p.second } }
        val peakHour = hourDistribution.maxByOrNull { it.value }?.key ?: -1
        
        // 分类分布
        val categoryBreakdown = records.groupBy { it.category }
            .mapValues { it.value.sumOf { r -> r.durationSeconds } }
        
        // 计算沉迷指数
        val addictionIndex = calculateAddictionIndex(
            totalSeconds, categoryBreakdown, records
        )
        
        return DailyStats(
            date = date,
            totalUsageSeconds = totalSeconds,
            mostUsedApp = mostUsed?.key ?: "",
            mostUsedAppSeconds = mostUsed?.value ?: 0,
            peakHour = peakHour,
            addictionIndex = addictionIndex,
            categoryBreakdown = categoryBreakdown
        )
    }
    
    /**
     * 计算周统计
     */
    fun calculateWeeklyStats(
        weekStart: LocalDate,
        dailyStats: List<DailyStats>
    ): WeeklyStats {
        if (dailyStats.isEmpty()) {
            return WeeklyStats(weekStartDate = weekStart)
        }
        
        val totalSeconds = dailyStats.sumOf { it.totalUsageSeconds }
        val dailyAverage = totalSeconds / dailyStats.size
        
        // 区分工作日和周末
        val weekdayStats = dailyStats.filter { 
            it.date.dayOfWeek.value <= 5 
        }
        val weekendStats = dailyStats.filter { 
            it.date.dayOfWeek.value > 5 
        }
        
        val weekdayAverage = if (weekdayStats.isNotEmpty()) {
            weekdayStats.sumOf { it.totalUsageSeconds } / weekdayStats.size
        } else 0
        
        val weekendAverage = if (weekendStats.isNotEmpty()) {
            weekendStats.sumOf { it.totalUsageSeconds } / weekendStats.size
        } else 0
        
        // 沉迷指数趋势
        val indexTrend = dailyStats.map { it.addictionIndex }
        
        // Top5应用（基于所有日期的聚合）
        val appUsageMap = mutableMapOf<String, Long>()
        dailyStats.forEach { day ->
            // 这里简化处理，实际应该从原始记录聚合
        }
        
        // 生成改善建议
        val suggestion = generateSuggestion(
            dailyAverage, weekdayAverage, weekendAverage, indexTrend
        )
        
        return WeeklyStats(
            weekStartDate = weekStart,
            totalUsageSeconds = totalSeconds,
            dailyAverageSeconds = dailyAverage,
            weekdayAverageSeconds = weekdayAverage,
            weekendAverageSeconds = weekendAverage,
            addictionIndexTrend = indexTrend,
            improvementSuggestion = suggestion
        )
    }
    
    /**
     * 计算沉迷指数（0-100）
     * 综合考量：总时长、高风险应用占比、连续使用时长
     */
    private fun calculateAddictionIndex(
        totalSeconds: Long,
        categoryBreakdown: Map<AppCategory, Long>,
        records: List<AppUsageRecord>
    ): Float {
        var score = 0f
        
        // 1. 时长评分（0-40分）
        val totalMinutes = totalSeconds / 60
        score += when {
            totalMinutes <= AddictionIndexParams.DAILY_LIMIT_NORMAL -> {
                (totalMinutes.toFloat() / AddictionIndexParams.DAILY_LIMIT_NORMAL) * 20
            }
            totalMinutes <= AddictionIndexParams.DAILY_LIMIT_WARNING -> {
                20 + ((totalMinutes - AddictionIndexParams.DAILY_LIMIT_NORMAL).toFloat() /
                        (AddictionIndexParams.DAILY_LIMIT_WARNING - AddictionIndexParams.DAILY_LIMIT_NORMAL)) * 10
            }
            else -> {
                30 + min(10f, (totalMinutes - AddictionIndexParams.DAILY_LIMIT_WARNING).toFloat() / 60)
            }
        }
        
        // 2. 高风险应用占比（0-35分）
        val highRiskTime = categoryBreakdown.filter { 
            it.key.riskLevel == AppCategory.RiskLevel.HIGH 
        }.values.sum()
        val highRiskRatio = if (totalSeconds > 0) {
            highRiskTime.toFloat() / totalSeconds
        } else 0f
        score += highRiskRatio * 35
        
        // 3. 连续使用时长评分（0-25分）
        val maxContinuousMinutes = calculateMaxContinuousUsage(records)
        score += when {
            maxContinuousMinutes <= 30 -> maxContinuousMinutes.toFloat() / 30 * 5
            maxContinuousMinutes <= AddictionIndexParams.CONTINUOUS_WARNING_MINUTES -> {
                5 + (maxContinuousMinutes - 30).toFloat() /
                        (AddictionIndexParams.CONTINUOUS_WARNING_MINUTES - 30) * 10
            }
            maxContinuousMinutes <= AddictionIndexParams.CONTINUOUS_DANGER_MINUTES -> {
                15 + (maxContinuousMinutes - AddictionIndexParams.CONTINUOUS_WARNING_MINUTES).toFloat() /
                        (AddictionIndexParams.CONTINUOUS_DANGER_MINUTES - AddictionIndexParams.CONTINUOUS_WARNING_MINUTES) * 5
            }
            else -> 20 + min(5f, (maxContinuousMinutes - AddictionIndexParams.CONTINUOUS_DANGER_MINUTES).toFloat() / 60)
        }
        
        return min(100f, score)
    }
    
    /**
     * 计算最大连续使用时长（分钟）
     */
    private fun calculateMaxContinuousUsage(records: List<AppUsageRecord>): Int {
        if (records.isEmpty()) return 0
        
        var maxContinuous = 0
        var currentContinuous = 0
        var lastEndTime: LocalDateTime? = null
        
        val sortedRecords = records.sortedBy { it.startTime }
        
        for (record in sortedRecords) {
            if (lastEndTime != null) {
                val gap = Duration.between(lastEndTime, record.startTime).toMinutes()
                if (gap <= 5) { // 5分钟内切换视为连续使用
                    currentContinuous += (record.durationSeconds / 60).toInt()
                } else {
                    maxContinuous = maxOf(maxContinuous, currentContinuous)
                    currentContinuous = (record.durationSeconds / 60).toInt()
                }
            } else {
                currentContinuous = (record.durationSeconds / 60).toInt()
            }
            lastEndTime = record.endTime ?: record.startTime.plusSeconds(record.durationSeconds)
        }
        
        return maxOf(maxContinuous, currentContinuous)
    }
    
    /**
     * 生成改善建议
     */
    private fun generateSuggestion(
        dailyAverage: Long,
        weekdayAverage: Long,
        weekendAverage: Long,
        indexTrend: List<Float>
    ): String {
        val suggestions = mutableListOf<String>()
        
        // 时长建议
        val dailyAverageMinutes = dailyAverage / 60
        when {
            dailyAverageMinutes > 300 -> suggestions.add("日均使用超过5小时，建议设定每日使用上限")
            dailyAverageMinutes > 180 -> suggestions.add("日均使用超过3小时，注意控制娱乐类应用时间")
        }
        
        // 周末对比
        if (weekendAverage > weekdayAverage * 1.5) {
            suggestions.add("周末使用时长明显高于工作日，建议丰富线下活动")
        }
        
        // 趋势建议
        if (indexTrend.size >= 3) {
            val recent = indexTrend.takeLast(3).average()
            val previous = indexTrend.dropLast(3).takeLast(3).average()
            if (recent > previous + 10) {
                suggestions.add("近期沉迷指数上升明显，建议启用捣蛋干预模式")
            } else if (recent < previous - 10) {
                suggestions.add("近期使用情况改善，继续保持！")
            }
        }
        
        return if (suggestions.isEmpty()) {
            "使用情况良好，继续保持自我觉察"
        } else {
            suggestions.joinToString("\n")
        }
    }
    
    /**
     * 获取沉迷等级描述
     */
    fun getAddictionLevel(index: Float): String {
        return when {
            index < 20 -> "健康"
            index < 40 -> "轻度关注"
            index < 60 -> "需要调整"
            index < 80 -> "沉迷风险"
            else -> "严重沉迷"
        }
    }
    
    /**
     * 获取沉迷等级颜色（用于UI展示）
     */
    fun getAddictionColor(index: Float): String {
        return when {
            index < 20 -> "#4CAF50"  // 绿色
            index < 40 -> "#8BC34A"  // 浅绿
            index < 60 -> "#FFC107"  // 黄色
            index < 80 -> "#FF9800"  // 橙色
            else -> "#F44336"        // 红色
        }
    }
}
