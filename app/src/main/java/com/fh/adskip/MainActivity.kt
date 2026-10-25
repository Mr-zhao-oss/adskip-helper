package com.fh.adskip

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var sw: Switch
    private lateinit var swWhite: Switch
    private lateinit var topState: TextView
    private lateinit var counter: TextView
    private lateinit var totalBlocked: TextView
    private lateinit var estMoney: TextView
    private lateinit var tvAccount: TextView
    private lateinit var logContainer: LinearLayout
    private lateinit var pageHome: View
    private lateinit var pageData: View
    private lateinit var pageMine: View
    private lateinit var tabHome: TextView
    private lateinit var tabData: TextView
    private lateinit var tabMine: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var bound = false

    private val refresh = object : Runnable {
        override fun run() {
            val on = AdSkipVpnService.running
            if (sw.isChecked != on) sw.isChecked = on
            if (on) {
                topState.text = "● 运行中"
                topState.setTextColor(getColor(R.color.accent2))
            } else {
                topState.text = "● 未开启"
                topState.setTextColor(getColor(R.color.text2))
            }
            val blocked = AdSkipVpnService.prefs(this@MainActivity).getLong("blocked", 0)
            counter.text = "已跳过：$blocked 次"
            totalBlocked.text = "$blocked 次"
            estMoney.text = String.format("¥%.2f", blocked * 0.1)
            if (visibleTab == TAB_DATA) renderLogs(blocked)
            handler.postDelayed(this, 1500)
        }
    }

    private var visibleTab = TAB_HOME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sw = findViewById(R.id.switch_vpn)
        swWhite = findViewById(R.id.switch_white)
        topState = findViewById(R.id.tv_top_state)
        counter = findViewById(R.id.tv_counter)
        totalBlocked = findViewById(R.id.tv_total_blocked)
        estMoney = findViewById(R.id.tv_est_money)
        tvAccount = findViewById(R.id.tv_account)
        logContainer = findViewById(R.id.log_container)
        pageHome = findViewById(R.id.page_home)
        pageData = findViewById(R.id.page_data)
        pageMine = findViewById(R.id.page_mine)
        tabHome = findViewById(R.id.tab_home)
        tabData = findViewById(R.id.tab_data)
        tabMine = findViewById(R.id.tab_mine)

        bound = AdSkipVpnService.prefs(this).getBoolean("bound", false)
        tvAccount.text = if (bound) "示例账号 · 已绑定" else "未绑定"

        findViewById<Button>(R.id.btn_login).setOnClickListener {
            bound = !bound
            AdSkipVpnService.prefs(this).edit().putBoolean("bound", bound).apply()
            tvAccount.text = if (bound) "示例账号 · 已绑定" else "未绑定"
            Toast.makeText(this, if (bound) "已模拟绑定（仅演示登录流程）" else "已解绑", Toast.LENGTH_SHORT).show()
        }

        swWhite.isChecked = AdSkipVpnService.prefs(this).getString("mode", "white") == "white"
        swWhite.setOnCheckedChangeListener { _, checked ->
            AdSkipVpnService.prefs(this).edit()
                .putString("mode", if (checked) "white" else "black").apply()
            restartIfRunning()
        }

        tabHome.setOnClickListener { showTab(TAB_HOME) }
        tabData.setOnClickListener { showTab(TAB_DATA) }
        tabMine.setOnClickListener { showTab(TAB_MINE) }

        findViewById<Button>(R.id.btn_open_settings).setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
        }
        findViewById<Button>(R.id.btn_clear).setOnClickListener {
            BlockLog.clear(this)
            renderLogs(0)
            Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
        }

        sw.setOnCheckedChangeListener { _, checked ->
            if (checked) startVpn() else stopVpn()
        }

        showTab(TAB_HOME)
    }

    private fun showTab(tab: Int) {
        visibleTab = tab
        pageHome.visibility = if (tab == TAB_HOME) View.VISIBLE else View.GONE
        pageData.visibility = if (tab == TAB_DATA) View.VISIBLE else View.GONE
        pageMine.visibility = if (tab == TAB_MINE) View.VISIBLE else View.GONE
        val on = getColor(R.color.accent)
        val off = getColor(R.color.text2)
        tabHome.setTextColor(if (tab == TAB_HOME) on else off)
        tabData.setTextColor(if (tab == TAB_DATA) on else off)
        tabMine.setTextColor(if (tab == TAB_MINE) on else off)
        if (tab == TAB_DATA) {
            renderLogs(AdSkipVpnService.prefs(this).getLong("blocked", 0))
        }
    }

    private fun renderLogs(blocked: Long) {
        logContainer.removeAllViews()
        totalBlocked.text = "$blocked 次"
        val items = BlockLog.list(this)
        if (items.isEmpty()) {
            logContainer.addView(label("暂无记录，开启开关后进游戏试试").apply {
                setTextColor(getColor(R.color.text2))
                textSize = 13f
            })
            return
        }
        for (it in items) {
            val row = TextView(this)
            row.text = "${BlockLog.timeOf(it.ts)}   ${it.host}"
            row.setTextColor(getColor(R.color.text2))
            row.textSize = 12.5f
            row.setPadding(0, 8, 0, 8)
            logContainer.addView(row)
        }
    }

    private fun label(t: String) = TextView(this).apply { text = t }

    private fun startVpn() {
        val prep = VpnService.prepare(this)
        if (prep != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(prep, REQ_VPN)
        } else {
            launchService()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_VPN) return
        if (resultCode == RESULT_OK) {
            launchService()
        } else {
            sw.isChecked = false
            Toast.makeText(this, "未授权 VPN，无法拦截", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchService() {
        AdSkipVpnService.prefs(this).edit().putBoolean("enabled", true).apply()
        startForegroundService(Intent(this, AdSkipVpnService::class.java))
    }

    private fun stopVpn() {
        AdSkipVpnService.prefs(this).edit().putBoolean("enabled", false).apply()
        stopService(Intent(this, AdSkipVpnService::class.java))
    }

    private fun restartIfRunning() {
        if (!AdSkipVpnService.running) return
        stopService(Intent(this, AdSkipVpnService::class.java))
        handler.postDelayed({
            if (AdSkipVpnService.prefs(this).getBoolean("enabled", false)) {
                startForegroundService(Intent(this, AdSkipVpnService::class.java))
            }
        }, 400)
    }

    override fun onResume() {
        super.onResume()
        handler.post(refresh)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refresh)
    }

    private companion object {
        const val REQ_VPN = 1
        const val TAB_HOME = 0
        const val TAB_DATA = 1
        const val TAB_MINE = 2
    }
}