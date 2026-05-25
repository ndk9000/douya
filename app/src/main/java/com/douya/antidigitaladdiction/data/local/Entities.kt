package com.douya.antidigitaladdiction.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 应用使用记录实体
 * 纯本地存储，不上传任何数据
 */
@Entity(
    tableName = "app_usage_records",
    indices = [
        Index(value = ["package_name", "date"]),
        Index(value = ["date"])
    ]
)
data class AppUsageRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "package_name")
    val packageName: String,
    
    @ColumnInfo(name = "app_name")
    val appName: String,
    
    @ColumnInfo(name = "date")
    val date: String, // YYYY-MM-DD
    
    @ColumnInfo(name = "start_time")
    val startTime: Long, // 毫秒时间戳
    
    @ColumnInfo(name = "end_time")
    val endTime: Long,
    
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    
    @ColumnInfo(name = "is_entertainment")
    val isEntertainment: Boolean = false, // 是否娱乐类应用
    
    @ColumnInfo(name = "category")
    val category: String = "other" // 应用分类
)

/**
 * 每日统计实体
 */
@Entity(tableName = "daily_stats")
data class DailyStats(
    @PrimaryKey
    @ColumnInfo(name = "date")
    val date: String, // YYYY-MM-DD
    
    @ColumnInfo(name = "total_screen_time_ms")
    val totalScreenTimeMs: Long = 0,
    
    @ColumnInfo(name = "entertainment_time_ms")
    val entertainmentTimeMs: Long = 0,
    
    @ColumnInfo(name = "social_time_ms")
    val socialTimeMs: Long = 0,
    
    @ColumnInfo(name = "game_time_ms")
    val gameTimeMs: Long = 0,
    
    @ColumnInfo(name = "education_time_ms")
    val educationTimeMs: Long = 0,
    
    @ColumnInfo(name = "unlock_count")
    val unlockCount: Int = 0,
    
    @ColumnInfo(name = "app_open_count")
    val appOpenCount: Int = 0,
    
    @ColumnInfo(name = "intervention_level1_count")
    val interventionLevel1Count: Int = 0,
    
    @ColumnInfo(name = "intervention_level2_count")
    val interventionLevel2Count: Int = 0,
    
    @ColumnInfo(name = "intervention_level3_triggered")
    val interventionLevel3Triggered: Boolean = false,
    
    @ColumnInfo(name = "addiction_score")
    val addictionScore: Float = 0f // 0-100 沉迷指数
)

/**
 * 应用配置实体（白名单/黑名单/阈值）
 */
@Entity(tableName = "app_configs")
data class AppConfig(
    @PrimaryKey
    @ColumnInfo(name = "package_name")
    val packageName: String,
    
    @ColumnInfo(name = "app_name")
    val appName: String,
    
    @ColumnInfo(name = "category")
    val category: AppCategory = AppCategory.OTHER,
    
    @ColumnInfo(name = "is_whitelisted")
    val isWhitelisted: Boolean = false, // 白名单（不计入沉迷）
    
    @ColumnInfo(name = "is_blacklisted")
    val isBlacklisted: Boolean = false, // 黑名单（严格限制）
    
    @ColumnInfo(name = "daily_limit_minutes")
    val dailyLimitMinutes: Int = 60, // 默认每日限制（分钟）
    
    @ColumnInfo(name = "is_strict_mode")
    val isStrictMode: Boolean = false // 是否严格模式（到时间直接阻断）
)

enum class AppCategory {
    SOCIAL,      // 社交
    GAME,        // 游戏
    VIDEO,       // 视频
    EDUCATION,   // 教育
    PRODUCTIVITY, // 生产力
    OTHER        // 其他
}

/**
 * 监护人信息实体
 */
@Entity(tableName = "guardian_info")
data class GuardianInfo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "phone_number")
    val phoneNumber: String? = null,
    
    @ColumnInfo(name = "email")
    val email: String? = null,
    
    @ColumnInfo(name = "is_primary")
    val isPrimary: Boolean = true,
    
    @ColumnInfo(name = "notify_level1")
    val notifyLevel1: Boolean = false, // 是否通知一级干预
    
    @ColumnInfo(name = "notify_level2")
    val notifyLevel2: Boolean = true,  // 是否通知二级干预
    
    @ColumnInfo(name = "notify_level3")
    val notifyLevel3: Boolean = true, // 是否通知三级干预（默认开启）
    
    @ColumnInfo(name = "daily_report")
    val dailyReport: Boolean = true,    // 是否发送每日报告
    
    @ColumnInfo(name = "weekly_report")
    val weeklyReport: Boolean = true    // 是否发送每周报告
)

/**
 * 订阅信息实体（本地验证）
 */
@Entity(tableName = "subscription")
data class Subscription(
    @PrimaryKey
    val id: Int = 1,
    
    @ColumnInfo(name = "tier")
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    
    @ColumnInfo(name = "expires_at")
    val expiresAt: Long? = null, // 过期时间戳
    
    @ColumnInfo(name = "activated_at")
    val activatedAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
)

enum class SubscriptionTier {
    FREE,       // 免费版：基础监控 + 一级干预
    BASIC,      // 基础版：+ 二级干预 + 周报
    PREMIUM,    // 高级版：+ 三级干预 + 实时同步 + 家长端Web
    FAMILY      // 家庭版：多设备 + 多个孩子管理
}

// ============== DAO ==============

@Dao
interface AppUsageDao {
    
    @Insert
    suspend fun insert(record: AppUsageRecord)
    
    @Insert
    suspend fun insertAll(records: List<AppUsageRecord>)
    
    @Query("""
        SELECT * FROM app_usage_records 
        WHERE date = :date 
        ORDER BY start_time DESC
    """)
    fun getRecordsByDate(date: String): Flow<List<AppUsageRecord>>
    
    @Query("""
        SELECT package_name, app_name, SUM(duration_ms) as total_duration
        FROM app_usage_records 
        WHERE date = :date
        GROUP BY package_name 
        ORDER BY total_duration DESC
    """)
    suspend fun getUsageSummaryByDate(date: String): List<UsageSummary>
    
    @Query("""
        SELECT SUM(duration_ms) FROM app_usage_records 
        WHERE date = :date AND is_entertainment = 1
    """)
    suspend fun getEntertainmentTimeByDate(date: String): Long?
    
    @Query("DELETE FROM app_usage_records WHERE date < :beforeDate")
    suspend fun deleteOldRecords(beforeDate: String): Int
    
    @Query("SELECT COUNT(*) FROM app_usage_records WHERE date = :date")
    suspend fun getRecordCountByDate(date: String): Int
}

data class UsageSummary(
    val packageName: String,
    val appName: String,
    val totalDuration: Long
)

@Dao
interface DailyStatsDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stats: DailyStats)
    
    @Query("SELECT * FROM daily_stats WHERE date = :date")
    suspend fun getStatsByDate(date: String): DailyStats?
    
    @Query("SELECT * FROM daily_stats ORDER BY date DESC LIMIT :limit")
    fun getRecentStats(limit: Int): Flow<List<DailyStats>>
    
    @Query("SELECT * FROM daily_stats WHERE date BETWEEN :startDate AND :endDate ORDER BY date")
    suspend fun getStatsRange(startDate: String, endDate: String): List<DailyStats>
    
    @Query("UPDATE daily_stats SET addiction_score = :score WHERE date = :date")
    suspend fun updateAddictionScore(date: String, score: Float)
}

@Dao
interface AppConfigDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: AppConfig)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<AppConfig>)
    
    @Query("SELECT * FROM app_configs")
    fun getAllConfigs(): Flow<List<AppConfig>>
    
    @Query("SELECT * FROM app_configs WHERE package_name = :packageName")
    suspend fun getConfig(packageName: String): AppConfig?
    
    @Query("SELECT * FROM app_configs WHERE is_whitelisted = 1")
    suspend fun getWhitelistedApps(): List<AppConfig>
    
    @Query("SELECT * FROM app_configs WHERE is_blacklisted = 1")
    suspend fun getBlacklistedApps(): List<AppConfig>
    
    @Query("UPDATE app_configs SET is_whitelisted = :whitelisted WHERE package_name = :packageName")
    suspend fun updateWhitelist(packageName: String, whitelisted: Boolean)
    
    @Query("UPDATE app_configs SET daily_limit_minutes = :limit WHERE package_name = :packageName")
    suspend fun updateLimit(packageName: String, limit: Int)
}

@Dao
interface GuardianDao {
    
    @Insert
    suspend fun insert(guardian: GuardianInfo)
    
    @Update
    suspend fun update(guardian: GuardianInfo)
    
    @Delete
    suspend fun delete(guardian: GuardianInfo)
    
    @Query("SELECT * FROM guardian_info WHERE is_primary = 1 LIMIT 1")
    suspend fun getPrimaryGuardian(): GuardianInfo?
    
    @Query("SELECT * FROM guardian_info")
    fun getAllGuardians(): Flow<List<GuardianInfo>>
}

@Dao
interface SubscriptionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subscription: Subscription)
    
    @Query("SELECT * FROM subscription WHERE id = 1")
    suspend fun getSubscription(): Subscription?
    
    @Query("UPDATE subscription SET tier = :tier, expires_at = :expiresAt WHERE id = 1")
    suspend fun updateTier(tier: SubscriptionTier, expiresAt: Long?)
    
    @Query("UPDATE subscription SET is_active = :active WHERE id = 1")
    suspend fun setActive(active: Boolean)
}

// ============== Database ==============

@Database(
    entities = [
        AppUsageRecord::class,
        DailyStats::class,
        AppConfig::class,
        GuardianInfo::class,
        Subscription::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DouyaDatabase : RoomDatabase() {
    abstract fun appUsageDao(): AppUsageDao
    abstract fun dailyStatsDao(): DailyStatsDao
    abstract fun appConfigDao(): AppConfigDao
    abstract fun guardianDao(): GuardianDao
    abstract fun subscriptionDao(): SubscriptionDao
    
    companion object {
        const val DATABASE_NAME = "douya_local.db"
    }
}
