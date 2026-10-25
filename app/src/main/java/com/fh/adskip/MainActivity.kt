package com.fh.adskip

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var sw: Switch
    private lateinit var status: TextView
    private lateinit var counter: TextView
    private val handler = Handler(Looper.getMainLooper())

    private val refresh = object : Runnable {
        override fun run() {
            val on = AdSkipVpnService.running
            if (sw.isChecked != on) sw.isChecked = on
            if (on) {
                status.text = "状态：运行中 · 全自动跳广已生效"
                status.setTextColor(getColor(R.color.accent2))
            } else {
                status.text = "状态：未开启"
                status.setTextColor(getColor(R.color.text2))
            }
            val blocked = AdSkipVpnService.prefs(this@MainActivity).getLong("blocked", 0)
            counter.text = "已拦截广告域名请求：$blocked 次"
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sw = findViewById(R.id.switch_vpn)
        status = findViewById(R.id.tv_status)
        counter = findViewById(R.id.tv_counter)
        findViewById<Button>(R.id.btn_open_settings).setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_VPN_SETTINGS))
        }

        sw.setOnCheckedChangeListener { _, checked ->
            if (checked) startVpn() else stopVpn()
        }

        if (AdSkipVpnService.prefs(this).getBoolean("enabled", false) &&
            VpnService.prepare(this) == null
        ) {
            launchService()
        }
    }

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
            status.text = "状态：未授权 VPN，无法拦截"
        }
    }

    private fun launchService() {
        AdSkipVpnService.prefs(this).edit().putBoolean("enabled", true).apply()
        startForegroundService(Intent(this, AdSkipVpnService::class.java))
        status.text = "状态：启动中…"
    }

    private fun stopVpn() {
        AdSkipVpnService.prefs(this).edit().putBoolean("enabled", false).apply()
        stopService(Intent(this, AdSkipVpnService::class.java))
        status.text = "状态：已关闭"
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