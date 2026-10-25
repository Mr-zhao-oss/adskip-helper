package com.fh.adskip

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * 只保留真实生效的功能，没有任何演示页：
 *  1) DNS 白名单拦截（VpnService）——除提现/支付/系统外，所有域名解析一律拒绝
 *  2) 广告页自动关闭（Accessibility）——命中 关闭/跳过/×/领取 直接点击
 * 计数与记录全部来自真实运行数据。
 */
class MainActivity : Activity() {

    private lateinit var sw: Switch
    private lateinit var tvState: TextView
    private lateinit var tvBlocked: TextView
    private lateinit var tvAcc: TextView
    private lateinit var tvClosed: TextView
    private lateinit var logBox: LinearLayout

    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            val on = AdSkipVpnService.running
            if (sw.isChecked != on) sw.isChecked = on
            tvState.text = if (on) "● 拦截中" else "● 未开启"
            tvState.setTextColor(if (on) 0xFF22C55E.toInt() else 0xFF8A97A8.toInt())

            tvBlocked.text = "已拦截域名请求：${AdSkipVpnService.prefs(this@MainActivity).getLong("blocked", 0)} 次"

            val accOn = AdCloseService.enabledFromSettings(this@MainActivity)
            tvAcc.text = if (accOn) "已开启" else "未开启（去系统设置勾选「广告页自动关闭」）"
            tvAcc.setTextColor(if (accOn) 0xFF22C55E.toInt() else 0xFF8A97A8.toInt())
            tvClosed.text = "已自动关闭广告页：${AdCloseService.closedCount(this@MainActivity)} 次"

            renderLogs()
            handler.postDelayed(this, 1500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sw = findViewById(R.id.sw_vpn)
        tvState = findViewById(R.id.tv_state)
        tvBlocked = findViewById(R.id.tv_blocked)
        tvAcc = findViewById(R.id.tv_acc)
        tvClosed = findViewById(R.id.tv_closed)
        logBox = findViewById(R.id.log_box)

        sw.setOnCheckedChangeListener { _, checked ->
            if (checked) startVpn() else stopVpn()
        }
        findViewById<Button>(R.id.btn_vpn_settings).setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
        }
        findViewById<Button>(R.id.btn_acc_settings).setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btn_clear).setOnClickListener {
            BlockLog.clear(this)
            renderLogs()
            Toast.makeText(this, "已清空", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderLogs() {
        logBox.removeAllViews()
        val items = BlockLog.list(this)
        if (items.isEmpty()) {
            logBox.addView(TextView(this).apply {
                text = "暂无记录"
                setTextColor(0xFF8A97A8.toInt())
                textSize = 12f
            })
            return
        }
        for (it in items.take(60)) {
            logBox.addView(TextView(this).apply {
                text = "${BlockLog.timeOf(it.ts)}   ${it.host}"
                setTextColor(0xFF8A97A8.toInt())
                textSize = 12f
                setPadding(0, 5, 0, 5)
            })
        }
    }

    private fun startVpn() {
        val prep = VpnService.prepare(this)
        if (prep != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(prep, REQ_VPN)
        } else launchService()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_VPN) return
        if (resultCode == RESULT_OK) launchService() else sw.isChecked = false
    }

    private fun launchService() {
        AdSkipVpnService.prefs(this).edit().putBoolean("enabled", true).apply()
        startForegroundService(Intent(this, AdSkipVpnService::class.java))
    }

    private fun stopVpn() {
        AdSkipVpnService.prefs(this).edit().putBoolean("enabled", false).apply()
        stopService(Intent(this, AdSkipVpnService::class.java))
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
    }
}