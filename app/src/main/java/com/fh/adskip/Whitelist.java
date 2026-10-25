package com.fh.adskip;

/**
 * 白名单模式：只有这些域名的解析被放行，其余全部拒绝。
 *
 * 依据：这类小游戏本体不联网，只有「提现」时才需要网络（微信/支付宝/银联支付域）。
 * 所以不需要知道广告域名，任何第三方的解析直接失败，广告 SDK 一律拉不到素材。
 */
public final class Whitelist {

    /** 后缀匹配：命中即放行（含子域名） */
    private static final String[] ALLOW = new String[]{
            // 微信 / 微信支付（提现主通道）
            "weixin.qq.com",
            "wx.qq.com",
            "wechat.com",
            "wechatpay.com",
            "tenpay.com",
            "tng.qq.com",
            "wxpay.com",
            "servicewechat.com",
            "weixinbridge.com",
            // 支付宝
            "alipay.com",
            "alipayobjects.com",
            "alipay.cn",
            "alipaydev.com",
            "mapi.alipay.com",
            // 银联
            "unionpay.com",
            "unionpaysecure.com",
            "95516.com",
            // 系统基础（时间同步 / 连通性检测 / 网络探测）
            "time.android.com",
            "time.windows.com",
            "ntp.org.cn",
            "ntp.aliyun.com",
            "ntp1.aliyun.com",
            "ntp2.aliyun.com",
            "connectivitycheck.gstatic.com",
            "connect.rom.miui.com",
            "connectivitycheck.platform.hicloud.com",
            "www.msftconnecttest.com",
            "msftconnecttest.com"
    };

    private Whitelist() {}

    public static boolean isAllowed(String host) {
        if (host == null || host.isEmpty()) return false;
        String h = host.toLowerCase();
        for (String a : ALLOW) {
            if (h.equals(a) || h.endsWith("." + a)) return true;
        }
        return false;
    }
}