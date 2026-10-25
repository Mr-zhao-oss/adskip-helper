package com.fh.adskip;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 拦截记录（数据页展示用），最多保留 120 条 */
public final class BlockLog {

    public static final class Item {
        public final String host;
        public final long ts;
        Item(String host, long ts) { this.host = host; this.ts = ts; }
    }

    private static final int MAX = 120;

    private BlockLog() {}

    private static SharedPreferences sp(Context c) {
        return AdSkipVpnService.prefs(c);
    }

    public static synchronized void append(Context c, String host) {
        try {
            JSONArray arr = load(c);
            JSONObject o = new JSONObject();
            o.put("h", host);
            o.put("t", System.currentTimeMillis());
            arr.put(o);
            while (arr.length() > MAX) arr.remove(0);
            sp(c).edit().putString("logs", arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static synchronized void clear(Context c) {
        sp(c).edit().remove("logs").putLong("blocked", 0).apply();
    }

    public static synchronized List<Item> list(Context c) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray arr = load(c);
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Item(o.optString("h"), o.optLong("t")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static String timeOf(long ts) {
        return new SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(new Date(ts));
    }

    private static JSONArray load(Context c) {
        try {
            return new JSONArray(sp(c).getString("logs", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }
}