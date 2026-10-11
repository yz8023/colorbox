package com.xiaokan.colorbox;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * 跨进程配置通道（exported Provider，LSPosed / FPA / LSPatch 全兼容）。
 *
 * hook 侧协议：
 *   call("enabled", 目标包名) → { v: boolean }   该作用域是否被用户停用
 *   call("cfgall", null)      → 全量配置（b_/i_/s_ 前缀扁平键）
 *
 * 数据源：
 *   prefs "cfg"     —— 功能开关与参数（明文键存储）
 *   prefs "settings"—— hook_master 总门控 与 hook_<pkg> 作用域级停用
 */
public class HookStateProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle out = new Bundle();
        Context c = getContext();
        if (c == null) {
            return out;
        }
        try {
            SharedPreferences cfg = c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
            SharedPreferences settings = c.getSharedPreferences("settings", Context.MODE_PRIVATE);
            if ("enabled".equals(method) && arg != null) {
                out.putBoolean("v", settings.getBoolean("hook_" + arg, true)
                        && settings.getBoolean(FeatureKeys.MASTER, true));
            } else if ("cfgall".equals(method)) {
                export(cfg, settings, out);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void export(SharedPreferences cfg, SharedPreferences settings, Bundle b) {
        b.putBoolean("b_" + FeatureKeys.MASTER, settings.getBoolean(FeatureKeys.MASTER, true));
        for (String[] s : FeatureKeys.SWITCHES) {
            b.putBoolean("b_" + s[0], cfg.getBoolean(s[0],
                    (Boolean) FeatureKeys.defaultValue(s[0])));
        }
        for (String k : FeatureKeys.INT_PARAMS) {
            b.putInt("i_" + k, cfg.getInt(k, (Integer) FeatureKeys.defaultValue(k)));
        }
        for (String k : FeatureKeys.STR_PARAMS) {
            b.putString("s_" + k, cfg.getString(k, (String) FeatureKeys.defaultValue(k)));
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        return 0;
    }
}
