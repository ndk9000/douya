package com.douya.antidigitaladdiction.analyzer

import com.douya.antidigitaladdiction.data.local.AppCategory

/**
 * 应用分类器
 * 基于包名和应用名称进行本地分类
 */
object AppClassifier {
    
    // 社交类应用关键词
    private val SOCIAL_KEYWORDS = setOf(
        "wechat", "qq", "tim", "momo", "soul", "zhihu", "weibo",
        "xiaohongshu", "douban", "tieba", "twitter", "facebook",
        "instagram", "snapchat", "whatsapp", "telegram", "discord"
    )
    
    // 游戏类应用关键词
    private val GAME_KEYWORDS = setOf(
        "game", "games", "gaming", "play", "honor", "arena",
        "royale", "pubg", "genshin", "impact", "mobile", "legends",
        "chess", "puzzle", "racing", "shooter", "strategy", "rpg",
        "mihoyo", "tencent", "netease", "4399", "taptap"
    )
    
    // 短视频类应用关键词
    private val SHORT_VIDEO_KEYWORDS = setOf(
        "douyin", "tiktok", "kuaishou", "kwai", "huoshan",
        "xigua", "haokan", "weishi", "bilibili", "acfun"
    )
    
    // 长视频类应用关键词
    private val VIDEO_KEYWORDS = setOf(
        "video", "tv", "movie", "film", "drama", "series",
        "iqiyi", "youku", "tencent", "mgtv", "sohu", "letv",
        "netflix", "hulu", "disney", "hbo", "prime"
    )
    
    // 资讯类应用关键词
    private val NEWS_KEYWORDS = setOf(
        "news", "headline", "toutiao", "ifeng", "netease",
        "sina", "sohu", "thepaper", "caixin", "36kr", "huxiu"
    )
    
    // 购物类应用关键词
    private val SHOPPING_KEYWORDS = setOf(
        "shop", "shopping", "mall", "store", "buy", "taobao",
        "tmall", "jd", "pdd", "duoduo", "meituan", "eleme",
        "dianping", "xiaohongshu", "dealmoon", "amazon"
    )
    
    // 学习类应用关键词
    private val EDUCATION_KEYWORDS = setOf(
        "edu", "education", "learn", "study", "course", "class",
        "school", "university", "mooc", "coursera", "edx",
        "xuetangx", "zhihuishu", "chaoxing", "yuketang",
        "brilliant", "duolingo", "memrise", "anki", "quizlet"
    )
    
    // 工具类应用关键词
    private val PRODUCTIVITY_KEYWORDS = setOf(
        "tool", "file", "doc", "sheet", "slide", "note",
        "calendar", "mail", "drive", "cloud", "scan",
        "wps", "office", "word", "excel", "powerpoint",
        "notion", "evernote", "todoist", "ticktick", "forest"
    )
    
    // 系统类应用关键词
    private val SYSTEM_KEYWORDS = setOf(
        "system", "settings", "launcher", "android", "google",
        "com.android", "com.google"
    )
    
    /**
     * 根据包名和应用名称分类应用
     */
    fun classify(packageName: String, appName: String): AppCategory {
        val lowerPackage = packageName.lowercase()
        val lowerName = appName.lowercase()
        
        return when {
            matchesAny(lowerPackage, lowerName, SHORT_VIDEO_KEYWORDS) -> AppCategory.SHORT_VIDEO
            matchesAny(lowerPackage, lowerName, SOCIAL_KEYWORDS) -> AppCategory.SOCIAL
            matchesAny(lowerPackage, lowerName, GAME_KEYWORDS) -> AppCategory.GAME
            matchesAny(lowerPackage, lowerName, VIDEO_KEYWORDS) -> AppCategory.VIDEO
            matchesAny(lowerPackage, lowerName, NEWS_KEYWORDS) -> AppCategory.NEWS
            matchesAny(lowerPackage, lowerName, SHOPPING_KEYWORDS) -> AppCategory.SHOPPING
            matchesAny(lowerPackage, lowerName, EDUCATION_KEYWORDS) -> AppCategory.EDUCATION
            matchesAny(lowerPackage, lowerName, PRODUCTIVITY_KEYWORDS) -> AppCategory.PRODUCTIVITY
            matchesAny(lowerPackage, lowerName, SYSTEM_KEYWORDS) -> AppCategory.SYSTEM
            else -> AppCategory.OTHER
        }
    }
    
    /**
     * 批量分类应用列表
     */
    fun classifyApps(apps: List<Pair<String, String>>): Map<String, AppCategory> {
        return apps.associate { (packageName, appName) ->
            packageName to classify(packageName, appName)
        }
    }
    
    /**
     * 更新分类规则（用户自定义）
     */
    fun updateCustomRules(packageName: String, category: AppCategory) {
        // 保存到本地配置
        CustomRules.addRule(packageName, category)
    }
    
    private fun matchesAny(packageName: String, appName: String, keywords: Set<String>): Boolean {
        return keywords.any { 
            packageName.contains(it) || appName.contains(it) 
        }
    }
}

/**
 * 用户自定义分类规则
 */
object CustomRules {
    private val rules = mutableMapOf<String, AppCategory>()
    
    fun addRule(packageName: String, category: AppCategory) {
        rules[packageName] = category
    }
    
    fun getRule(packageName: String): AppCategory? {
        return rules[packageName]
    }
    
    fun removeRule(packageName: String) {
        rules.remove(packageName)
    }
    
    fun getAllRules(): Map<String, AppCategory> = rules.toMap()
}
