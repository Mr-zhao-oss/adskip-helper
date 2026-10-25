package com.fh.adskip;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

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
public class AdSkipVpnService extends VpnService {

    public static final String TUN_ADDR = "10.111.0.2";
    public static final String FAKE_DNS = "10.111.222.222";
    public static final String UPSTREAM_DNS = "223.5.5.5";
    public static final String CHANNEL_ID = "adskip_vpn";
    public static final int NOTI_ID = 1;
    public static final String ACTION_STOP = "com.fh.adskip.STOP";

    public static volatile boolean running = false;
    public static final AtomicLong blockedCount = new AtomicLong(0);
    public static final AtomicLong passedCount = new AtomicLong(0);

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("adskip", Context.MODE_PRIVATE);
    }

    private ParcelFileDescriptor tun;
    private volatile boolean stopped = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        startForeground(NOTI_ID, buildNotification());
        ParcelFileDescriptor fd = establish();
        if (fd == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        tun = fd;
        stopped = false;
        running = true;
        blockedCount.set(prefs(this).getLong("blocked", 0));
        new Thread(() -> loop(fd), "dns-filter").start();
        return START_STICKY;
    }

    @Override
    public void onRevoke() {
        stopEngine();
    }

    @Override
    public void onDestroy() {
        stopEngine();
    }

    private void stopEngine() {
        stopped = true;
        running = false;
        try {
            if (tun != null) tun.close();
        } catch (Exception ignored) {
        }
        tun = null;
    }

    private ParcelFileDescriptor establish() {
        if (tun != null) return tun;
        try {
            return new Builder()
                    .setSession("跳广助手")
                    .setMtu(1500)
                    .addAddress(TUN_ADDR, 32)
                    .addDnsServer(FAKE_DNS)
                    .addRoute(FAKE_DNS, 32)
                    .setBlocking(true)
                    .establish();
        } catch (Exception e) {
            return null;
        }
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_channel),
                NotificationManager.IMPORTANCE_LOW));

        PendingIntent open = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(
                this, 1,
                new Intent(this, AdSkipVpnService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_secure)
                .setContentTitle("跳广运行中 · 全自动跳广告")
                .setContentText("已拦截广告域名请求 " + blockedCount.get() + " 次")
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(null, "停止", stop).build())
                .setOngoing(true)
                .build();
    }

    /** TUN 读包 -> DNS 过滤 -> 写回 */
    private void loop(ParcelFileDescriptor fd) {
        FileInputStream input = new FileInputStream(fd.getFileDescriptor());
        FileOutputStream output = new FileOutputStream(fd.getFileDescriptor());
        byte[] buf = new byte[32767];

        while (!stopped) {
            int n;
            try {
                n = input.read(buf);
            } catch (Exception e) {
                break;
            }
            if (n < 40) continue; // IPv4(20)+UDP(8)+DNS头(12)

            byte[] reply = handlePacket(buf, n);
            if (reply == null) continue;
            try {
                output.write(reply);
            } catch (Exception ignored) {
            }
        }
        try {
            input.close();
        } catch (Exception ignored) {
        }
        try {
            output.close();
        } catch (Exception ignored) {
        }
    }

    /** 解析 IPv4/UDP/DNS，返回写回 TUN 的完整 IP 包；非 DNS 或坏包返回 null */
    private byte[] handlePacket(byte[] pkt, int len) {
        if (len < 28) return null;
        int version = (pkt[0] >> 4) & 0xF;
        if (version != 4) return null;

        int ihl = (pkt[0] & 0xF) * 4;
        if (ihl < 20 || len < ihl + 8) return null;
        if ((pkt[9] & 0xFF) != 17) return null; // 只处理 UDP

        byte[] srcIp = new byte[]{pkt[12], pkt[13], pkt[14], pkt[15]};
        byte[] dstIp = new byte[]{pkt[16], pkt[17], pkt[18], pkt[19]};
        int sport = ((pkt[ihl] & 0xFF) << 8) | (pkt[ihl + 1] & 0xFF);
        int dport = ((pkt[ihl + 2] & 0xFF) << 8) | (pkt[ihl + 3] & 0xFF);
        if (dport != 53) return null;

        int dnsLen = len - ihl - 8;
        if (dnsLen < 12) return null;
        byte[] dns = new byte[dnsLen];
        System.arraycopy(pkt, ihl + 8, dns, 0, dnsLen);

        int[] span = new int[1];
        String qname = parseQuestion(dns, span);
        if (qname == null) return null;
        int qEnd = span[0];

        byte[] answer;
        if (AdHosts.isAdHost(qname)) {
            blockedCount.incrementAndGet();
            persist();
            answer = buildRcode(dns, qEnd, 3);
        } else {
            byte[] resp = forward(dns);
            answer = resp != null ? resp : buildRcode(dns, qEnd, 2);
        }

        return buildIpUdp(dstIp, srcIp, sport, answer);
    }

    /** 解析 DNS 问题区，返回域名；span[0] = question 结束偏移 */
    private String parseQuestion(byte[] dns, int[] span) {
        if (dns.length < 17) return null;
        int qdCount = ((dns[4] & 0xFF) << 8) | (dns[5] & 0xFF);
        if (qdCount != 1) return null;

        int p = 12;
        StringBuilder sb = new StringBuilder();
        while (p < dns.length) {
            int labelLen = dns[p] & 0xFF;
            if (labelLen == 0) {
                p++;
                break;
            }
            if ((labelLen & 0xC0) != 0) return null;
            if (p + 1 + labelLen > dns.length) return null;
            for (int i = 1; i <= labelLen; i++) {
                int c = dns[p + i] & 0xFF;
                if (c == 0) return null;
                sb.append((char) c);
            }
            sb.append('.');
            p += 1 + labelLen;
        }
        // QTYPE(2) + QCLASS(2)
        if (p + 4 > dns.length) return null;
        span[0] = p + 4;
        return sb.toString().toLowerCase();
    }

    /** 构造带指定 RCODE 的响应（3=NXDOMAIN, 2=SERVFAIL），保留 header+question */
    private byte[] buildRcode(byte[] query, int qEnd, int rcode) {
        byte[] out = new byte[qEnd];
        System.arraycopy(query, 0, out, 0, qEnd);
        out[2] = (byte) ((out[2] & 0xFF) | 0x80);         // QR=1
        out[3] = (byte) (0x80 | (rcode & 0x0F));          // RA + RCODE
        out[6] = 0; out[7] = 0;                           // ANCOUNT
        out[8] = 0; out[9] = 0;                           // NSCOUNT
        out[10] = 0; out[11] = 0;                         // ARCOUNT
        return out;
    }

    /** 普通域名转发上游 DNS */
    private byte[] forward(byte[] dns) {
        try (DatagramSocket s = new DatagramSocket()) {
            protect(s);
            s.setSoTimeout(3000);
            s.send(new DatagramPacket(dns, dns.length, new InetSocketAddress(UPSTREAM_DNS, 53)));
            byte[] rx = new byte[4096];
            DatagramPacket pkt = new DatagramPacket(rx, rx.length);
            s.receive(pkt);
            if (pkt.getLength() <= 0) return null;
            byte[] out = new byte[pkt.getLength()];
            System.arraycopy(rx, 0, out, 0, out.length);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    /** 构造回给 app 的完整 IPv4/UDP 包（源=DNS，目的=app） */
    private byte[] buildIpUdp(byte[] srcIp, byte[] dstIp, int dstPort, byte[] payload) {
        int total = 20 + 8 + payload.length;
        ByteBuffer out = ByteBuffer.allocate(total);

        out.put(0, (byte) 0x45);                // version 4, IHL 5
        out.put(1, (byte) 0);
        out.putShort(2, (short) total);         // total length
        out.putShort(4, (short) 0);
        out.putShort(6, (short) 0x4000);        // DF
        out.put(8, (byte) 64);                  // TTL
        out.put(9, (byte) 17);                  // UDP
        out.putShort(10, (short) 0);            // checksum placeholder
        out.position(12);
        out.put(srcIp);
        out.position(16);
        out.put(dstIp);

        // IP 头校验和
        int sum = 0;
        for (int i = 0; i < 20; i += 2) {
            sum += ((out.get(i) & 0xFF) << 8) | (out.get(i + 1) & 0xFF);
            while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        }
        out.putShort(10, (short) (~sum & 0xFFFF));

        // UDP 头
        out.putShort(20, (short) 53);
        out.putShort(22, (short) dstPort);
        out.putShort(24, (short) (8 + payload.length));
        out.putShort(26, (short) 0);            // IPv4 UDP 校验和可置 0
        out.position(28);
        out.put(payload);

        return out.array();
    }

    private void persist() {
        prefs(this).edit()
                .putLong("blocked", blockedCount.get())
                .putBoolean("was_on", true)
                .apply();
    }
}