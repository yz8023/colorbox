package com.xiaokan.colorbox;

import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 截屏/录屏解锁（常驻链路，无开关）。
 *
 * 按 LSPosed DisableFlagSecure 方案在 system_server 层解除 FLAG_SECURE /
 * Secure Layer 限制，覆盖 AOSP / ColorOS / HyperOS / One UI 路径；
 * 作用域内的截图组件与普通应用额外获得 SurfaceView / Transaction 级解除。
 *
 * 所有 hook 点通过 Hooker 接线，本类同时负责目标发现与决策逻辑。
 */
final class CaptureHooks {

    private static final String SYSTEMUI = "com.android.systemui";
    private static final String OPLUS_APPPLATFORM = "com.oplus.appplatform";
    private static final String OPLUS_SCREENSHOT = "com.oplus.screenshot";
    private static final String FLYME_SYSTEMUIEX = "com.flyme.systemuiex";
    private static final String MIUI_SCREENSHOT = "com.miui.screenshot";

    private static volatile int nativeHooks;

    private CaptureHooks() {
    }

    // ─────────────────────────── system_server ───────────────────────────

    static void installSystemServer(Hooker hk, ClassLoader cl) {
        if (Build.VERSION.SDK_INT >= 35) {
            try {
                Class<?> wms = DexLocator.cls(cl, "com.android.server.wm.WindowManagerService");
                for (Method m : DexLocator.methods(wms, "registerScreenRecordingCallback")) {
                    hk.hook(m, falseResult());
                }
            } catch (Throwable t) {
                hk.log("hook registerScreenRecordingCallback failed", t);
            }
        }
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                Class<?> atms = DexLocator.cls(cl, "com.android.server.wm.ActivityTaskManagerService");
                for (Method m : DexLocator.methods(atms, "registerScreenCaptureObserver")) {
                    hk.hook(m, nullResult());
                }
            } catch (Throwable t) {
                hk.log("hook registerScreenCaptureObserver failed", t);
            }
            try {
                Class<?> hyper = DexLocator.cls(cl, "com.android.server.wm.WindowManagerServiceImpl");
                for (Method m : DexLocator.methods(hyper, "notAllowCaptureDisplay")) {
                    hk.hook(m, falseResult());
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            hookCaptureArgs(hk, cl);
        } catch (Throwable t) {
            hk.log("hook CaptureArgs failed", t);
        }
        try {
            Class<?> builder = DexLocator.cls(cl,
                    "com.oplus.screenshot.OplusScreenCapture$CaptureArgs$Builder");
            Method setUid = DexLocator.method(builder, "setUid", long.class);
            if (setUid != null) {
                hk.hook(setUid, (thiz, args) -> {
                    args[0] = -1L;
                    return Hooker.PASS;
                });
            }
        } catch (Throwable t) {
            hk.log("hook OplusScreenCapture failed", t);
        }
        if (Build.VERSION.SDK_INT < 34) {
            try {
                Class<?> ams = DexLocator.cls(cl, "com.android.server.am.ActivityManagerService");
                Method check = DexLocator.method(ams, "checkPermission",
                        String.class, int.class, int.class);
                if (check != null) {
                    hk.hook(check, (thiz, args) -> {
                        if ("android.permission.CAPTURE_BLACKOUT_CONTENT".equals(args[0])) {
                            args[0] = "android.permission.READ_FRAME_BUFFER";
                        }
                        return Hooker.PASS;
                    });
                }
            } catch (Throwable t) {
                hk.log("hook AMS checkPermission failed", t);
            }
        }
        try {
            hookVirtualDisplayCreate(hk, cl);
        } catch (Throwable t) {
            hk.log("hook DisplayControl failed", t);
        }
        try {
            Class<?> vda = DexLocator.cls(cl, "com.android.server.display.VirtualDisplayAdapter");
            for (Method m : DexLocator.methods(vda, "createVirtualDisplayLocked")) {
                hk.hook(m, (thiz, args) -> {
                    int caller = args.length > 2 && args[2] instanceof Integer
                            ? (Integer) args[2] : -1;
                    if (caller >= 10000 && args.length > 1 && args[1] == null) {
                        return Hooker.PASS;
                    }
                    for (int i = 3; i < args.length; i++) {
                        if (args[i] instanceof Integer) {
                            args[i] = (Integer) args[i] | DisplayManager.VIRTUAL_DISPLAY_FLAG_SECURE;
                            break;
                        }
                    }
                    return Hooker.PASS;
                });
            }
        } catch (Throwable t) {
            hk.log("hook VirtualDisplayAdapter failed", t);
        }
        try {
            hookContainsSecureLayers(hk, cl);
        } catch (Throwable t) {
            hk.log("hook ScreenshotHardwareBuffer failed", t);
        }
        try {
            Class<?> oneui = DexLocator.cls(cl, "com.android.server.wm.WmScreenshotController");
            for (Method m : DexLocator.methods(oneui, "canBeScreenshotTarget")) {
                hk.hook(m, trueResult());
            }
        } catch (Throwable ignored) {
        }
        try {
            hookIsSecureLocked(hk, cl);
        } catch (Throwable t) {
            hk.log("hook WindowState.isSecureLocked failed", t);
        }
        try {
            Class<?> longshot = DexLocator.cls(cl, "com.android.server.wm.OplusLongshotMainWindow");
            for (Method m : DexLocator.methods(longshot, "hasSecure")) {
                hk.hook(m, falseResult());
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> wms = DexLocator.cls(cl, "com.android.server.wm.WindowManagerService");
            Hooker.Callback clearFlag = (thiz, args) -> {
                if (Binder.getCallingUid() < 10000) {
                    return Hooker.PASS;
                }
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof WindowManager.LayoutParams) {
                        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) args[i];
                        if ((lp.flags & WindowManager.LayoutParams.FLAG_SECURE) != 0) {
                            lp.flags &= ~WindowManager.LayoutParams.FLAG_SECURE;
                        }
                    }
                }
                return Hooker.PASS;
            };
            for (Method m : DexLocator.methods(wms, "relayoutWindow", "addWindow")) {
                hk.hook(m, clearFlag);
            }
        } catch (Throwable t) {
            hk.log("hook WMS flag clear failed", t);
        }
        try {
            Class<?> ar = DexLocator.cls(cl, "com.android.server.wm.ActivityRecord");
            for (Method m : DexLocator.methods(ar, "shouldUseAppThemeSnapshot")) {
                hk.hook(m, falseResult());
            }
        } catch (Throwable ignored) {
        }
    }

    /** 让关键路径退出 JIT 内联，保证 hook 生效（仅现代入口真正 deopt） */
    static void deoptimizeSystemServer(Hooker hk, ClassLoader cl) {
        deopt(hk, cl, "com.android.server.wm.WindowStateAnimator", "createSurfaceLocked");
        deopt(hk, cl, "com.android.server.wm.WindowManagerService", "relayoutWindow");
        for (int i = 0; i < 20; i++) {
            try {
                Class<?> lambda = cl.loadClass(
                        "com.android.server.wm.RootWindowContainer$$ExternalSyntheticLambda" + i);
                if (java.util.function.BiConsumer.class.isAssignableFrom(lambda)) {
                    deoptAll(hk, lambda, "accept");
                }
            } catch (ClassNotFoundException ignored) {
            }
            try {
                Class<?> inner = cl.loadClass("com.android.server.wm.DisplayContent$" + i);
                if (java.util.function.BiPredicate.class.isAssignableFrom(inner)) {
                    deoptAll(hk, inner, "test");
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
    }

    // ─────────────────────────── 作用域包 ───────────────────────────

    static void installForPackage(Hooker hk, String pkg, ClassLoader cl) {
        boolean captureSuite = SYSTEMUI.equals(pkg) || MIUI_SCREENSHOT.equals(pkg)
                || OPLUS_SCREENSHOT.equals(pkg) || OPLUS_APPPLATFORM.equals(pkg)
                || FLYME_SYSTEMUIEX.equals(pkg);
        if (captureSuite) {
            try {
                hookCaptureArgs(hk, cl);
            } catch (Throwable t) {
                hk.log("hook CaptureArgs(" + pkg + ") failed", t);
            }
            try {
                hookContainsSecureLayers(hk, cl);
            } catch (Throwable t) {
                hk.log("hook ScreenshotHardwareBuffer(" + pkg + ") failed", t);
            }
            if (OPLUS_SCREENSHOT.equals(pkg) && Build.VERSION.SDK_INT >= 35) {
                try {
                    Class<?> builder = DexLocator.cls(cl,
                            "com.oplus.screenshot.OplusScreenCapture$CaptureArgs$Builder");
                    Method setUid = DexLocator.method(builder, "setUid", long.class);
                    if (setUid != null) {
                        hk.hook(setUid, (thiz, args) -> {
                            args[0] = -1L;
                            return Hooker.PASS;
                        });
                    }
                } catch (Throwable t) {
                    hk.log("hook OplusScreenCapture(" + pkg + ") failed", t);
                }
            }
            return;
        }
        installAppUnlock(hk, cl);
    }

    /** 普通作用域应用：SurfaceView / Transaction / Builder / SurfacePackage 解除 */
    static void installAppUnlock(Hooker hk, ClassLoader cl) {
        try {
            Class<?> sv = DexLocator.cls(cl, "android.view.SurfaceView");
            Method setSecure = DexLocator.method(sv, "setSecure", boolean.class);
            if (setSecure != null) {
                hk.hook(setSecure, (thiz, args) -> {
                    args[0] = Boolean.FALSE;
                    return Hooker.PASS;
                });
            }
        } catch (Throwable t) {
            hk.log("hook SurfaceView.setSecure failed", t);
        }
        try {
            Class<?> tx = DexLocator.cls(cl, "android.view.SurfaceControl$Transaction");
            Method setSecure = DexLocator.method(tx, "setSecure",
                    SurfaceControl.class, boolean.class);
            if (setSecure != null) {
                hk.hook(setSecure, (thiz, args) -> {
                    args[1] = Boolean.FALSE;
                    return Hooker.PASS;
                });
            }
        } catch (Throwable t) {
            hk.log("hook Transaction.setSecure failed", t);
        }
        try {
            Class<?> builder = DexLocator.cls(cl, "android.view.SurfaceControl$Builder");
            for (Method m : builder.getDeclaredMethods()) {
                if ("setSecure".equals(m.getName()) && m.getParameterCount() == 1) {
                    hk.hook(m, (thiz, args) -> {
                        args[0] = Boolean.FALSE;
                        return Hooker.PASS;
                    });
                }
            }
        } catch (Throwable t) {
            hk.log("hook Builder.setSecure failed", t);
        }
        try {
            Class<?> sv = DexLocator.cls(cl, "android.view.SurfaceView");
            Class<?> pkgType = DexLocator.cls(cl,
                    "android.view.SurfaceControlViewHost$SurfacePackage");
            Method setChild = DexLocator.method(sv, "setChildSurfacePackage", pkgType);
            hookNativeSurfacePackage(hk, cl);
            if (setChild != null) {
                hk.hook(setChild, new Hooker.Callback() {
                    @Override
                    public Object before(Object thiz, Object[] args) {
                        return Hooker.PASS;
                    }

                    @Override
                    public Object after(Object thiz, Object[] args, Object result) {
                        if (nativeHooks == 0) {
                            hookNativeSurfacePackage(hk, cl);
                        }
                        if (args[0] != null) {
                            clearSecureDelayed(hk, args[0], 0);
                        }
                        return null;
                    }
                });
            }
        } catch (Throwable t) {
            hk.log("hook setChildSurfacePackage failed", t);
        }
    }

    /** 抖音等把页面放进 native SurfacePackage 的场景：按类名直查替换 */
    private static void hookNativeSurfacePackage(Hooker hk, ClassLoader cl) {
        try {
            Class<?> pkgType = DexLocator.cls(cl,
                    "android.view.SurfaceControlViewHost$SurfacePackage");
            int hooked = 0;
            Class<?> target = DexLocator.cls(cl, "yyds.C3555");
            if (target != null) {
                for (Method m : target.getDeclaredMethods()) {
                    for (Class<?> p : m.getParameterTypes()) {
                        if (p == pkgType) {
                            try {
                                hk.hook(m, falseResult());
                                hooked++;
                            } catch (Throwable ignored) {
                            }
                            break;
                        }
                    }
                }
            }
            nativeHooks = hooked;
        } catch (Throwable t) {
            hk.log("hook native SurfacePackage failed", t);
        }
    }

    /** SurfacePackage 的 secure 标记异步清除（300ms × 6 轮） */
    private static void clearSecureDelayed(Hooker hk, Object pkg, int round) {
        try {
            SurfaceControl sc =
                    ((SurfaceControlViewHost.SurfacePackage) pkg).getSurfaceControl();
            Class<?> txCls = Class.forName("android.view.SurfaceControl$Transaction");
            Object tx = txCls.getDeclaredConstructor().newInstance();
            Method setSecure = txCls.getDeclaredMethod("setSecure",
                    SurfaceControl.class, boolean.class);
            setSecure.setAccessible(true);
            setSecure.invoke(tx, sc, Boolean.FALSE);
            txCls.getMethod("apply").invoke(tx);
            if (round < 6) {
                new Handler(Looper.getMainLooper()).postDelayed(
                        () -> clearSecureDelayed(hk, pkg, round + 1), 300);
            }
        } catch (Throwable ignored) {
        }
    }

    // ─────────────────────────── 决策与工具 ───────────────────────────

    private static void hookCaptureArgs(Hooker hk, ClassLoader cl) throws Exception {
        Class<?> argsClass;
        Class<?> captureClass;
        try {
            captureClass = DexLocator.cls(cl, "android.window.ScreenCaptureInternal");
            argsClass = DexLocator.cls(cl, "android.window.ScreenCaptureInternal$CaptureArgs");
        } catch (Throwable e) {
            try {
                captureClass = DexLocator.cls(cl, "android.window.ScreenCapture");
                argsClass = DexLocator.cls(cl, "android.window.ScreenCapture$CaptureArgs");
            } catch (Throwable e2) {
                captureClass = SurfaceControl.class;
                argsClass = DexLocator.cls(cl, "android.view.SurfaceControl$CaptureArgs");
            }
        }
        Field secureField;
        boolean intMode;
        try {
            secureField = argsClass.getDeclaredField("mSecureContentPolicy");
            intMode = true;
        } catch (NoSuchFieldException e) {
            secureField = argsClass.getDeclaredField("mCaptureSecureLayers");
            intMode = false;
        }
        secureField.setAccessible(true);
        final Field f = secureField;
        final boolean asInt = intMode;
        for (Method m : DexLocator.methods(captureClass, "nativeCaptureDisplay", "nativeCaptureLayers")) {
            hk.hook(m, (thiz, args) -> {
                Object captureArgs = args.length > 0 ? args[0] : null;
                try {
                    if (captureArgs != null) {
                        if (asInt) {
                            f.setInt(captureArgs, 1);
                        } else {
                            f.setBoolean(captureArgs, true);
                        }
                    }
                } catch (Throwable t) {
                    hk.log("CaptureArgs force failed", t);
                }
                return Hooker.PASS;
            });
        }
    }

    private static void hookContainsSecureLayers(Hooker hk, ClassLoader cl) throws Exception {
        String name = Build.VERSION.SDK_INT >= 34
                ? "android.window.ScreenCapture$ScreenshotHardwareBuffer"
                : "android.view.SurfaceControl$ScreenshotHardwareBuffer";
        Class<?> buffer = DexLocator.cls(cl, name);
        Method m = DexLocator.method(buffer, "containsSecureLayers");
        if (m != null) {
            hk.hook(m, falseResult());
        }
    }

    private static void hookVirtualDisplayCreate(Hooker hk, ClassLoader cl) throws Exception {
        Class<?> dc = Build.VERSION.SDK_INT >= 34
                ? DexLocator.cls(cl, "com.android.server.display.DisplayControl")
                : SurfaceControl.class;
        String name = Build.VERSION.SDK_INT >= 35 ? "createVirtualDisplay" : "createDisplay";
        Method m = dc.getDeclaredMethod(name, String.class, boolean.class);
        final ClassLoader systemCl = dc.getClassLoader();
        hk.hook(m, (thiz, args) -> {
            if (Build.VERSION.SDK_INT < 34 && !calledFromCreateVirtualDisplayLocked(systemCl)) {
                return Hooker.PASS;
            }
            args[1] = Boolean.TRUE;
            return Hooker.PASS;
        });
    }

    private static boolean calledFromCreateVirtualDisplayLocked(ClassLoader systemCl) {
        for (StackTraceElement frame : new Throwable().getStackTrace()) {
            if ("createVirtualDisplayLocked".equals(frame.getMethodName())) {
                try {
                    if (Class.forName(frame.getClassName(), false, systemCl)
                            .getClassLoader() == systemCl) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    /**
     * WindowState.isSecureLocked：仅当 Surface 创建路径
     * （createSurfaceLocked / setInitialSurfaceControlProperties）调用时放行原语义
     * （避免破坏 DRM），其余调用一律返回 false = 窗口不再按安全层处理。
     */
    private static void hookIsSecureLocked(Hooker hk, ClassLoader cl) throws Exception {
        Class<?> ws = DexLocator.cls(cl, "com.android.server.wm.WindowState");
        Method m = ws.getDeclaredMethod("isSecureLocked");
        final ClassLoader systemCl = ws.getClassLoader();
        hk.hook(m, (thiz, args) -> {
            if (Build.VERSION.SDK_INT >= 34) {
                java.lang.StackWalker walker = java.lang.StackWalker.getInstance(
                        java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE);
                boolean surfaceCreation = walker.walk(frames -> frames.anyMatch(f ->
                        f.getDeclaringClass() != null
                                && f.getDeclaringClass().getClassLoader() == systemCl
                                && ("setInitialSurfaceControlProperties".equals(f.getMethodName())
                                || "createSurfaceLocked".equals(f.getMethodName()))));
                if (surfaceCreation) {
                    return Hooker.PASS;
                }
            } else {
                for (StackTraceElement frame : new Throwable().getStackTrace()) {
                    String name = frame.getMethodName();
                    if ("setInitialSurfaceControlProperties".equals(name)
                            || "createSurfaceLocked".equals(name)) {
                        try {
                            if (Class.forName(frame.getClassName(), false, systemCl)
                                    .getClassLoader() == systemCl) {
                                return Hooker.PASS;
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
            return Boolean.FALSE;
        });
    }

    private static Hooker.Callback falseResult() {
        return (thiz, args) -> Boolean.FALSE;
    }

    private static Hooker.Callback trueResult() {
        return (thiz, args) -> Boolean.TRUE;
    }

    private static Hooker.Callback nullResult() {
        return (thiz, args) -> Hooker.RETURN_NULL;
    }

    private static void deopt(Hooker hk, ClassLoader cl, String className, String method) {
        try {
            deoptAll(hk, cl.loadClass(className), method);
        } catch (Throwable ignored) {
        }
    }

    private static void deoptAll(Hooker hk, Class<?> c, String method) {
        for (Method m : DexLocator.methods(c, method)) {
            hk.deoptimize(m);
        }
    }
}
