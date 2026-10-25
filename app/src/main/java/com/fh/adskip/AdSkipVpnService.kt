package com.fh.adskip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * 无 root 广告拦截核心。
 *
 * 只把"假 DNS 服务器 IP"路由进 TUN，其余流量完全不经过 VPN（零网速损失）：
 *   设备 -> DNS 10.111.222.222:53 -> TUN -> 本服务
 *   - 广告域名：直接回 NXDOMAIN，广告 SDK 拉不到素材即失败
 *   - 普通域名：转发阿里 DNS 并回写结果
 *
 * 绝大多数小游戏拿不到广告时会立即关闭广告页或直接发奖，
 * 不需要模拟点击，不需要 root。
 */
class AdSkipVpnService : VpnService() {

    companion object {
        const val TUN_ADDR = "10.111.0.2"
        const val FAKE_DNS = "10.111.222.222"
        const val UPSTREAM_DNS = "223.5.5.5"
        const val CHANNEL_ID = "adskip_vpn"
        const val NOTI_ID = 1
        const val ACTION_STOP = "com.fh.adskip.STOP"

        @Volatile var running = false
            private set

        val blockedCount = AtomicLong(0)
        val passedCount = AtomicLong(0)

        fun prefs(ctx: android.content.Context) =
            ctx.getSharedPreferences("adskip", android.content.Context.MODE_PRIVATE)
    }

    private var tun: ParcelFileDescriptor? = null
    @Volatile private var stopped = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTI_ID, buildNotification())
        val fd = establish()
        if (fd == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        tun = fd
        stopped = false
        running = true
        blockedCount.set(prefs(this).getLong("blocked", 0))
        Thread({ loop(fd) }, "dns-filter").start()
        return START_STICKY
    }

    override fun onRevoke() = stopEngine()

    override fun onDestroy() = stopEngine()

    private fun stopEngine() {
        stopped = true
        running = false
        try { tun?.close() } catch (_: Exception) {}
        tun = null
    }

    private fun establish(): ParcelFileDescriptor? {
        tun?.let { return it }
        return try {
            VpnService.Builder()
                .setSession("跳广助手")
                .setMtu(1500)
                .addAddress(TUN_ADDR, 32)
                .addDnsServer(FAKE_DNS)
                .addRoute(FAKE_DNS, 32)
                .setBlocking(true)
                .establish()
        } catch (_: Exception) {
            null
        }
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.vpn_channel_desc) }
        )

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, AdSkipVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentTitle("跳广运行中 · 全自动跳广告")
            .setContentText("已拦截广告域名请求 ${blockedCount.get()} 次")
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "停止", stop).build())
            .setOngoing(true)
            .build()
    }

    /** TUN 读包 -> DNS 过滤 -> 写回 */
    private fun loop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buf = ByteArray(32767)

        while (!stopped) {
            val n = try {
                input.read(buf)
            } catch (_: Exception) {
                break
            }
            if (n < 40) continue  // IPv4(20)+UDP(8)+DNS 头(12)

            val reply = handlePacket(buf, n) ?: continue
            try {
                output.write(reply)
            } catch (_: Exception) {}
        }
        try { input.close() } catch (_: Exception) {}
        try { output.close() } catch (_: Exception) {}
    }

    /** 解析 IPv4/UDP/DNS，返回写回 TUN 的完整 IP 包；非 DNS 或坏包返回 null */
    private fun handlePacket(pkt: ByteArray, len: Int): ByteArray? {
        if (len < 28) return null
        val version = (pkt[0].toInt() shr 4) and 0xF
        if (version != 4) return null

        val ihl = (pkt[0].toInt() and 0xF) * 4
        if (ihl < 20 || len < ihl + 8) return null
        if ((pkt[9].toInt() and 0xFF) != 17) return null  // 只处理 UDP

        val srcIp = byteArrayOf(pkt[12], pkt[13], pkt[14], pkt[15])
        val dstIp = byteArrayOf(pkt[16], pkt[17], pkt[18], pkt[19])
        val sport = ((pkt[ihl].toInt() and 0xFF) shl 8) or (pkt[ihl + 1].toInt() and 0xFF)
        val dport = ((pkt[ihl + 2].toInt() and 0xFF) shl 8) or (pkt[ihl + 3].toInt() and 0xFF)
        if (dport != 53) return null

        val dnsLen = len - ihl - 8
        if (dnsLen < 12) return null
        val dns = ByteArray(dnsLen)
        System.arraycopy(pkt, ihl + 8, dns, 0, dnsLen)

        val parsed = parseQuestion(dns) ?: return null
        val qname = parsed.first
        val qEnd = parsed.second

        val answer = if (AdHosts.isAdHost(qname)) {
            blockedCount.incrementAndGet()
            persist()
            buildNxdomain(dns, qEnd)
        } else {
            val resp = forward(dns)
            if (resp == null) buildServFail(dns, qEnd) else resp
        }

        return buildIpUdp(srcIp = dstIp, dstIp = srcIp, dstPort = sport, payload = answer)
    }

    /**
     * 解析 DNS 问题区，返回 [域名, question 结束偏移]。
     * 只接受单问题、非压缩指针的查询。
     */
    private fun parseQuestion(dns: ByteArray): Pair<String, Int>? {
        if (dns.size < 17) return null
        val qdCount = ((dns[4].toInt() and 0xFF) shl 8) or (dns[5].toInt() and 0xFF)
        if (qdCount != 1) return null

        var p = 12
        val sb = StringBuilder()
        while (p < dns.size) {
            val labelLen = dns[p].toInt() and 0xFF
            if (labelLen == 0) {
                p++
                break
            }
            if (labelLen and 0xC0 != 0) return null
            if (p + 1 + labelLen > dns.size) return null
            for (i in 1..labelLen) {
                val c = dns[p + i].toInt() and 0xFF
                if (c == 0) return null
                sb.append(c.toChar())
            }
            sb.append('.')
            p += 1 + labelLen
        }
        // QTYPE(2) + QCLASS(2)
        if (p + 4 > dns.size) return null
        return sb.toString().trimEnd('.').lowercase() to (p + 4)
    }

    /** 广告域名 -> NXDOMAIN，保留 header 与 question 区 */
    private fun buildNxdomain(query: ByteArray, qEnd: Int): ByteArray {
        val out = ByteArray(qEnd)
        System.arraycopy(query, 0, out, 0, qEnd)
        out[2] = ((out[2].toInt() and 0xFF) or 0x80).toByte()  // QR=1，保留 RD
        out[3] = 0x83.toByte()                                // RA=1 | RCODE=3(NXDOMAIN)
        out[6] = 0; out[7] = 0                                // ANCOUNT
        out[8] = 0; out[9] = 0                                // NSCOUNT
        out[10] = 0; out[11] = 0                              // ARCOUNT
        return out
    }

    /** 上游失败 -> SERVFAIL(2)，避免游戏一直转圈 */
    private fun buildServFail(query: ByteArray, qEnd: Int): ByteArray {
        val out = buildNxdomain(query, qEnd)
        out[3] = ((out[3].toInt() and 0xF0) or 0x02).toByte()
        return out
    }

    /** 普通域名转发上游 DNS */
    private fun forward(dns: ByteArray): ByteArray? = try {
        DatagramSocket().use { s ->
            protect(s)
            s.soTimeout = 3000
            s.send(DatagramPacket(dns, dns.size, InetSocketAddress(UPSTREAM_DNS, 53)))
            val rx = ByteArray(4096)
            val pkt = DatagramPacket(rx, rx.size)
            s.receive(pkt)
            if (pkt.length > 0) rx.copyOf(pkt.length) else null
        }
    } catch (_: Exception) {
        null
    }

    /** 构造回给 app 的完整 IPv4/UDP 包（源=DNS，目的=app） */
    private fun buildIpUdp(
        srcIp: ByteArray,
        dstIp: ByteArray,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val total = 20 + 8 + payload.size
        val out = ByteBuffer.allocate(total)

        out.put(0, 0x45.toByte())              // version 4, IHL 5
        out.put(1, 0.toByte())
        out.putShort(2, total.toShort())       // total length
        out.putShort(4, 0)
        out.put(6, 0x4000.toShort())           // DF
        out.put(8, 64.toByte())                // TTL
        out.put(9, 17.toByte())                // UDP
        out.putShort(10, 0)                    // checksum placeholder
        out.position(12); out.put(srcIp)
        out.position(16); out.put(dstIp)

        // IP 头校验和
        var sum = 0
        for (i in 0 until 20 step 2) {
            sum += ((out.get(i).toInt() and 0xFF) shl 8) or (out.get(i + 1).toInt() and 0xFF)
            while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        }
        out.putShort(10, (sum.inv() and 0xFFFF).toShort())

        // UDP 头
        out.putShort(20, 53)
        out.putShort(22, dstPort.toShort())
        out.putShort(24, (8 + payload.size).toShort())
        out.putShort(26, 0)                    // IPv4 UDP 校验和可置 0
        out.position(28); out.put(payload)

        return out.array()
    }

    private fun persist() {
        prefs(this).edit()
            .putLong("blocked", blockedCount.get())
            .putBoolean("was_on", true)
            .apply()
    }
}