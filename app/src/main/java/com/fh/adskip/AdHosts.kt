package com.fh.adskip

/**
 * 广告 SDK 域名黑名单。
 * 命中即返回 403 / 直接断开，广告 SDK 拉不到素材 -> 多数游戏会立即关闭广告页或直接发奖。
 * 关键词匹配为主，域名精确匹配为辅，避免误杀。
 */
object AdHosts {

    /** 精确匹配（含子域名自动覆盖） */
    val exact: Array<String> = arrayOf(
        // 穿山甲 / Pangle / CSJ
        "pglstatp-toutiao.com",
        "pangolin-sdk-toutiao.com",
        "ad.pglstatp-toutiao.com",
        "csjplatform.bytedance.com",
        "open.e.kuaishou.com",
        // 优量汇 / 广点通
        "gdt.qq.com",
        "pgdt.gtimg.com",
        "adx.gdt.qq.com",
        "e.qq.com",
        // 百度联盟
        "mobads.baidu.com",
        "mobads-logs.baidu.com",
        "cpro.baidu.com",
        "afd.baidu.com",
        // 快手
        "ksad.kuaishou.com",
        "ad.kuaishou.com",
        // AMS / 芒果 / 美团
        "ad.mgtv.com",
        // 讯飞 / 阿里妈妈
        "alimama.com",
        "atm.alibaba.com",
        "tanx.com",
        // 汇量 / Mintegral
        "mintegral.com",
        "dsp.mintegral.com",
        "hb.mintegral.com",
        // Sigmob / 倍孜 / 点金石
        "sigmob.com",
        "ad.sigmob.com",
        "beizi.biz",
        "adnet.beizi.biz",
        // 云图 / 伽马 / 其他聚合
        "adn.360.cn",
        "miaozhen.com",
        "irs01.com",
        "umeng.com",
        "growingio.com",
        // 海外常见（部分游戏集成）
        "admob.com",
        "googleads.g.doubleclick.net",
        "applovin.com",
        "unityads.unity3d.com",
        "ironsrc.mobi"
    )

    /** 只要域名包含这些关键词就拦截 */
    val keywords: Array<String> = arrayOf(
        "pglstatp", "pangolin", "csj", "bytedance.com/union",
        "gdt", "guangdiantong", "mobads", "cpro",
        "ksad", "kuaishou.com/ad",
        "mintegral", "sigmob", "beizi", "adn.",
        "adx.", "adservice", "adserver",
        "doubleclick", "applovin", "unityads"
    )

    /** 判断 host 是否属于广告域名 */
    fun isAdHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val h = host.lowercase().trimEnd('.')
        for (e in exact) {
            if (h == e || h.endsWith(".$e")) return true
        }
        for (k in keywords) {
            if (h.contains(k)) return true
        }
        return false
    }
}