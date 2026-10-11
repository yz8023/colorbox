package com.xiaokan.colorbox;

import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;

/**
 * hook 侧配置读取，带 2 秒缓存。
 *
 * 读取顺序：
 *   1. HookStateProvider（app 进程可用时，设置改动约 2 秒内生效）
 *   2. 入口注入的远端偏好读取器（system_server 等无 Application 进程的回退路径）
 *   3. 首次完全无数据时按 FeatureKeys 默认值引导，保证行为可预期
 *
 * 键风格：b_<key> 布尔 / i_<key> 整型 / s_<key> 字符串，由 Provider 扁平化导出。
 */
public final class Cfg {

    /** 远端偏好读取器接口：由双入口各自实现注入 */
    public interface Remote {
        Bundle read(String key);
    }

    public static final Uri STATE_URI =
            Uri.parse("content://com.xiaokan.colorbox.hookstate");

    private static Remote remote;
    private static android.content.ContentResolver resolver;
    private static long lastFetch;
    private static Bundle cache = new Bundle();

    public static void setRemote(Remote r) {
        remote = r;
    }

    private static void refresh() {
        long now = SystemClock.uptimeMillis();
        if (now - lastFetch < 2000) {
            return;
        }
        lastFetch = now;
        Bundle b = null;
        try {
            android.content.ContentResolver r = cr();
            if (r != null) {
                b = r.call(STATE_URI, "cfgall", null, null);
            }
        } catch (Throwable ignored) {
        }
        if (b == null || b.isEmpty()) {
            try {
                if (remote != null) {
                    b = remote.read("cfgall");
                }
            } catch (Throwable ignored) {
            }
        }
        if (b != null && !b.isEmpty()) {
            cache = b;
            return;
        }
        if (!cache.containsKey("bootstrapped")) {
            cache.putBoolean("bootstrapped", true);
            for (String[] s : FeatureKeys.SWITCHES) {
                cache.putBoolean("b_" + s[0], (Boolean) FeatureKeys.defaultValue(s[0]));
            }
            for (String k : FeatureKeys.INT_PARAMS) {
                cache.putInt("i_" + k, (Integer) FeatureKeys.defaultValue(k));
            }
            for (String k : FeatureKeys.STR_PARAMS) {
                cache.putString("s_" + k, (String) FeatureKeys.defaultValue(k));
            }
        }
    }

    private static android.content.ContentResolver cr() {
        if (resolver != null) {
            return resolver;
        }
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (app instanceof android.content.Context) {
                resolver = ((android.content.Context) app).getContentResolver();
                return resolver;
            }
        } catch (Throwable ignored) {
        }
        try {
            Object at = Class.forName("android.app.ActivityThread")
                    .getMethod("currentActivityThread").invoke(null);
            if (at != null) {
                Object ctx = at.getClass().getMethod("getSystemContext").invoke(at);
                if (ctx instanceof android.content.Context) {
                    resolver = ((android.content.Context) ctx).getContentResolver();
                }
            }
        } catch (Throwable ignored) {
        }
        return resolver;
    }

    /** 功能开关读取；总门控关闭时一律返回 false */
    public static boolean on(String key) {
        refresh();
        if (!cache.getBoolean("b_" + FeatureKeys.MASTER, true)) {
            return false;
        }
        return cache.getBoolean("b_" + key,
                (Boolean) FeatureKeys.defaultValue(key));
    }

    public static int num(String key) {
        refresh();
        Object d = FeatureKeys.defaultValue(key);
        return cache.getInt("i_" + key, d instanceof Integer ? (Integer) d : 0);
    }

    public static String text(String key) {
        refresh();
        Object d = FeatureKeys.defaultValue(key);
        return cache.getString("s_" + key, d instanceof String ? (String) d : "");
    }

    private Cfg() {
    }
}
