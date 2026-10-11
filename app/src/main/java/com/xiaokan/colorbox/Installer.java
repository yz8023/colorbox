package com.xiaokan.colorbox;

/**
 * 双入口共享的功能装配：system_server 常驻链路 + 应用内分发。
 */
final class Installer {

    static void installSystemServer(Hooker hk, ClassLoader cl) {
        try {
            CaptureHooks.deoptimizeSystemServer(hk, cl);
        } catch (Throwable t) {
            hk.log("deoptimize system server failed", t);
        }
        try {
            CaptureHooks.installSystemServer(hk, cl);
        } catch (Throwable t) {
            hk.log("capture system server failed", t);
        }
        try {
            MultiFeatures.installSystemServer(hk, cl);
        } catch (Throwable t) {
            hk.log("multi-features system server failed", t);
        }
    }

    static void installPackage(Hooker hk, String pkg, ClassLoader cl) {
        try {
            CaptureHooks.installForPackage(hk, pkg, cl);
        } catch (Throwable t) {
            hk.log("capture package failed in " + pkg, t);
        }
        try {
            MultiFeatures.onPackage(hk, pkg, cl);
        } catch (Throwable t) {
            hk.log("multi-features package failed in " + pkg, t);
        }
    }

    private Installer() {
    }
}
