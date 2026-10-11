package com.xiaokan.colorbox;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 经典 XposedBridge 入口（assets/xposed_init），
 * 服务不支持 libxposed API 102 的框架；语义与 HookEntry 完全一致。
 */
public final class LegacyEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log("ColorBox: legacy entry loaded in " + lpparam.packageName);
        Cfg.setRemote(key -> {
            try {
                de.robv.android.xposed.XSharedPreferences p =
                        new de.robv.android.xposed.XSharedPreferences(
                                "com.xiaokan.colorbox", "cfg");
                p.reload();
                return RemoteCfg.flatten(new RemoteCfg.Reader() {
                    @Override
                    public boolean contains(String k) {
                        return p.contains(k);
                    }

                    @Override
                    public boolean getBoolean(String k, boolean def) {
                        return p.getBoolean(k, def);
                    }

                    @Override
                    public int getInt(String k, int def) {
                        return p.getInt(k, def);
                    }

                    @Override
                    public String getString(String k, String def) {
                        return p.getString(k, def);
                    }
                }, key);
            } catch (Throwable t) {
                return null;
            }
        });
        LegacyHooker hk = new LegacyHooker();
        if ("android".equals(lpparam.packageName)) {
            Installer.installSystemServer(hk, lpparam.classLoader);
        } else {
            Installer.installPackage(hk, lpparam.packageName, lpparam.classLoader);
        }
    }
}
