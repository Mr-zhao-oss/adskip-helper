package com.fh.adskip

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 广告页自动关闭（真功能，不依赖任何私有接口）。
 *
 * 小游戏看广告领奖时，屏幕上会出现「关闭 / 跳过 / × / 领取」等控件。
 * 本服务监听窗口变化，命中这些控件就直接点击，实现广告页秒关。
 *
 * 只在用户手动开启无障碍后生效；节流 350ms，防误点。
 */
class AdCloseService : AccessibilityService() {

    private var lastClick = 0L

    companion object {
        private val CLOSE_WORDS = arrayOf(
            "关闭", "跳过", "跳过广告", "关闭广告", "×", "✕", "X", "skip", "close",
            "领取", "领取奖励", "确定", "知道了", "稍后", "以后再说", "我再想想"
        )

        const val PREFS = "adskip"

        fun closedCount(ctx: Context): Long =
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("closed", 0)

        /** 服务是否已在系统无障碍里启用 */
        fun isEnabled(ctx: Context): Boolean {
            val am = ctx.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                ?: return false
            return am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo?.serviceInfo?.packageName == ctx.packageName }
        }

        fun enabledFromSettings(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return flat.split(':').any {
                it.startsWith("${ctx.packageName}/") && it.contains("AdCloseService")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val now = SystemClock.uptimeMillis()
        if (now - lastClick < 350) return

        val root = rootInActiveWindow ?: return
        val hit = findTarget(root, 0) ?: return
        if (click(hit)) {
            lastClick = now
            val sp = getSharedPreferences(PREFS, MODE_PRIVATE)
            sp.edit().putLong("closed", sp.getLong("closed", 0) + 1).apply()
        }
    }

    private fun click(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        var guard = 0
        while (n != null && guard++ < 6) {
            if (n.isClickable) {
                return n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            n = n.parent
        }
        return false
    }

    private fun findTarget(node: AccessibilityNodeInfo, depth: Int): AccessibilityNodeInfo? {
        if (depth > 40) return null
        val label = buildString {
            node.text?.let { append(it) }
            node.contentDescription?.let { append(' ').append(it) }
        }.trim()

        if (label.isNotEmpty()) {
            val lower = label.lowercase()
            for (w in CLOSE_WORDS) {
                if (lower == w.lowercase() || (w.length > 1 && lower.contains(w.lowercase()))) {
                    if (node.isVisibleToUser) return node
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findTarget(child, depth + 1)?.let { return it }
        }
        return null
    }

    override fun onInterrupt() {}
}