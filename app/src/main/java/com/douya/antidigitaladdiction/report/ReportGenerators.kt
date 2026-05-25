package com.douya.antidigitaladdiction.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.douya.antidigitaladdiction.analyzer.UsageStatsEngine
import com.douya.antidigitaladdiction.data.local.AppCategory
import com.douya.antidigitaladdiction.data.local.DailyStats
import com.douya.antidigitaladdiction.data.local.WeeklyStats
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 日报生成器
 */
class DailyReportGenerator(private val context: Context) {
    
    private val dateFormatter = DateTimeFormatter.ofPattern("MM月dd日")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    
    /**
     * 生成日报文本
     */
    fun generateTextReport(stats: DailyStats): String {
        val totalMinutes = stats.totalUsageSeconds / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        
        val mostUsedMinutes = stats.mostUsedAppSeconds / 60
        
        val level = UsageStatsEngine.getAddictionLevel(stats.addictionIndex)
        val levelEmoji = when {
            stats.addictionIndex < 20 -> "🌱"
            stats.addictionIndex < 40 -> "🌿"
            stats.addictionIndex < 60 -> "🍂"
            stats.addictionIndex < 80 -> "🍁"
            else -> "🔥"
        }
        
        return buildString {
            appendLine("═══ 豆芽日报 $levelEmoji ═══")
            appendLine("📅 ${stats.date.format(dateFormatter)}")
            appendLine()
            appendLine("⏱️ 总使用时长: ${hours}小时${minutes}分钟")
            appendLine()
            appendLine("📱 最常使用: ${stats.mostUsedApp}")
            appendLine("   时长: ${mostUsedMinutes}分钟")
            appendLine()
            
            if (stats.peakHour >= 0) {
                appendLine("⏰ 使用高峰: ${stats.peakHour}:00-${stats.peakHour + 1}:00")
                appendLine()
            }
            
            appendLine("📊 应用分类分布:")
            stats.categoryBreakdown.forEach { (category, seconds) ->
                val mins = seconds / 60
                val bar = "█".repeat((mins / 10).toInt().coerceAtMost(10))
                appendLine("   ${category.displayName}: ${mins}分钟 $bar")
            }
            appendLine()
            
            appendLine("🎯 沉迷指数: ${String.format("%.1f", stats.addictionIndex)}/100")
            appendLine("   等级: $level")
            appendLine()
            
            appendLine("💡 今日建议:")
            appendLine("   ${generateDailySuggestion(stats)}")
        }
    }
    
    /**
     * 生成日报图片（用于分享）
     */
    fun generateImageReport(stats: DailyStats): Bitmap {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 背景
        canvas.drawColor(Color.parseColor("#F5F5F5"))
        
        val paint = Paint().apply {
            isAntiAlias = true
        }
        
        // 标题区域
        val titleBg = RectF(0f, 0f, width.toFloat(), 200f)
        paint.color = Color.parseColor(UsageStatsEngine.getAddictionColor(stats.addictionIndex))
        canvas.drawRect(titleBg, paint)
        
        // 标题文字
        paint.color = Color.WHITE
        paint.textSize = 48f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("豆芽日报", width / 2f, 100f, paint)
        
        paint.textSize = 32f
        canvas.drawText(stats.date.format(dateFormatter), width / 2f, 150f, paint)
        
        // 沉迷指数圆环
        val centerX = width / 2f
        val centerY = 400f
        val radius = 150f
        
        // 背景圆环
        paint.color = Color.parseColor("#E0E0E0")
        paint.strokeWidth = 20f
        paint.style = Paint.Style.STROKE
        canvas.drawCircle(centerX, centerY, radius, paint)
        
        // 进度圆环
        paint.color = Color.parseColor(UsageStatsEngine.getAddictionColor(stats.addictionIndex))
        val sweepAngle = (stats.addictionIndex / 100f) * 360
        canvas.drawArc(
            centerX - radius, centerY - radius,
            centerX + radius, centerY + radius,
            -90f, sweepAngle, false, paint
        )
        
        // 指数文字
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        paint.textSize = 64f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            String.format("%.0f", stats.addictionIndex),
            centerX, centerY - 20f, paint
        )
        
        paint.textSize = 28f
        canvas.drawText("沉迷指数", centerX, centerY + 30f, paint)
        
        // 时长信息
        val totalMinutes = stats.totalUsageSeconds / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        
        paint.textSize = 36f
        paint.color = Color.parseColor("#333333")
        canvas.drawText(
            "今日使用: ${hours}小时${minutes}分钟",
            width / 2f, 650f, paint
        )
        
        // 分类分布柱状图
        var startY = 750f
        val barMaxWidth = width - 200f
        
        stats.categoryBreakdown.toList()
            .sortedByDescending { it.second }
            .take(5)
            .forEach { (category, seconds) ->
                val mins = seconds / 60
                val ratio = if (stats.totalUsageSeconds > 0) {
                    seconds.toFloat() / stats.totalUsageSeconds
                } else 0f
                
                paint.color = Color.parseColor("#666666")
                paint.textSize = 28f
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(category.displayName, 50f, startY, paint)
                
                // 柱状图
                val barWidth = barMaxWidth * ratio
                val barRect = RectF(50f, startY + 10f, 50f + barWidth, startY + 40f)
                paint.color = when (category.riskLevel) {
                    AppCategory.RiskLevel.LOW -> Color.parseColor("#4CAF50")
                    AppCategory.RiskLevel.MEDIUM -> Color.parseColor("#FFC107")
                    AppCategory.RiskLevel.HIGH -> Color.parseColor("#F44336")
                }
                canvas.drawRect(barRect, paint)
                
                paint.color = Color.parseColor("#666666")
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("${mins}分钟", width - 50f, startY + 35f, paint)
                
                startY += 80f
            }
        
        return bitmap
    }
    
    /**
     * 保存日报图片
     */
    fun saveReportImage(bitmap: Bitmap, date: LocalDate): File {
        val fileName = "daily_report_${date}.png"
        val file = File(context.getExternalFilesDir(null), fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }
    
    private fun generateDailySuggestion(stats: DailyStats): String {
        val totalMinutes = stats.totalUsageSeconds / 60
        
        return when {
            totalMinutes > 480 -> "今日使用超过8小时，建议立即启用捣蛋模式，并考虑安排线下活动"
            totalMinutes > 300 -> "使用时长较长，注意休息眼睛，建议每小时远眺5分钟"
            stats.addictionIndex > 60 -> "沉迷指数偏高，尝试将手机放在另一个房间充电"
            stats.categoryBreakdown[AppCategory.SHORT_VIDEO] != null -> {
                val videoMinutes = stats.categoryBreakdown[AppCategory.SHORT_VIDEO]!! / 60
                if (videoMinutes > 60) "短视频使用${videoMinutes}分钟，尝试设置每日1小时上限"
                else "短视频使用控制良好"
            }
            else -> "使用情况良好，继续保持自我觉察"
        }
    }
}

/**
 * 周报生成器
 */
class WeeklyReportGenerator(private val context: Context) {
    
    private val dateFormatter = DateTimeFormatter.ofPattern("MM/dd")
    
    /**
     * 生成周报文本
     */
    fun generateTextReport(stats: WeeklyStats): String {
        val totalHours = stats.totalUsageSeconds / 3600
        val dailyAvgHours = stats.dailyAverageSeconds / 3600
        val dailyAvgMinutes = (stats.dailyAverageSeconds % 3600) / 60
        
        return buildString {
            appendLine("═══ 豆芽周报 📊 ═══")
            appendLine("📅 ${stats.weekStartDate.format(dateFormatter)} - ${stats.weekStartDate.plusDays(6).format(dateFormatter)}")
            appendLine()
            appendLine("⏱️ 本周总时长: ${totalHours}小时")
            appendLine("📈 日均使用: ${dailyAvgHours}小时${dailyAvgMinutes}分钟")
            appendLine()
            
            val weekdayAvgHours = stats.weekdayAverageSeconds / 3600
            val weekendAvgHours = stats.weekendAverageSeconds / 3600
            appendLine("📊 工作日日均: ${weekdayAvgHours}小时")
            appendLine("🎯 周末日均: ${weekendAvgHours}小时")
            
            if (stats.weekendAverageSeconds > stats.weekdayAverageSeconds * 1.5) {
                appendLine("⚠️ 周末使用明显偏多")
            }
            appendLine()
            
            // 沉迷指数趋势
            if (stats.addictionIndexTrend.isNotEmpty()) {
                appendLine("📉 沉迷指数趋势:")
                stats.addictionIndexTrend.forEachIndexed { index, value ->
                    val dayLabel = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
                    val label = dayLabel.getOrElse(index) { "第${index + 1}天" }
                    val bar = "█".repeat((value / 10).toInt().coerceAtMost(10))
                    appendLine("   $label: ${String.format("%.1f", value)} $bar")
                }
                appendLine()
            }
            
            appendLine("💡 本周建议:")
            appendLine("   ${stats.improvementSuggestion}")
        }
    }
    
    /**
     * 生成守护报告（给家长）
     */
    fun generateGuardianReport(stats: WeeklyStats): String {
        val dailyAvgHours = stats.dailyAverageSeconds / 3600
        
        return buildString {
            appendLine("═══ 豆芽守护周报 👨‍👩‍👧 ═══")
            appendLine("📅 ${stats.weekStartDate.format(dateFormatter)} - ${stats.weekStartDate.plusDays(6).format(dateFormatter)}")
            appendLine()
            appendLine("📊 本周概况:")
            appendLine("   日均手机使用: ${dailyAvgHours}小时")
            
            val level = when {
                dailyAvgHours < 2 -> "健康"
                dailyAvgHours < 4 -> "正常"
                dailyAvgHours < 6 -> "需要关注"
                else -> "建议沟通"
            }
            appendLine("   评估等级: $level")
            appendLine()
            
            appendLine("💡 科学建议:")
            when {
                dailyAvgHours < 2 -> {
                    appendLine("   ✓ 使用情况良好，给予肯定和鼓励")
                    appendLine("   ✓ 继续保持健康的数字生活习惯")
                }
                dailyAvgHours < 4 -> {
                    appendLine("   • 使用时长在合理范围内")
                    appendLine("   • 建议共同制定周末使用计划")
                }
                dailyAvgHours < 6 -> {
                    appendLine("   ⚠ 使用时长偏长，建议温和沟通")
                    appendLine("   ⚠ 了解是否有特定原因（学习/社交压力）")
                    appendLine("   • 尝试约定每日"无手机时段"")
                }
                else -> {
                    appendLine("   🔴 使用时长较长，需要关注")
                    appendLine("   🔴 建议安排线下活动转移注意力")
                    appendLine("   • 避免指责，以关心角度沟通")
                    appendLine("   • 考虑启用豆芽的捣蛋干预功能")
                }
            }
            appendLine()
            
            appendLine("📱 数据说明:")
            appendLine("   本报告基于孩子手机本地数据分析生成")
            appendLine("   所有数据仅存储在孩子手机中，保护隐私")
            appendLine()
            appendLine("═══ 豆芽守护 ═══")
        }
    }
}
