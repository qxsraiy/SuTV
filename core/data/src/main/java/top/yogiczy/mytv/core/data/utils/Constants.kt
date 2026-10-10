package top.yogiczy.mytv.core.data.utils

import top.yogiczy.mytv.core.data.entities.epgsource.EpgSource
import top.yogiczy.mytv.core.data.entities.epgsource.EpgSourceList
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSource
import top.yogiczy.mytv.core.data.entities.iptvsource.IptvSourceList
import top.yogiczy.mytv.core.data.entities.vod.VodSource
import top.yogiczy.mytv.core.data.entities.vod.VodSourceList

/**
 * 常量
 */
object Constants {
    /**
     * 应用 标题
     */
    const val APP_TITLE = "天光云影"

    /**
     * 应用 代码仓库
     */
    const val APP_REPO = "https://github.com/yaoxieyoulei/mytv-android"

    /**
     * 交流群 telegram
     */
    const val GROUP_TELEGRAM = "https://t.me/mytv_android"

    /**
     * 直播源
     */
    val IPTV_SOURCE_LIST = IptvSourceList(
        listOf(
            IptvSource(
                name = "咪咕直播 (内置)",
                url = "http://localhost:10481/migu.m3u",
            ),
            IptvSource(
                name = "IPTV默认源 (Guovin)",
                url = "https://raw.githubusercontent.com/Guovin/iptv-api/gd/output/result.m3u",
            ),
            IptvSource(
                name = "myIPTV IPv4",
                url = "https://raw.githubusercontent.com/suxuang/myIPTV/refs/heads/main/ipv4.m3u",
            ),
            IptvSource(
                name = "BigBigGrandG 集合源",
                url = "https://raw.githubusercontent.com/BigBigGrandG/IPTV-URL/release/Gather.m3u",
            ),
            IptvSource(
                name = "茶客源 (vamoschuck)",
                url = "https://raw.githubusercontent.com/vamoschuck/TV/main/M3U",
            ),
            IptvSource(
                name = "YanG 集合源",
                url = "https://raw.githubusercontent.com/YanG-1989/m3u/main/Gather.m3u",
            ),
        )
    )

    /**
     * 直播源缓存时间（毫秒）
     */
    const val IPTV_SOURCE_CACHE_TIME = 1000 * 60 * 60 * 24L // 24小时

    /**
     * 节目单来源
     */
    val EPG_SOURCE_LIST = EpgSourceList(
        listOf(
            EpgSource(
                name = "默认节目单 fanmingming",
                url = "https://live.fanmingming.com/e.xml",
            ),
        )
    )

    /**
     * 节目单刷新时间阈值（小时）
     */
    const val EPG_REFRESH_TIME_THRESHOLD = 2 // 不到2点不刷新

    /**
     * 频道图标提供
     *
     * {name} 频道名称
     *
     * {name|lowercase} 转成小写
     *
     * {name|uppercase} 转成大写
     *
     */
    const val CHANNEL_LOGO_PROVIDER = "https://live.fanmingming.com/tv/{name|uppercase}.png"

    /**
     * GitHub加速代理地址
     */
    const val GITHUB_PROXY = "https://ghp.ci/"

    /**
     * Git最新版本信息
     */
    val GIT_RELEASE_LATEST_URL = mapOf<String, String>()

    /**
     * 网络请求重试次数
     */
    const val NETWORK_RETRY_COUNT = 10L

    /**
     * 网络请求重试间隔时间（毫秒）
     */
    const val NETWORK_RETRY_INTERVAL = 3000L

    /**
     * 播放器 userAgent
     */
    const val VIDEO_PLAYER_USER_AGENT = "okhttp"

    /**
     * 播放器加载超时
     */
    const val VIDEO_PLAYER_LOAD_TIMEOUT = 1000L * 15 // 15秒

    /**
     * 日志历史最大保留条数
     */
    const val LOG_HISTORY_MAX_SIZE = 100

    /**
     * 点播采集源 (苹果CMS API)
     */
    val VOD_SOURCE_LIST = VodSourceList(
        listOf(
            VodSource("bfzy", "暴风资源", "https://bfzyapi.com/api.php/provide/vod", "clean"),
            VodSource("360zy", "360资源", "https://360zy.com/api.php/provide/vod", "clean", true),
            VodSource("guangsu", "光速资源", "https://api.guangsuapi.com/api.php/provide/vod", "clean"),
            VodSource("maoyan", "猫眼资源", "https://api.maoyanapi.top/api.php/provide/vod", "clean"),
            VodSource("wujin", "无尽资源", "https://api.wujinapi.me/api.php/provide/vod", "clean"),
            VodSource("iqiyi", "爱奇艺资源", "https://iqiyizyapi.com/api.php/provide/vod", "clean"),
            VodSource("hongniu", "红牛资源", "https://www.hongniuzy2.com/api.php/provide/vod", "clean"),
            VodSource("huya", "虎牙资源", "https://www.huyaapi.com/api.php/provide/vod", "clean"),
            VodSource("suoni", "索尼资源", "https://suoniapi.com/api.php/provide/vod", "clean"),
            VodSource("tyys", "天翼资源", "https://tyyszyapi.com/api.php/provide/vod", "clean"),
            VodSource("jszy", "JS资源", "https://jszyapi.com/api.php/provide/vod", "clean"),
            VodSource("dytt", "电影天堂", "http://caiji.dyttzyapi.com/api.php/provide/vod", "clean"),
            VodSource("xlapi", "新浪资源", "http://api.xinlangapi.com/xinlangapi.php/provide/vod", "clean"),
            VodSource("bdzy", "百度资源", "https://api.apibdzy.com/api.php/provide/vod", "clean"),
            VodSource("niuniu", "牛牛资源", "https://api.niuniuzy.me/api.php/provide/vod", "clean"),
            VodSource("zuida", "最大资源", "https://api.zuidapi.com/api.php/provide/vod", "clean"),
            VodSource("dbzy", "DB资源", "https://caiji.dbzy5.com/api.php/provide/vod", "clean"),
            VodSource("kczy", "快车资源", "https://caiji.kuaichezy.org/api.php/provide/vod", "clean"),
            VodSource("lzi", "LZI资源", "https://cj.lziapi.com/api.php/provide/vod", "clean"),
            VodSource("rycj", "RY资源", "https://cj.rycjapi.com/api.php/provide/vod", "clean"),
            VodSource("hhzy", "HH资源", "https://hhzyapi.com/api.php/provide/vod", "clean"),
            VodSource("yhzy", "YH资源", "https://m3u8.apiyhzy.com/api.php/provide/vod", "clean"),
            VodSource("mdzy", "MD资源", "https://www.mdzyapi.com/api.php/provide/vod", "clean"),
            VodSource("sdzy", "SD资源", "http://sdzyapi.com/api.php/provide/vod", "clean"),
        )
    )

    /**
     * 界面 临时频道界面显示时间
     */
    const val UI_TEMP_CHANNEL_SCREEN_SHOW_DURATION = 2000L // 2秒

    /**
     * 界面 超时未操作自动关闭界面
     */
    const val UI_SCREEN_AUTO_CLOSE_DELAY = 1000L * 15 // 15秒

    /**
     * 界面 时间显示前后范围
     */
    const val UI_TIME_SCREEN_SHOW_DURATION = 1000L * 30 // 前后30秒
}