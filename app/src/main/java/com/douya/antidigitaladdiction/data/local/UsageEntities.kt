package com.douya.antidigitaladdiction.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.time.LocalDateTime
import java.time.LocalDate

/**
 * 应用使用记录实体
 */
@Entity(tableName = "app_usage_records")
data class AppUsageRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,           // 应用包名
    val appName: String,               // 应用名称
    val startTime: LocalDateTime,      // 启动时间
    val endTime: LocalDateTime? = null, // 结束时间（可能未结束）
    val durationSeconds: Long = 0,     // 使用时长（秒）
    val date: LocalDate,               // 日期（用于按天统计）
    val category: AppCategory = AppCategory.OTHER  // 应用分类
)

/**
 * 每日统计实体
 */
@Entity(tableName = "daily_stats")
data class DailyStats(
    @PrimaryKey
    val date: LocalDate,               // 日期
    val totalUsageSeconds: Long = 0,   // 总使用时长
    val mostUsedApp: String = "",      // 最常使用应用
    val mostUsedAppSeconds: Long = 0,  // 最常使用应用时长
    val unlockCount: Int = 0,          // 解锁次数
    val peakHour: Int = -1,            // 使用高峰时段（0-23）
    val addictionIndex: Float = 0f,    // 沉迷指数（0-100）
    val categoryBreakdown: Map<AppCategory, Long> = emptyMap()  // 分类时长分布
)

/**
 * 应用分类枚举
 */
enum class AppCategory(val displayName: String, val riskLevel: RiskLevel) {
    SOCIAL("社交", RiskLevel.HIGH),
    GAME("游戏", RiskLevel.HIGH),
    SHORT_VIDEO("短视频", RiskLevel.HIGH),
    VIDEO("长视频", RiskLevel.MEDIUM),
    NEWS("资讯", RiskLevel.MEDIUM),
    SHOPPING("购物", RiskLevel.MEDIUM),
    EDUCATION("学习", RiskLevel.LOW),
    PRODUCTIVITY("工具", RiskLevel.LOW),
    SYSTEM("系统", RiskLevel.LOW),
    OTHER("其他", RiskLevel.MEDIUM);

    enum class RiskLevel {
        LOW, MEDIUM, HIGH
    }
}

/**
 * 周统计实体
 */
@Entity(tableName = "weekly_stats")
data class WeeklyStats(
    @PrimaryKey
    val weekStartDate: LocalDate,      // 周开始日期
    val totalUsageSeconds: Long = 0,   // 周总时长
    val dailyAverageSeconds: Long = 0, // 日均时长
    val weekdayAverageSeconds: Long = 0, // 工作日日均
    val weekendAverageSeconds: Long = 0, // 周末日均
    val addictionIndexTrend: List<Float> = emptyList(), // 每日沉迷指数趋势
    val topApps: List<Pair<String, Long>> = emptyList(), // Top5应用
    val improvementSuggestion: String = "" // 改善建议
)

/**
 * 沉迷指数计算参数
 */
object AddictionIndexParams {
    // 各风险等级的权重
    const val HIGH_RISK_WEIGHT = 2.0f
    const val MEDIUM_RISK_WEIGHT = 1.0f
    const val LOW_RISK_WEIGHT = 0.5f
    
    // 时长阈值（分钟）
    const val DAILY_LIMIT_NORMAL = 180   // 3小时正常
    const val DAILY_LIMIT_WARNING = 300  // 5小时警告
    const val DAILY_LIMIT_DANGER = 480   // 8小时危险
    
    // 连续使用阈值
    const val CONTINUOUS_WARNING_MINUTES = 60  // 连续1小时警告
    const val CONTINUOUS_DANGER_MINUTES = 120   // 连续2小时危险
}
