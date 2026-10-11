package com.xiaokan.colorbox;

import android.util.Log;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * libxposed API 102 入口（META-INF/xposed/java_init.list）。
 *
 * 在 system_server 装配常驻截屏链路与多功能组；
 * 在作用域应用内装配应用级解锁与包级功能。
 */
public final class HookEntry extends XposedModule {

    private static final String TAG = "ColorBox";

    public HookEntry() {
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        super.onModuleLoaded(param);
        this.log(Log.INFO, TAG, "loaded in " + param.getProcessName());
        try {
            Cfg.setRemote(ModernHooker.remoteOf(this, "cfg"));
        } catch (Throwable t) {
            this.log(Log.WARN, TAG, "remote prefs unavailable", t);
        }
    }

    @Override
    public void onSystemServerStarting(
            XposedModuleInterface.SystemServerStartingParam param) {
        super.onSystemServerStarting(param);
        Installer.installSystemServer(new ModernHooker(this), param.getClassLoader());
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        Installer.installPackage(new ModernHooker(this),
                param.getPackageName(), param.getClassLoader());
    }
}
