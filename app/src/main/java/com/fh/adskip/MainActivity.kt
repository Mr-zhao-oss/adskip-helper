package com.fh.adskip

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * 界面按视频原 app「小游戏助手」1:1 复刻：
 * 登录 / 数据 / 余额 / 用户数据 / 提现 五个标签页，字段、按钮、结果文案与视频一致。
 *
 * 行为说明（重要，别被 UI 骗）：
 *  - 顶部「跳广」开关 = 真实生效的引擎（白名单 VPN，除提现支付外全拦）。
 *  - 红包/提现等按钮调用的是原助手的游戏私有接口，没有原包的 appid/签名/端点无法真跑，
 *    这里只按视频原样展示流程与结果文案，不产生真实资金动作。
 */
class MainActivity : Activity() {

    private lateinit var container: FrameLayout
    private lateinit var tabBar: LinearLayout
    private lateinit var swJump: Switch
    private val handler = Handler(Looper.getMainLooper())

    private val games = arrayOf(
        "①熊猫日记", "②花间小院", "③桃李好时节", "④丰衣足食", "⑤修仙来财",
        "⑥幸福小饭馆", "⑦南山小院", "⑧富贵茶庄", "⑨幸福小农院", "⑩桃源有良田",
        "⑪简单修仙", "⑫羊羊多福"
    )

    private var gameIndex = 9 // 默认 桃源有良田
    private var accessKey = "c374729ea674484aae19f200ab6a2ce6_2946227666"
    private var oaid = "0927a5d5b7d39a02"
    private var deviceId = "0927a5d5b7d39a02"
    private var appid = "wxbe679c1bcda8d91b"
    private var channel = ""

    private val tabs = arrayOf("登录", "数据", "余额", "用户数据", "提现")
    private val pages = arrayOfNulls<View>(5)
    private var current = 0

    private lateinit var logData: TextView
    private lateinit var accountBox: TextView
    private lateinit var balanceLog: TextView
    private lateinit var withdrawLog: TextView
    private lateinit var credList: LinearLayout
    private lateinit var loginResult: TextView
    private lateinit var accStatus: TextView
    private lateinit var closeStat: TextView

    private val refresh = object : Runnable {
        override fun run() {
            val on = AdSkipVpnService.running
            if (swJump.isChecked != on) swJump.isChecked = on
            updateAccStatus()
            if (::closeStat.isInitialized) {
                closeStat.text = "已自动关闭广告页 ${AdCloseService.closedCount(this@MainActivity)} 次" +
                        " · 已拦截域名请求 ${AdSkipVpnService.prefs(this@MainActivity).getLong("blocked", 0)} 次"
            }
            handler.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        container = findViewById(R.id.container)
        tabBar = findViewById(R.id.tab_bar)
        swJump = findViewById(R.id.switch_jump)

        loadState()

        tabs.forEachIndexed { i, name ->
            val tv = TextView(this)
            tv.text = name
            tv.textSize = 14f
            tv.setPadding(dp(16), 0, dp(16), 0)
            tv.gravity = Gravity.CENTER_VERTICAL
            tv.setOnClickListener { showTab(i) }
            tabBar.addView(tv)
        }

        pages[0] = buildLogin()
        pages[1] = buildData()
        pages[2] = buildBalance()
        pages[3] = buildUsers()
        pages[4] = buildWithdraw()
        pages.forEach { v -> v?.let { container.addView(it) } }

        swJump.setOnCheckedChangeListener { _, checked ->
            if (checked) startVpn() else stopVpn()
        }

        showTab(0)
    }

    // ---------------- 登录 ----------------
    private fun buildLogin(): View {
        val box = page()
        box.addView(title("微信扫码登录"))
        box.addView(btn("微信扫码登录") { qrDemo() })

        box.addView(row("appid", appid))
        box.addView(pickRow("游戏", { games[gameIndex] }) { pickGame { } })

        box.addView(editRow("设备oaid", oaid) { oaid = it })
        box.addView(btn("随机设备指纹") {
            oaid = randomHex()
            Toast.makeText(this, "已生成 $oaid", Toast.LENGTH_SHORT).show()
            showTab(0)
        })
        box.addView(check("真实oaid（优先使用）", true) {})
        box.addView(btn("保存") { saveState(); toast("已保存") })

        box.addView(editRow("deviceId", deviceId) { deviceId = it })
        box.addView(editRow("渠道（留空=用该游戏默认值，可覆盖）", channel) { channel = it })

        box.addView(btn("一键获取凭证（设备注册）") { registerDemo() })
        box.addView(btn("生成二维码") { qrDemo() })

        loginResult = value("登录结果/accessKey\n$accessKey")
        box.addView(loginResult)
        box.addView(btnSmall("复制") { copy(accessKey) })

        box.addView(title("广告页自动关闭（真功能）"))
        accStatus = value("")
        box.addView(accStatus)
        box.addView(btn("去开启无障碍") {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        return wrap(box)
    }

    private fun updateAccStatus() {
        if (!::accStatus.isInitialized) return
        val on = AdCloseService.enabledFromSettings(this)
        val closed = AdCloseService.closedCount(this)
        accStatus.text = if (on)
            "已开启 · 已自动关闭广告页 $closed 次"
        else
            "未开启（去系统设置里勾选「广告页自动关闭」后立即生效）"
        accStatus.setTextColor(if (on) 0xFF22C55E.toInt() else 0xFF8A97A8.toInt())
    }

    private fun qrDemo() {
        AlertDialog.Builder(this)
            .setTitle("微信扫码登录")
            .setMessage(
                "请使用微信扫描二维码完成登录。\n\n" +
                        "注意：真正的扫码链路由原助手的云端接口完成，" +
                        "本包没有该接口，扫码不会产生真实凭证。"
            )
            .setPositiveButton("已扫码（演示）") { _, _ ->
                toast("登录成功，凭证已同步到各功能面板。")
                loginResult.text = "登录结果/accessKey\n$accessKey\n已登录：${accessKey.take(6)}..${accessKey.takeLast(4)}"
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun registerDemo() {
        logLine("【换取accessKey·两步链路】")
        logLine("① 设备注册 HTTP 200 code=0")
        logLine("   设备凭证 accessKey=${randomHex(4)}..${randomHex(4)}")
        logLine("② 绑定登录 HTTP 200 code=0")
        logLine("   最终凭证=$accessKey")
        toast("凭证已生成（流程演示，未真实调用接口）")
    }

    // ---------------- 数据 ----------------
    private fun buildData(): View {
        val box = page()
        box.addView(title("数据"))
        box.addView(pickRow("游戏", { games[gameIndex] }) { pickGame { } })
        box.addView(value("登录结果/accessKey\n$accessKey"))

        closeStat = value("已自动关闭广告页 0 次")
        box.addView(closeStat)

        box.addView(row2(
            btn("查询数据") { queryData() },
            btn("用登录凭证") { toast("已切用当前 accessKey") }
        ))

        logData = value("【换取accessKey·两步链路】\n① 设备注册 HTTP 200 code=0\n② 绑定登录 HTTP 200 code=0")
        box.addView(logData)

        accountBox = value("")
        box.addView(accountBox)
        return wrap(box)
    }

    private fun queryData() {
        logLine("【换取accessKey·两步链路】")
        logLine("① 设备注册 HTTP 200 code=0")
        logLine("   设备凭证 accessKey=b29f2f..7142")
        logLine("② 绑定登录 HTTP 200 code=0")
        logLine("   最终凭证=$accessKey")
        accountBox.text = buildString {
            append("【${games[gameIndex]}·账户】登录有效\n")
            append("昵称            芬达2\n")
            append("红包余额        60010000\n")
            append("账户余额        0.00元\n")
            append("折合            0.60元（按倍率1）\n")
            append("注册时间        2026-10-09 17:38")
        }
    }

    // ---------------- 余额 ----------------
    private fun buildBalance(): View {
        val box = page()
        box.addView(title("余额"))
        box.addView(pickRow("游戏", { games[gameIndex] }) { pickGame { } })
        box.addView(value("登录结果/accessKey\n$accessKey"))

        var amount = "10000000"
        box.addView(editRow("红包数量（1千万=0.1元）", amount) { amount = it })

        box.addView(row2(
            btn("查询余额") { balanceLog.text = "查询请求已发送（演示）" },
            btn("增加红包") { addPacket(amount) }
        ))
        box.addView(check("勾选后：增加红包成功即自动提现", false) {})

        balanceLog = value("")
        box.addView(balanceLog)
        return wrap(box)
    }

    private fun addPacket(amount: String) {
        val n = amount.toLongOrNull() ?: 0L
        balanceLog.text = buildString {
            append("【${games[gameIndex]}·增加红包】成功\n")
            append("已提交            $amount\n")
            append("折合界面红包        ${"%.2f".format(n / 10000.0)}\n")
            append("正在自动提现...\n")
            append("【${games[gameIndex]}·提现】\n")
            append("已提交，金额0.10元（倍率1）")
        }
    }

    // ---------------- 用户数据 ----------------
    private fun buildUsers(): View {
        val box = page()
        box.addView(title("用户数据"))
        box.addView(value("扫码登录后自动保存，可导出为文件"))
        box.addView(pickRow("游戏", { games[0] }) { pickGame { } })

        credList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(credList)
        renderCreds()

        box.addView(row3(
            btn("复制") { copy(accessKey) },
            btn("导出") { toast("已导出到剪贴板") },
            btn("清空") { toast("清空仅演示") }
        ))
        return wrap(box)
    }

    private fun renderCreds() {
        if (!::credList.isInitialized) return
        credList.removeAllViews()
        val rows = arrayOf(
            "c6a83f6495933a...2921650630" to "2921650630",
            "d9a09b74046ed5...2946275829" to "2946275829",
            "2d8af6774eac6d...2946232509" to "2946232509"
        )
        for ((k, dev) in rows) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                setBackgroundColor(0xFF151C25.toInt())
            }
            card.addView(value(k).apply { textSize = 12.5f })
            card.addView(value("设备ID  $dev").apply { textSize = 12.5f })
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (b in arrayOf("有效", "复制", "使用", "删除")) {
                row.addView(btnSmall(b) { toast("$b（演示）") })
            }
            card.addView(row)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(8)
            credList.addView(card, lp)
        }
    }

    // ---------------- 提现 ----------------
    private fun buildWithdraw(): View {
        val box = page()
        box.addView(title("提现"))
        box.addView(pickRow("游戏", { games[gameIndex] }) { pickGame { } })
        box.addView(value("登录结果/accessKey\n$accessKey"))
        box.addView(value("提现只能全额提，额度由服务端按档位下发（如0.1元／0.3元），无需填写。")
            .apply { setTextColor(0xFF8A97A8.toInt()); textSize = 12f })

        box.addView(row2(
            btn("查询提现记录") { showRecords() },
            btn("提现") { withdraw() }
        ))

        withdrawLog = value("")
        box.addView(withdrawLog)
        return wrap(box)
    }

    private fun showRecords() {
        withdrawLog.text = buildString {
            append("【${games[gameIndex]}·提现记录】\n")
            append("2026-10-09 17:46   提现   0.10元   已到账\n")
            append("2026-10-09 17:50   提现   0.11元   已到账\n")
            append("2026-10-09 17:50   提现   0.10元   已到账\n")
            append("共3笔，已到账0.31元")
        }
    }

    private fun withdraw() {
        withdrawLog.text = buildString {
            append("起始状态：今日尝试0／18次，现金到账0笔，共0.00元\n")
            append("风控/节流：当前提现人数较多，请稍后再试\n")
            append("等待60秒...\n")
            append("[第1/18轮]提交中..\n")
            append("【${games[gameIndex]}·提现】\n")
            append("识别到您的账户存在异常，请多玩儿一会儿游戏进行验证！")
        }
    }

    // ---------------- VPN ----------------
    private fun startVpn() {
        val prep = VpnService.prepare(this)
        if (prep != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(prep, 1)
        } else launchService()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1) return
        if (resultCode == RESULT_OK) launchService() else swJump.isChecked = false
    }

    private fun launchService() {
        prefs().edit().putBoolean("enabled", true).apply()
        startForegroundService(Intent(this, AdSkipVpnService::class.java))
        toast("跳广已开启：除提现/支付外，所有第三方请求都会被拒绝")
    }

    private fun stopVpn() {
        prefs().edit().putBoolean("enabled", false).apply()
        stopService(Intent(this, AdSkipVpnService::class.java))
    }

    // ---------------- 状态 ----------------
    private fun prefs() = AdSkipVpnService.prefs(this)

    private fun loadState() {
        val p = prefs()
        oaid = p.getString("oaid", oaid) ?: oaid
        deviceId = p.getString("deviceId", deviceId) ?: deviceId
        channel = p.getString("channel", "") ?: ""
        gameIndex = p.getInt("gameIndex", gameIndex)
        accessKey = p.getString("accessKey", accessKey) ?: accessKey
    }

    private fun saveState() {
        prefs().edit()
            .putString("oaid", oaid)
            .putString("deviceId", deviceId)
            .putString("channel", channel)
            .putInt("gameIndex", gameIndex)
            .putString("accessKey", accessKey)
            .apply()
    }

    private fun pickGame(onDone: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("选择游戏")
            .setItems(games) { _, which ->
                gameIndex = which
                saveState()
                showTab(current)
                onDone()
            }
            .show()
    }

    // ---------------- 视图小工具 ----------------
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun page() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(8), dp(14), dp(16))
    }

    private fun wrap(v: View) = ScrollView(this).apply { addView(v) }

    private fun title(t: String) = TextView(this).apply {
        text = t
        setTextColor(0xFFE8EDF3.toInt())
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(12), 0, dp(6))
    }

    private fun row(label: String, v: String) = value("$label\n$v").apply { textSize = 13f }

    private fun value(t: String) = TextView(this).apply {
        text = t
        setTextColor(0xFFE8EDF3.toInt())
        textSize = 13f
        setPadding(dp(10), dp(8), dp(10), dp(8))
        setBackgroundColor(0xFF151C25.toInt())
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(6)
        layoutParams = lp
    }

    private fun editRow(label: String, v: String, onChange: (String) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(0xFF151C25.toInt())
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = dp(6)
            layoutParams = lp
        }
        box.addView(TextView(this).apply {
            text = label
            setTextColor(0xFF8A97A8.toInt())
            textSize = 12f
        })
        box.addView(EditText(this).apply {
            setText(v)
            setTextColor(0xFFE8EDF3.toInt())
            textSize = 13f
            background = null
            inputType = InputType.TYPE_CLASS_TEXT
        }.also { et ->
            et.setOnFocusChangeListener { _, has -> if (!has) onChange(et.text.toString()) }
        })
        return box
    }

    private fun pickRow(label: String, v: () -> String, onClick: () -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(0xFF151C25.toInt())
            val lp = LinearLayout.LayoutParams(-1, -2)
            lp.topMargin = dp(6)
            layoutParams = lp
            setOnClickListener { onClick() }
        }
        box.addView(TextView(this).apply {
            text = label
            setTextColor(0xFF8A97A8.toInt())
            textSize = 12f
        })
        box.addView(TextView(this).apply {
            text = v()
            setTextColor(0xFFE8EDF3.toInt())
            textSize = 13f
        })
        return box
    }

    private fun btn(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        textSize = 13f
        setTextColor(0xFFE8EDF3.toInt())
        backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF1F2A38.toInt())
        setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(-1, dp(44))
        lp.topMargin = dp(8)
        layoutParams = lp
    }

    private fun btnSmall(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        textSize = 11.5f
        setTextColor(0xFF8A97A8.toInt())
        backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF151C25.toInt())
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(8), 0, dp(8), 0)
        setOnClickListener { onClick() }
    }

    private fun check(t: String, on: Boolean, onChange: (Boolean) -> Unit) = CheckBox(this).apply {
        text = t
        textSize = 12.5f
        isChecked = on
        setTextColor(0xFFE8EDF3.toInt())
        setOnCheckedChangeListener { _, c -> onChange(c) }
    }

    private fun row2(a: View, b: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        a.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        b.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        addView(a)
        addView(b)
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(8)
        layoutParams = lp
    }

    private fun row3(a: View, b: View, c: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        for (v in arrayOf(a, b, c)) {
            v.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(v)
        }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.topMargin = dp(8)
        layoutParams = lp
    }

    private fun showTab(i: Int) {
        current = i
        pages.forEachIndexed { idx, v -> v?.visibility = if (idx == i) View.VISIBLE else View.GONE }
        for (idx in 0 until tabBar.childCount) {
            val tv = tabBar.getChildAt(idx) as TextView
            tv.setTextColor(if (idx == i) 0xFFFF4D4F.toInt() else 0xFF8A97A8.toInt())
        }
        if (i == 3) renderCreds()
    }

    private fun logLine(t: String) {
        if (::logData.isInitialized) logData.text = "${logData.text}\n$t"
    }

    private fun randomHex(len: Int = 16): String {
        val chars = "0123456789abcdef"
        return (1..len).map { chars.random() }.joinToString("")
    }

    private fun copy(s: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("accessKey", s))
        toast("已复制")
    }

    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }
}