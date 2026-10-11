package com.xiaokan.colorbox;

import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Intent;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.util.Pair;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 多功能组：桌面 / 核心破解 / 系统 / 系统服务 / 分屏与小窗 /
 * 智慧侧边栏 / 安全中心 / 小布扫一扫 / 主题商店。
 *
 * 功能代码只针对 Hooker 编写一份，由入口装配；
 * 未开启的开关在回调首行直接 PASS，开销为零。
 */
final class MultiFeatures {

    private static final Set<String> EYE_FEATURES = new HashSet<>(Arrays.asList(
            "oplus.software.display.smart_color_temperature_rhythm_health_support",
            "oplus.software.display.eyeprotect_paper_texture_support"));

    private static final java.util.concurrent.atomic.AtomicReference<Object> PMS_INSTANCE =
            new java.util.concurrent.atomic.AtomicReference<>();
    private static final ThreadLocal<Boolean> KS_BYPASS_KEYSET = new ThreadLocal<>();

    private MultiFeatures() {
    }

    // ─────────────────────────── system_server ───────────────────────────

    static void installSystemServer(Hooker hk, ClassLoader cl) {
        replaceIn(hk, cl, "com.android.server.pm.OplusPackageManagerHelper",
                FeatureKeys.ENABLE_32BIT, Boolean.TRUE, "allowInstall32BitApp");
        replaceIn(hk, cl, "com.android.server.pm.OplusPackageInstallInterceptManager",
                FeatureKeys.REMOVE_ADB_CONFIRM, Boolean.FALSE,
                "allowInterceptAdbInstallInInstallStage");
        replaceIn(hk, cl, "com.android.server.pm.ColorPackageInstallInterceptManager",
                FeatureKeys.REMOVE_ADB_CONFIRM, Boolean.FALSE,
                "allowInterceptAdbInstallInInstallStage");
        replaceIn(hk, cl, "com.android.server.wm.OplusLongshotMainWindow",
                FeatureKeys.CAPTURE_PRIVACY, Boolean.FALSE, "hasSecure");
        replaceIn(hk, cl, "com.android.server.policy.PhoneWindowManager",
                FeatureKeys.CAPTURE_NO_DELAY, 0L, "getScreenshotChordLongPressDelay");
        try {
            hookDowngrade(hk, cl);
        } catch (Throwable t) {
            hk.log("hook downgrade failed", t);
        }
        try {
            hookVerify(hk, cl);
        } catch (Throwable t) {
            hk.log("hook verify failed", t);
        }
        hookFeatureConfig(hk);
        try {
            Class<?> lsa = DexLocator.cls(cl,
                    "com.android.server.locksettings.LockSettingsStrongAuth");
            replace(hk, lsa, FeatureKeys.DISABLE_PIN_72H, Hooker.RETURN_NULL,
                    "rescheduleStrongAuthTimeoutAlarm");
        } catch (Throwable ignored) {
        }
        try {
            Class<?> ws = DexLocator.cls(cl, "com.android.server.wm.WindowState");
            for (Method m : DexLocator.methods(ws, "getTouchOcclusionMode")) {
                if (m.getParameterCount() != 0 || m.getReturnType() != int.class) {
                    continue;
                }
                hk.hook(m, (thiz, args) ->
                        Cfg.on(FeatureKeys.ALLOW_UNTRUSTED_TOUCH) ? 2 : Hooker.PASS);
            }
        } catch (Throwable ignored) {
        }
        try {
            hookAudioFocus(hk, cl);
        } catch (Throwable ignored) {
        }
        replaceIn(hk, cl, "com.android.server.oplus.heimdall.HeimdallService",
                FeatureKeys.DISABLE_ROOT_CHECK, Boolean.FALSE, "isRootEnable");
        try {
            hookSplitScreen(hk, cl);
        } catch (Throwable t) {
            hk.log("hook small-window failed", t);
        }
        try {
            hookPmsChecks(hk, cl);
        } catch (Throwable t) {
            hk.log("hook pms-checks failed", t);
        }
        try {
            hookDefaultAppPolicy(hk, cl);
        } catch (Throwable t) {
            hk.log("hook default-app-policy failed", t);
        }
        try {
            hookUninstallBlacklist(hk, cl);
        } catch (Throwable ignored) {
        }
        try {
            hookInstallerRedirect(hk, cl);
        } catch (Throwable t) {
            hk.log("hook installer-redirect failed", t);
        }
    }

    /** 应用内分发：与 OShin 全进程加载一致，缺类静默跳过 */
    static void onPackage(Hooker hk, String pkg, ClassLoader cl) {
        try {
            hookUninstallBlacklist(hk, cl);
            if (pkg.startsWith("com.android.launcher")) {
                hookFolderBg(hk, cl);
                hookLauncherLayout(hk, cl);
                hookBadges(hk, cl);
            } else if (pkg.startsWith("com.android.packageinstaller")) {
                hookInstaller(hk, cl);
            } else if (pkg.equals("com.android.settings")) {
                hookSettingsUninstall(hk, cl);
            } else if (pkg.equals("com.oplus.securitypermission")) {
                hookPermissionUnlock(hk, cl);
            } else if (pkg.equals("com.oplus.eyeprotect")) {
                hookFeatureConfig(hk);
            } else if (pkg.equals("com.coloros.ocrscanner")) {
                hookFullScreenTranslation(hk, cl);
            } else if (pkg.equals("com.heytap.themestore")) {
                hookThemeStore(hk, cl);
            } else if (pkg.equals("com.coloros.smartsidebar")) {
                hookSmartSidebar(hk, cl);
            } else if (pkg.equals("com.oplus.safecenter")) {
                hookSafeProvider(hk, cl);
            }
        } catch (Throwable t) {
            hk.log("multi-features failed in " + pkg, t);
        }
    }

    // ─────────────────────────── 降级与验证 ───────────────────────────

    private static void hookDowngrade(Hooker hk, ClassLoader cl) {
        Class<?> pms = DexLocator.cls(cl, "com.android.server.pm.PackageManagerService");
        for (Method m : DexLocator.methods(pms, "checkDowngrade")) {
            final boolean boolRet = m.getReturnType() == boolean.class;
            hk.hook(m, (thiz, args) -> {
                if (!Cfg.on(FeatureKeys.ALLOW_DOWNGRADE)) {
                    return Hooker.PASS;
                }
                return boolRet ? Boolean.FALSE : Hooker.RETURN_NULL;
            });
        }
    }

    private static void hookVerify(Hooker hk, ClassLoader cl) {
        Class<?> vs = DexLocator.cls(cl, "com.android.server.pm.VerifyingSession");
        final Field flags = DexLocator.field(vs, "mInstallFlags");
        if (flags != null) {
            for (Method m : DexLocator.methods(vs, "handleStartVerify")) {
                hk.hook(m, (thiz, args) -> {
                    if (Cfg.on(FeatureKeys.DISABLE_VERIFY) && thiz != null) {
                        flags.setInt(thiz, flags.getInt(thiz) | 0x00080000);
                    }
                    return Hooker.PASS;
                });
            }
        }
        replace(hk, vs, FeatureKeys.DISABLE_VERIFY, Boolean.FALSE, "isAdbVerificationEnabled");
        replace(hk, DexLocator.cls(cl, "com.android.server.pm.PackageManagerService"),
                FeatureKeys.DISABLE_VERIFY, Boolean.FALSE, "isVerificationEnabled");
        try {
            Class<?> iph = DexLocator.cls(cl, "com.android.server.pm.InstallPackageHelper");
            for (Method m : DexLocator.methods(iph, "doesSignatureMatchForPermissions")) {
                if (m.getReturnType() != boolean.class) {
                    continue;
                }
                hk.hook(m, new Hooker.Callback() {
                    @Override
                    public Object before(Object thiz, Object[] args) {
                        return Hooker.PASS;
                    }

                    @Override
                    public Object after(Object thiz, Object[] args, Object result) {
                        if (!Boolean.FALSE.equals(result)) {
                            return null;
                        }
                        if (Cfg.on(FeatureKeys.DISABLE_VERIFY)) {
                            return Boolean.TRUE;
                        }
                        if (Cfg.on(FeatureKeys.ALLOW_SIG_MISMATCH_UPDATE)
                                && args.length > 1 && args[0] != null && args[1] != null) {
                            try {
                                Object parsed = args[1];
                                Object name = parsed.getClass().getMethod("getPackageName")
                                        .invoke(parsed);
                                if (args[0].equals(name)) {
                                    return Boolean.TRUE;
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                        return null;
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    /** OplusFeatureConfigManager.hasFeature：护眼特性强制开启 */
    private static void hookFeatureConfig(Hooker hk) {
        try {
            Class<?> fc = Class.forName("com.oplus.content.OplusFeatureConfigManager");
            for (Method m : DexLocator.methods(fc, "hasFeature")) {
                hk.hook(m, (thiz, args) -> {
                    if (Cfg.on(FeatureKeys.EYE_TEXTURE) && args.length > 0
                            && EYE_FEATURES.contains(String.valueOf(args[0]))) {
                        return Boolean.TRUE;
                    }
                    return Hooker.PASS;
                });
            }
        } catch (Throwable ignored) {
        }
    }

    // ─────────────────────────── 系统服务 ───────────────────────────

    private static void hookAudioFocus(Hooker hk, ClassLoader cl) {
        Class<?> c = DexLocator.cls(cl, "com.android.server.audio.MediaFocusControl");
        for (Method m : c.getDeclaredMethods()) {
            if (!"requestAudioFocus".equals(m.getName())) {
                continue;
            }
            hk.hook(m, (thiz, args) -> {
                if (!Cfg.on(FeatureKeys.IGNORE_AUDIO_FOCUS)) {
                    return Hooker.PASS;
                }
                if (args.length > 0 && args[0] instanceof android.media.AudioAttributes) {
                    int usage = ((android.media.AudioAttributes) args[0]).getUsage();
                    boolean allowed =
                            usage == android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION
                                    || usage == android.media.AudioAttributes
                                    .USAGE_ASSISTANCE_ACCESSIBILITY
                                    || usage == android.media.AudioAttributes
                                    .USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
                    if (!allowed) {
                        return android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
                    }
                }
                return Hooker.PASS;
            });
        }
    }

    /** 分屏与小窗：黑白名单与参数覆写 */
    private static void hookSplitScreen(Hooker hk, ClassLoader cl) {
        Class<?> fwu = DexLocator.cls(cl, "com.android.server.wm.FlexibleWindowUtils");
        final String rk = FeatureKeys.SMALL_WIN_UNRESTRICT;
        replace(hk, fwu, rk, Boolean.TRUE, "isUnSupportCallerFlexibleWindow",
                "isSupportFlexibleWindow");
        replace(hk, fwu, rk, Boolean.FALSE, "isInFlexibleWindowBlackList",
                "isInMultiWindowFlexibleBlackList", "isFlexibleTaskInPSBlackList");
        for (Method m : DexLocator.methods(fwu, "getUnSupportRatiosInFlexibleTask")) {
            hk.hook(m, (thiz, args) -> Cfg.on(rk) ? "" : Hooker.PASS);
        }
        Class<?> fwm = DexLocator.cls(cl, "com.android.server.wm.FlexibleWindowManagerService");
        for (Method m : fwm.getDeclaredMethods()) {
            if (m.getParameterCount() != 1 || m.getReturnType() != int.class) {
                continue;
            }
            String name = m.getName();
            String key;
            if ("getMaxWinNum".equals(name)) {
                key = FeatureKeys.P_MAX_SMALL_WINDOWS;
            } else if ("getCornerRadius".equals(name)) {
                key = FeatureKeys.P_WIN_CORNER;
            } else if ("getShadowRadiusFocused".equals(name)) {
                key = FeatureKeys.P_WIN_FOCUS_SHADOW;
            } else if ("getShadowRadiusUnfocused".equals(name)) {
                key = FeatureKeys.P_WIN_UNFOCUS_SHADOW;
            } else {
                continue;
            }
            final String k = key;
            hk.hook(m, (thiz, args) -> {
                int v = Cfg.num(k);
                return v != -1 ? v : Hooker.PASS;
            });
        }
        replace(hk, fwm, FeatureKeys.FORCE_MULTI_WINDOW, Boolean.TRUE, "isSupportMultiMode");
    }

    // ─────────────────────────── 核心破解（PMS 检查） ───────────────────────────

    private static void hookPmsChecks(Hooker hk, ClassLoader cl) {
        replace(hk, DexLocator.cls(cl, "com.android.server.pm.PackageManagerServiceUtils"),
                FeatureKeys.ALLOW_DOWNGRADE, Hooker.RETURN_NULL, "checkDowngrade");
        hookJarVerifier(hk, cl);
        try {
            Class<?> am = Class.forName("android.content.res.AssetManager");
            replace(hk, am, FeatureKeys.BYPASS_ARSC_CHECK, Boolean.FALSE,
                    "containsAllocatedTable");
        } catch (Throwable ignored) {
        }
        hookMinSigVersion(hk, cl);
        hookSigMismatchUpdate(hk, cl);
        replace(hk, DexLocator.cls(cl, "android.content.pm.SigningDetails"),
                FeatureKeys.ALLOW_SPLIT_SIG_MISMATCH, Boolean.TRUE, "signaturesMatchExactly");
        hookHiddenApi(hk);
        hookSharedUid(hk, cl);
        hookInstallVerification(hk, cl);
        hookV1SigErrors(hk, cl);
        hookPmsCommand(hk, cl);
    }

    /** StrictJarVerifier 校验置真 + 签名方案回滚保护关闭 + 摘要比对恒等 */
    private static void hookJarVerifier(Hooker hk, ClassLoader cl) {
        try {
            Class<?> sjv = DexLocator.cls(cl, "android.util.jar.StrictJarVerifier");
            final String k = FeatureKeys.DISABLE_JAR_VERIFIER;
            for (Method m : DexLocator.methods(sjv, "verifyMessageDigest", "verify")) {
                if (m.getReturnType() != boolean.class) {
                    continue;
                }
                hk.hook(m, (thiz, args) -> Cfg.on(k) ? Boolean.TRUE : Hooker.PASS);
            }
            final Field roll = DexLocator.field(sjv,
                    "signatureSchemeRollbackProtectionsEnforced");
            for (Constructor<?> ctor : sjv.getDeclaredConstructors()) {
                hk.hook(ctor, new Hooker.Callback() {
                    @Override
                    public Object before(Object thiz, Object[] args) {
                        return Hooker.PASS;
                    }

                    @Override
                    public Object after(Object thiz, Object[] args, Object result) {
                        if (Cfg.on(k) && thiz != null && roll != null) {
                            try {
                                roll.setBoolean(thiz, false);
                            } catch (Throwable ignored) {
                            }
                        }
                        return null;
                    }
                });
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> md = Class.forName("java.security.MessageDigest");
            replace(hk, md, FeatureKeys.DISABLE_MESSAGE_DIGEST, Boolean.TRUE, "isEqual");
        } catch (Throwable ignored) {
        }
    }

    /** 绕过最低签名方案版本：阈值归零 + 断言异常吞掉 */
    private static void hookMinSigVersion(Hooker hk, ClassLoader cl) {
        try {
            Class<?> asv = DexLocator.cls(cl, "android.util.apk.ApkSignatureVerifier");
            for (Method m : DexLocator.methods(asv,
                    "getMinimumSignatureSchemeVersionForTargetSdk")) {
                if (m.getParameterCount() != 1 || m.getReturnType() != int.class) {
                    continue;
                }
                hk.hook(m, (thiz, args) ->
                        Cfg.on(FeatureKeys.BYPASS_MIN_SIG_VERSION) ? 0 : Hooker.PASS);
            }
            Class<?> spu = DexLocator.cls(cl, "com.android.server.pm.ScanPackageUtils");
            for (Method m : DexLocator.methods(spu, "assertMinSignatureSchemeIsValid")) {
                hk.hook(m, new Hooker.Callback() {
                    @Override
                    public Object before(Object thiz, Object[] args) {
                        return Hooker.PASS;
                    }

                    @Override
                    public Object onError(Throwable error) {
                        return Cfg.on(FeatureKeys.BYPASS_MIN_SIG_VERSION)
                                ? Hooker.RETURN_NULL : Hooker.PASS;
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    /** 覆盖安装签名不一致：checkCapability 放行 + KeySet 检查绕过 */
    private static void hookSigMismatchUpdate(Hooker hk, ClassLoader cl) {
        try {
            Class<?> sd = DexLocator.cls(cl, "android.content.pm.SigningDetails");
            for (Method m : DexLocator.methods(sd, "checkCapability")) {
                if (m.getParameterCount() != 2) {
                    continue;
                }
                hk.hook(m, (thiz, args) -> {
                    if (!Cfg.on(FeatureKeys.ALLOW_SIG_MISMATCH_UPDATE)) {
                        return Hooker.PASS;
                    }
                    if (args.length >= 2 && args[1] instanceof Integer) {
                        int cap = (Integer) args[1];
                        if (cap != 4 && cap != 16) {
                            return Boolean.TRUE;
                        }
                    }
                    return Hooker.PASS;
                });
            }
            Class<?> ksm = DexLocator.cls(cl, "com.android.server.pm.KeySetManagerService");
            for (Method m : DexLocator.methods(ksm, "shouldCheckUpgradeKeySetLocked")) {
                hk.hook(m, new Hooker.Callback() {
                    @Override
                    public Object before(Object thiz, Object[] args) {
                        return Hooker.PASS;
                    }

                    @Override
                    public Object after(Object thiz, Object[] args, Object result) {
                        if (!Cfg.on(FeatureKeys.ALLOW_SIG_MISMATCH_UPDATE)) {
                            return null;
                        }
                        boolean fromPrepare = false;
                        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                            if (e.getMethodName().startsWith("preparePackage")) {
                                fromPrepare = true;
                                break;
                            }
                        }
                        KS_BYPASS_KEYSET.set(fromPrepare);
                        return fromPrepare ? Boolean.TRUE : null;
                    }
                });
            }
            for (Method m : DexLocator.methods(ksm, "checkUpgradeKeySetLocked")) {
                hk.hook(m, new Hooker.Callback() {
                    @Override
                    public Object before(Object thiz, Object[] args) {
                        return Hooker.PASS;
                    }

                    @Override
                    public Object after(Object thiz, Object[] args, Object result) {
                        return Cfg.on(FeatureKeys.ALLOW_SIG_MISMATCH_UPDATE)
                                && Boolean.TRUE.equals(KS_BYPASS_KEYSET.get())
                                ? Boolean.TRUE : null;
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    /** 系统应用隐藏 API 白名单 */
    private static void hookHiddenApi(Hooker hk) {
        try {
            Class<?> ai = android.content.pm.ApplicationInfo.class;
            for (Method m : DexLocator.methods(ai, "isPackageWhitelistedForHiddenApis")) {
                if (m.getReturnType() != boolean.class) {
                    continue;
                }
                final boolean isStatic = java.lang.reflect.Modifier.isStatic(m.getModifiers());
                hk.hook(m, (thiz, args) -> {
                    if (!Cfg.on(FeatureKeys.ALLOW_HIDDEN_API)) {
                        return Hooker.PASS;
                    }
                    Object info = isStatic ? null : thiz;
                    if (info instanceof android.content.pm.ApplicationInfo) {
                        int f = ((android.content.pm.ApplicationInfo) info).flags;
                        if ((f & 1) != 0 || (f & 128) != 0) {
                            return Boolean.TRUE;
                        }
                    }
                    return Hooker.PASS;
                });
            }
        } catch (Throwable ignored) {
        }
    }

    /** 非系统预装共享 UID 放行：静态开关字段强写 */
    private static void hookSharedUid(Hooker hk, ClassLoader cl) {
        if (!Cfg.on(FeatureKeys.ALLOW_NONSYSTEM_SHARED_UID)) {
            return;
        }
        try {
            Class<?> rpu = DexLocator.cls(cl, "com.android.server.pm.ReconcilePackageUtils");
            DexLocator.setStaticInt(rpu, "ALLOW_NON_PRELOADS_SYSTEM_SHAREDUIDS", 1);
        } catch (Throwable ignored) {
        }
    }

    private static void hookInstallVerification(Hooker hk, ClassLoader cl) {
        try {
            Class<?> vs = DexLocator.cls(cl, "com.android.server.pm.VerifyingSession");
            for (Method m : DexLocator.methods(vs, "isVerificationEnabled")) {
                if (m.getReturnType() != boolean.class) {
                    continue;
                }
                hk.hook(m, (thiz, args) -> {
                    if (Cfg.on(FeatureKeys.DISABLE_INSTALL_VERIFICATION)
                            || Cfg.on(FeatureKeys.DISABLE_VERIFY)) {
                        return Boolean.FALSE;
                    }
                    return Hooker.PASS;
                });
            }
        } catch (Throwable ignored) {
        }
    }

    static final String COREPATCH_CERT =
            "308203c6308202aea003020102021426d148b7c65944abcf3a683b4c3dd3b139c4ec85"
                    + "300d06092a864886f70d01010b05003074310b3009060355040613025553311330110603"
                    + "550408130a43616c69666f726e6961311630140603550407130d4d6f756e7461696e2056"
                    + "69657731143012060355040a130b476f6f676c6520496e632e3110300e060355040b1307"
                    + "416e64726f69643110300e06035504031307416e64726f6964301e170d31393031303231"
                    + "38353233385a170d3439303130323138353233385a3074310b3009060355040613025553"
                    + "311330110603550408130a43616c69666f726e6961311630140603550407130d4d6f756e"
                    + "7461696e205669657731143012060355040a130b476f6f676c6520496e632e3110300e06"
                    + "0355040b1307416e64726f69643110300e06035504031307416e64726f69643082012230"
                    + "0d06092a864886f70d01010105000382010f003082010a028201010087fcde48d9beaeba"
                    + "37b733a397ae586fb42b6c3f4ce758dc3ef1327754a049b58f738664ece587994f1c6362"
                    + "f98c9be5fe82c72177260c390781f74a10a8a6f05a6b5ca0c7c5826e15526d8d7f0e74f2"
                    + "170064896b0cf32634a388e1a975ed6bab10744d9b371cba85069834bf098f1de0205cde"
                    + "e8e715759d302a64d248067a15b9beea11b61305e367ac71b1a898bf2eec7342109c9c58"
                    + "13a579d8a1b3e6a3fe290ea82e27fdba748a663f73cca5807cff1e4ad6f3ccca7c029459"
                    + "26a47279d1159599d4ecf01c9d0b62e385c6320a7a1e4ddc9833f237e814b34024b9ad10"
                    + "8a5b00786ea15593a50ca7987cbbdc203c096eed5ff4bf8a63d27d33ecc9639902030100"
                    + "01a350304e300c0603551d13040530030101ff301d0603551d0e04160414a361efb00203"
                    + "4d596c3a60ad7b0332012a16aee3301f0603551d23041830168014a361efb002034d596c"
                    + "3a60ad7b0332012a16aee3300d06092a864886f70d01010b0500038201010022ccb684a7"
                    + "a8706f3ee7c81d6750fd662bf39f84805862040b625ddf378eeefae5a4f1f283deea61a3"
                    + "c7f8e7963fd745415153a531912b82b596e7409287ba26fb80cedba18f22ae3d987466e1"
                    + "fdd88e440402b2ea2819db5392cadee501350e81b8791675ea1a2ed7ef7696dff273f13f"
                    + "b742bb9625fa12ce9c2cb0b7b3d94b21792f1252b1d9e4f7012cb341b62ff556e6864b40"
                    + "927e942065d8f0f51273fcda979b8832dd5562c79acf719de6be5aee2a85f89265b071bf"
                    + "38339e2d31041bc501d5e0c034ab1cd9c64353b10ee70b49274093d13f733eb9d3543140"
                    + "814c72f8e003f301c7a00b1872cc008ad55e26df2e8f07441002c4bcb7dc746745f0db";

    /** V1 签名错误兜底：error=-103 时替换为 CorePatch 证书的 SigningDetails */
    private static void hookV1SigErrors(Hooker hk, ClassLoader cl) {
        try {
            Class<?> asv = DexLocator.cls(cl, "android.util.apk.ApkSignatureVerifier");
            Class<?> ppe = DexLocator.cls(cl,
                    "android.content.pm.PackageParser$PackageParserException");
            final Field errField = DexLocator.field(ppe, "error");
            for (Method m : DexLocator.methods(asv, "verifyV1Signature")) {
                hk.hook(m, new Hooker.Callback() {
                    @Override
                    public Object before(Object thiz, Object[] args) {
                        return Hooker.PASS;
                    }

                    @Override
                    public Object onError(Throwable error) {
                        if (!Cfg.on(FeatureKeys.BYPASS_V1_SIG_ERRORS)
                                || !isV1Error(error, ppe, errField)) {
                            return Hooker.PASS;
                        }
                        Object sd = newSigningDetailsV1(cl);
                        return sd != null ? sd : Hooker.PASS;
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean isV1Error(Throwable t, Class<?> ppe, Field errField) {
        if (ppe == null || errField == null) {
            return false;
        }
        Throwable cur = t;
        for (int i = 0; i < 3 && cur != null; i++) {
            if (cur.getClass() == ppe) {
                try {
                    if (errField.getInt(cur) == -103) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    private static Object newSigningDetailsV1(ClassLoader cl) {
        try {
            Class<?> sigC = DexLocator.cls(cl, "android.content.pm.Signature");
            Object sig = sigC.getConstructor(String.class).newInstance(COREPATCH_CERT);
            Object sigArr = java.lang.reflect.Array.newInstance(sigC, 1);
            java.lang.reflect.Array.set(sigArr, 0, sig);
            Class<?> sd = DexLocator.cls(cl, "android.content.pm.SigningDetails");
            for (Constructor<?> c : sd.getDeclaredConstructors()) {
                Class<?>[] ps = c.getParameterTypes();
                if (ps.length == 2 && ps[0].isArray() && ps[1] == int.class) {
                    c.setAccessible(true);
                    return c.newInstance(sigArr, 1);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** adb shell pm pms 调试命令：转储包/共享用户签名信息 */
    private static void hookPmsCommand(Hooker hk, ClassLoader cl) {
        if (!Cfg.on(FeatureKeys.PMS_COMMAND)) {
            return;
        }
        try {
            Class<?> pms = DexLocator.cls(cl, "com.android.server.pm.PackageManagerService");
            for (Constructor<?> ctor : pms.getDeclaredConstructors()) {
                hk.hook(ctor, new Hooker.Callback() {
                    @Override
                    public Object before(Object thiz, Object[] args) {
                        return Hooker.PASS;
                    }

                    @Override
                    public Object after(Object thiz, Object[] args, Object result) {
                        PMS_INSTANCE.set(thiz);
                        return null;
                    }
                });
            }
            Class<?> pmsc = DexLocator.cls(cl,
                    "com.android.server.pm.PackageManagerShellCommand");
            for (Method m : DexLocator.methods(pmsc, "onCommand")) {
                if (m.getParameterCount() != 1) {
                    continue;
                }
                hk.hook(m, (thiz, args) -> {
                    Object cmd = args.length > 0 ? args[0] : null;
                    if (!"pms".equals(cmd)) {
                        return Hooker.PASS;
                    }
                    Object localPms = PMS_INSTANCE.get();
                    if (localPms == null) {
                        return 0;
                    }
                    try {
                        Object pw = thiz.getClass().getMethod("getOutPrintWriter")
                                .invoke(thiz);
                        String type = (String) thiz.getClass()
                                .getMethod("getNextArgRequired").invoke(thiz);
                        Field sf = DexLocator.field(localPms.getClass(), "mSettings");
                        Object settings = sf == null ? null : sf.get(localPms);
                        if (settings == null) {
                            print(pw, "Error: Could not get mSettings from PMS.");
                            return 0;
                        }
                        if ("p".equals(type) || "package".equals(type)) {
                            String name = (String) thiz.getClass()
                                    .getMethod("getNextArgRequired").invoke(thiz);
                            Object ps = null;
                            for (Method gm : settings.getClass().getDeclaredMethods()) {
                                if ("getPackageLPr".equals(gm.getName())
                                        && gm.getParameterCount() == 1
                                        && gm.getParameterTypes()[0] == String.class) {
                                    gm.setAccessible(true);
                                    ps = gm.invoke(settings, name);
                                    break;
                                }
                            }
                            if (ps != null) {
                                dumpSignatures(ps, pw);
                            } else {
                                print(pw, "no package " + name + " found");
                            }
                        } else if ("su".equals(type) || "shareduser".equals(type)) {
                            String name = (String) thiz.getClass()
                                    .getMethod("getNextArgRequired").invoke(thiz);
                            Field suf = DexLocator.field(settings.getClass(), "mSharedUsers");
                            Object su = suf == null ? null : suf.get(settings);
                            Object target = su instanceof java.util.Map
                                    ? ((java.util.Map<?, ?>) su).get(name) : null;
                            if (target != null) {
                                dumpSignatures(target, pw);
                            } else {
                                print(pw, "no shared user " + name + " found");
                            }
                        } else {
                            print(pw, "usage: <p|package|su|shareduser> <name>");
                        }
                    } catch (Throwable t) {
                        hk.log("pms command failed", t);
                    }
                    return 0;
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static void print(Object pw, String s) throws Exception {
        pw.getClass().getMethod("println", String.class).invoke(pw, s);
    }

    private static void dumpSignatures(Object setting, Object pw) throws Exception {
        Object sigsObj = DexLocator.field(setting.getClass(), "signatures").get(setting);
        Object details = sigsObj;
        if (details != null) {
            Field sdf = DexLocator.field(details.getClass(), "mSigningDetails");
            if (sdf != null) {
                details = sdf.get(details);
            }
        }
        print(pw, "signing for " + setting);
        if (details == null) {
            return;
        }
        Object[] sigs = null;
        try {
            sigs = (Object[]) details.getClass().getMethod("getSignatures").invoke(details);
        } catch (Throwable ignored) {
        }
        if (sigs == null) {
            print(pw, "Could not get signatures.");
            return;
        }
        for (int i = 0; i < sigs.length; i++) {
            String hex = null;
            try {
                hex = (String) sigs[i].getClass().getMethod("toCharsString").invoke(sigs[i]);
            } catch (Throwable ignored) {
            }
            print(pw, (i + 1) + ": " + hex);
        }
    }

    // ─────────────────────────── 默认应用策略 / 卸载黑名单 / 重定向 ───────────────────────────

    /** 文件选择器接管 / 禁止接管（OplusDefaultAppPolicyManager） */
    private static void hookDefaultAppPolicy(Hooker hk, ClassLoader cl) {
        if (!Cfg.on(FeatureKeys.CUSTOM_FILE_MANAGER)
                && !Cfg.on(FeatureKeys.FORBID_PICKER)) {
            return;
        }
        Class<?> c = DexLocator.cls(cl,
                "com.android.server.pm.OplusDefaultAppPolicyManager");
        for (Method m : DexLocator.methods(c, "interceptPickerIntent")) {
            if (m.getParameterCount() != 2
                    || m.getParameterTypes()[0] != Intent.class
                    || m.getParameterTypes()[1] != List.class) {
                continue;
            }
            hk.hook(m, (thiz, args) -> {
                if (Cfg.on(FeatureKeys.FORBID_PICKER)) {
                    return Hooker.RETURN_NULL;
                }
                if (!Cfg.on(FeatureKeys.CUSTOM_FILE_MANAGER)) {
                    return Hooker.PASS;
                }
                if (args.length < 2 || !(args[1] instanceof List)) {
                    return Hooker.PASS;
                }
                Set<String> allowed = allowedPickerPackages();
                Object match = null;
                for (Object ri : (List<?>) args[1]) {
                    if (allowed.contains(pickerPackageName(ri))) {
                        match = ri;
                    }
                }
                return match != null ? match : Hooker.PASS;
            });
        }
    }

    private static Set<String> allowedPickerPackages() {
        Set<String> out = new HashSet<>();
        for (String s : Cfg.text(FeatureKeys.P_FILE_MANAGER_PKG).split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static String pickerPackageName(Object ri) {
        try {
            Object ai = ri.getClass().getField("activityInfo").get(ri);
            return (String) ai.getClass().getField("packageName").get(ai);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 清空卸载图标隐藏名单（多 ColorOS 进程均有此类，缺类静默跳过） */
    private static void hookUninstallBlacklist(Hooker hk, ClassLoader cl) {
        Class<?> c = DexLocator.cls(cl,
                "com.android.server.pm.OplusUninstallableConfigManager");
        if (c == null) {
            return;
        }
        for (Method m : DexLocator.methods(c, "loadUninstallableConfig")) {
            hk.hook(m, new Hooker.Callback() {
                @Override
                public Object before(Object thiz, Object[] args) {
                    return Hooker.PASS;
                }

                @Override
                public Object after(Object thiz, Object[] args, Object result) {
                    if (Cfg.on(FeatureKeys.UNINSTALL_BLACKLIST) && thiz != null) {
                        try {
                            clearUninstallSets(thiz);
                        } catch (Throwable t) {
                            hk.log("uninstall-blacklist clear failed", t);
                        }
                    }
                    return null;
                }
            });
        }
    }

    private static void clearUninstallSets(Object self) throws Exception {
        Class<?> cls = self.getClass();
        for (String fn : new String[]{"mHideUninstallIcon", "mHideUninstallIconSoft"}) {
            Field f = DexLocator.field(cls, fn);
            if (f == null) {
                continue;
            }
            Object holder = f.get(self);
            if (holder == null) {
                continue;
            }
            Field lf = DexLocator.field(holder.getClass(), "mList");
            if (lf == null) {
                continue;
            }
            Object list = lf.get(holder);
            if (list instanceof Set) {
                ((Set<?>) list).clear();
            }
        }
    }

    /** 安装器/卸载器重定向：ActivityStarter.execute 前改写 Intent（原地生效） */
    private static void hookInstallerRedirect(Hooker hk, ClassLoader cl) {
        if (!Cfg.on(FeatureKeys.INSTALLER_REDIRECT)
                && !Cfg.on(FeatureKeys.UNINSTALLER_REDIRECT)) {
            return;
        }
        Class<?> c = DexLocator.cls(cl, "com.android.server.wm.ActivityStarter");
        Class<?> reqCls = null;
        for (Class<?> ic : c.getDeclaredClasses()) {
            if (ic.getSimpleName().equals("Request")) {
                reqCls = ic;
                break;
            }
        }
        final Field intentF = DexLocator.field(reqCls, "intent");
        final Class<?> reqType = reqCls;
        for (Method m : DexLocator.methods(c, "execute")) {
            hk.hook(m, (thiz, args) -> {
                Object request = null;
                if (args.length > 0 && args[0] != null && reqType != null
                        && reqType.isInstance(args[0])) {
                    request = args[0];
                }
                if (request == null && thiz != null) {
                    Field f = DexLocator.field(thiz.getClass(), "mRequest");
                    if (f != null) {
                        request = f.get(thiz);
                    }
                }
                if (request == null) {
                    return Hooker.PASS;
                }
                Object iv = intentF != null ? intentF.get(request) : null;
                if (!(iv instanceof Intent)) {
                    return Hooker.PASS;
                }
                Intent intent = (Intent) iv;
                String action = intent.getAction();
                if (Intent.ACTION_DELETE.equals(action)
                        || "android.intent.action.UNINSTALL_PACKAGE".equals(action)) {
                    if (intent.getData() != null
                            && Cfg.on(FeatureKeys.UNINSTALLER_REDIRECT)) {
                        String target = Cfg.text(FeatureKeys.P_UNINSTALLER_TARGETS).trim();
                        if (!target.isEmpty()) {
                            intent.setPackage(target);
                            intent.setComponent(null);
                        }
                    }
                    return Hooker.PASS;
                }
                boolean redirect;
                if (Intent.ACTION_GET_CONTENT.equals(action)
                        || Intent.ACTION_CHOOSER.equals(action)) {
                    redirect = false;
                } else if ("android.intent.action.INSTALL_PACKAGE".equals(action)) {
                    redirect = true;
                } else {
                    ComponentName cn = intent.getComponent();
                    if (cn != null) {
                        redirect = cn.getClassName().contains("InstallStart")
                                && "application/vnd.android.package-archive"
                                .equals(intent.getType());
                    } else {
                        redirect = "application/vnd.android.package-archive"
                                .equals(intent.getType());
                    }
                }
                if (!redirect || !Cfg.on(FeatureKeys.INSTALLER_REDIRECT)) {
                    return Hooker.PASS;
                }
                String target = Cfg.text(FeatureKeys.P_INSTALLER_TARGETS).trim();
                if (target.isEmpty()) {
                    return Hooker.PASS;
                }
                intent.setPackage(target);
                intent.setComponent(null);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                return Hooker.PASS;
            });
        }
    }

    /** packageinstaller 进程：安装入口页跳转自定义安装器 */
    private static void hookInstallerActivity(Hooker hk) {
        if (!Cfg.on(FeatureKeys.INSTALLER_REDIRECT)) {
            return;
        }
        for (Method m : DexLocator.methods(Activity.class, "onCreate")) {
            if (m.getParameterCount() != 1
                    || m.getParameterTypes()[0] != Bundle.class) {
                continue;
            }
            hk.hook(m, new Hooker.Callback() {
                @Override
                public Object before(Object thiz, Object[] args) {
                    return Hooker.PASS;
                }

                @Override
                public Object after(Object thiz, Object[] args, Object result) {
                    try {
                        redirectInstallerActivity(thiz);
                    } catch (Throwable ignored) {
                    }
                    return null;
                }
            });
        }
    }

    private static void redirectInstallerActivity(Object self) {
        if (!Cfg.on(FeatureKeys.INSTALLER_REDIRECT) || !(self instanceof Activity)) {
            return;
        }
        Activity act = (Activity) self;
        Intent intent = act.getIntent();
        if (intent == null || intent.getData() == null) {
            return;
        }
        String type = intent.getType();
        String lower = act.getClass().getName().toLowerCase();
        boolean installerLike = lower.contains("packageinstalleractivity")
                || lower.contains("installstart")
                || (type != null && type.contains("application/vnd.android.package-archive"));
        if (!installerLike || lower.contains("installstart")) {
            return;
        }
        String target = Cfg.text(FeatureKeys.P_INSTALLER_TARGETS).trim();
        if (target.isEmpty() || act.getPackageName().equals(target)) {
            return;
        }
        Intent redirect = new Intent(Intent.ACTION_VIEW);
        redirect.setPackage(target);
        redirect.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        redirect.setDataAndType(intent.getData(), type);
        act.startActivity(redirect);
        act.finish();
    }

    // ─────────────────────────── 安装器 ───────────────────────────

    private static void hookInstaller(Hooker hk, ClassLoader cl) {
        hookInstallButtonFix(hk, cl);
        hookSkipScan(hk, cl);
        hookAllowReplaceInstall(hk, cl);
        hookAutoClick(hk, cl);
        hookDisableAppDetail(hk, cl);
        hookInstallProgress(hk, cl);
        try {
            hookInstallerActivity(hk);
        } catch (Throwable ignored) {
        }
    }

    /** 修复安装按钮显示异常：Cts 状态复位 */
    private static void hookInstallButtonFix(Hooker hk, ClassLoader cl) {
        int n = 0;
        for (String cn : new String[]{
                "com.android.packageinstaller.oplus.view.ConfusedButton",
                "com.android.packageinstaller.oplus.view.ConfusedTextView"}) {
            try {
                Class<?> c = DexLocator.cls(cl, cn);
                Method setCts = null;
                for (Method m : c.getDeclaredMethods()) {
                    if ("setCts".equals(m.getName())) {
                        setCts = m;
                        break;
                    }
                }
                Field rnd = null;
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType() == java.security.SecureRandom.class) {
                        rnd = f;
                        break;
                    }
                }
                final Method fix = setCts;
                final Field rndF = rnd;
                for (Method m : DexLocator.methods(c,
                        "getAccessibilityViewId", "getText")) {
                    hk.hook(m, (thiz, args) -> {
                        if (Cfg.on(FeatureKeys.FIX_INSTALL_BUTTON) && thiz != null) {
                            if (fix != null) {
                                fix.invoke(thiz, Boolean.TRUE);
                            }
                            if (rndF != null) {
                                rndF.set(thiz, new java.security.SecureRandom());
                            }
                        }
                        return Hooker.PASS;
                    });
                    n++;
                }
            } catch (Throwable ignored) {
            }
        }
        ok(hk, "install-button", n);
    }

    /** 跳过 APK 安全扫描：扫描方法改为直接进入初始化 */
    private static void hookSkipScan(Hooker hk, ClassLoader cl) {
        if (!Cfg.on(FeatureKeys.SKIP_APK_SCAN)) {
            return;
        }
        try {
            Class<?> act = DexLocator.cls(cl,
                    "com.android.packageinstaller.oplus.OPlusPackageInstallerActivity");
            Method scan = DexIndex.firstInClass(cl, act, new DexIndex.Query()
                    .params(0).voidReturn().calledBy("onClick")
                    .touchesFieldTypes("J", "Z", "Landroid/view/View;"));
            Method init = DexIndex.firstInClass(cl, act, new DexIndex.Query()
                    .params(0).voidReturn().calledBy("onClick")
                    .touchesFieldTypes("Landroid/content/pm/PackageManager;",
                            "Landroid/content/pm/PackageInfo;",
                            "Landroid/content/pm/ApplicationInfo;", "Z"));
            if (scan == null || init == null) {
                hk.log("skip-scan candidates not found");
                return;
            }
            final Method initM = init;
            hk.hook(scan, (thiz, args) -> {
                if (Cfg.on(FeatureKeys.SKIP_APK_SCAN) && thiz != null) {
                    initM.invoke(thiz);
                    return Hooker.RETURN_NULL;
                }
                return Hooker.PASS;
            });
            ok(hk, "skip-scan", 1);
        } catch (Throwable t) {
            hk.log("skip-scan failed", t);
        }
    }

    /** 允许降级/替换安装：版本比较方法改走安全前检查 */
    private static void hookAllowReplaceInstall(Hooker hk, ClassLoader cl) {
        if (!Cfg.on(FeatureKeys.ALLOW_DOWNGRADE)) {
            return;
        }
        try {
            Class<?> act = DexLocator.cls(cl,
                    "com.android.packageinstaller.oplus.OPlusPackageInstallerActivity");
            Method parse = DexIndex.firstInClass(cl, act, new DexIndex.Query()
                    .params(0).hasStrings("currentVersionCode", "apkVersioncode"));
            Method preSafe = DexIndex.firstInClass(cl, act, new DexIndex.Query()
                    .params(0).hasStrings("startAppdetail", "reason"));
            if (parse == null || preSafe == null) {
                hk.log("allow-replace candidates not found");
                return;
            }
            final Method preSafeM = preSafe;
            hk.hook(parse, (thiz, args) -> {
                if (Cfg.on(FeatureKeys.ALLOW_DOWNGRADE) && thiz != null) {
                    preSafeM.invoke(thiz);
                    return Hooker.RETURN_NULL;
                }
                return Hooker.PASS;
            });
            ok(hk, "allow-replace", 1);
        } catch (Throwable t) {
            hk.log("allow-replace failed", t);
        }
    }

    /** 自动点击安装/卸载确认按钮 */
    private static void hookAutoClick(Hooker hk, ClassLoader cl) {
        if (Cfg.on(FeatureKeys.AUTO_INSTALL)) {
            try {
                Class<?> act = DexLocator.cls(cl,
                        "com.android.packageinstaller.oplus.OPlusPackageInstallerActivity");
                Method initRef = DexIndex.firstInClass(cl, act, new DexIndex.Query()
                        .params(0).voidReturn().calledBy("onClick")
                        .touchesFieldTypes("Landroid/content/pm/PackageManager;",
                                "Landroid/content/pm/PackageInfo;",
                                "Landroid/content/pm/ApplicationInfo;", "Z"));
                Method confirm = null;
                if (initRef != null) {
                    confirm = DexIndex.firstInClass(cl, act, new DexIndex.Query()
                            .params(0).voidReturn().calledBy(initRef.getName())
                            .touchesFieldTypes("Landroid/view/View;", "Z",
                                    "Ljava/util/ArrayList;"));
                }
                if (confirm == null) {
                    confirm = DexIndex.firstInClass(cl, act, new DexIndex.Query()
                            .params(0).voidReturn()
                            .touchesFieldTypes("Landroid/view/View;", "Z",
                                    "Ljava/util/ArrayList;"));
                }
                if (confirm != null) {
                    hk.hook(confirm, autoClickCb(hk, "ok_button"));
                    ok(hk, "auto-install", 1);
                } else {
                    hk.log("auto-install not found");
                }
            } catch (Throwable t) {
                hk.log("auto-install failed", t);
            }
        }
        if (Cfg.on(FeatureKeys.AUTO_UNINSTALL)) {
            try {
                Class<?> act = DexLocator.cls(cl,
                        "com.android.packageinstaller.UninstallerActivity");
                Method m = DexIndex.firstInClass(cl, act, new DexIndex.Query()
                        .params(1).voidReturn()
                        .paramTypes("Landroid/content/Intent;")
                        .hasStrings("isUninstalledFont"));
                if (m != null) {
                    hk.hook(m, autoClickCb(hk, "ok_button"));
                    ok(hk, "auto-uninstall-confirm", 1);
                }
                Class<?> prog = DexLocator.cls(cl,
                        "com.android.packageinstaller.oplus.OPlusUninstallAppProgress");
                Method pv = DexIndex.firstInClass(cl, prog, new DexIndex.Query()
                        .params(0).voidReturn()
                        .hasStrings("source_info", "package_name", "package_size"));
                if (pv != null) {
                    hk.hook(pv, autoClickCb(hk, "complete_button"));
                    ok(hk, "auto-uninstall-done", 1);
                }
            } catch (Throwable t) {
                hk.log("auto-uninstall failed", t);
            }
        }
    }

    private static Hooker.Callback autoClickCb(Hooker hk, String idName) {
        return new Hooker.Callback() {
            @Override
            public Object before(Object thiz, Object[] args) {
                return Hooker.PASS;
            }

            @Override
            public Object after(Object thiz, Object[] args, Object result) {
                try {
                    if (thiz instanceof Activity) {
                        Activity a = (Activity) thiz;
                        int id = a.getResources()
                                .getIdentifier(idName, "id", a.getPackageName());
                        if (id != 0) {
                            View v = a.findViewById(id);
                            if (v instanceof Button) {
                                v.performClick();
                            }
                        }
                    }
                } catch (Throwable t) {
                    hk.log("auto-click failed", t);
                }
                return null;
            }
        };
    }

    /** 禁止启动 AppDetail 定制扫描（全局字符串定位） */
    private static void hookDisableAppDetail(Hooker hk, ClassLoader cl) {
        if (!Cfg.on(FeatureKeys.DISABLE_APPDETAIL)) {
            return;
        }
        try {
            List<Method> found = DexIndex.global(cl, new DexIndex.Query()
                    .hasStrings("count_canceled_by_app_detail", "com.oplus.appdetail"));
            for (Method m : found) {
                hk.hook(m, (thiz, args) ->
                        Cfg.on(FeatureKeys.DISABLE_APPDETAIL) ? 9 : Hooker.PASS);
            }
            ok(hk, "disable-appdetail", found.size());
        } catch (Throwable t) {
            hk.log("disable-appdetail failed", t);
        }
    }

    /** 安装完成页：移除推荐广告 + 自动点击完成 */
    private static void hookInstallProgress(Hooker hk, ClassLoader cl) {
        boolean ads = Cfg.on(FeatureKeys.REMOVE_INSTALL_ADS);
        boolean auto = Cfg.on(FeatureKeys.AUTO_INSTALL);
        if (!ads && !auto) {
            return;
        }
        try {
            Class<?> prog = DexLocator.cls(cl,
                    "com.android.packageinstaller.oplus.InstallAppProgress");
            if (prog == null) {
                Method iv = DexIndex.firstBySimpleName(cl, "InstallAppProgress",
                        new DexIndex.Query().params(0).voidReturn().calledBy("onCreate")
                                .hasStrings("source_info", "type_channel_title",
                                        "type_channel_tips"));
                if (iv == null) {
                    hk.log("install-progress class not found");
                    return;
                }
                prog = iv.getDeclaringClass();
            }
            Method iv = DexIndex.firstInClass(cl, prog, new DexIndex.Query()
                    .params(0).voidReturn().calledBy("onCreate")
                    .hasStrings("source_info", "type_channel_title",
                            "type_channel_tips"));
            if (ads && iv != null) {
                hk.hook(iv, new Hooker.Callback() {
                    @Override
                    public Object before(Object thiz, Object[] args) {
                        return Hooker.PASS;
                    }

                    @Override
                    public Object after(Object thiz, Object[] args, Object result) {
                        hideAds(thiz);
                        return null;
                    }
                });
                Method hm = DexIndex.firstInClass(cl, prog, new DexIndex.Query()
                        .named("handleMessage")
                        .hasStrings("oplus.intent.action.VIRUS_APK_INSTALLED",
                                "oplus.permission.OPLUS_COMPONENT_SAFE"));
                if (hm != null) {
                    hk.hook(hm, new Hooker.Callback() {
                        @Override
                        public Object before(Object thiz, Object[] args) {
                            return Hooker.PASS;
                        }

                        @Override
                        public Object after(Object thiz, Object[] args, Object result) {
                            hideAds(thiz);
                            return null;
                        }
                    });
                }
                ok(hk, "remove-ads", 1);
            }
            if (auto && iv != null) {
                Method opi = DexIndex.firstInClass(cl, prog, new DexIndex.Query()
                        .params(1).returns("I").calledBy(iv.getName()));
                if (opi != null) {
                    hk.hook(opi, new Hooker.Callback() {
                        @Override
                        public Object before(Object thiz, Object[] args) {
                            return Hooker.PASS;
                        }

                        @Override
                        public Object after(Object thiz, Object[] args, Object result) {
                            if (args.length > 0 && Integer.valueOf(0).equals(args[0])) {
                                clickSelf(thiz, "done_button");
                            }
                            return null;
                        }
                    });
                    ok(hk, "auto-done", 1);
                }
            }
        } catch (Throwable t) {
            hk.log("install-progress failed", t);
        }
    }

    private static void hideAds(Object self) {
        if (!(self instanceof Activity)) {
            return;
        }
        Activity a = (Activity) self;
        for (String id : new String[]{"suggest_A_scroll_layout", "install_done_suggest_B"}) {
            try {
                int res = a.getResources().getIdentifier(id, "id", a.getPackageName());
                if (res != 0) {
                    View v = a.findViewById(res);
                    if (v != null) {
                        v.setVisibility(View.GONE);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void clickSelf(Object self, String idName) {
        if (!(self instanceof Activity)) {
            return;
        }
        Activity a = (Activity) self;
        try {
            int id = a.getResources().getIdentifier(idName, "id", a.getPackageName());
            if (id != 0) {
                View v = a.findViewById(id);
                if (v instanceof Button) {
                    v.performClick();
                }
            }
        } catch (Throwable ignored) {
        }
    }

    // ─────────────────────────── 设置 / 权限管理 ───────────────────────────

    private static void hookSettingsUninstall(Hooker hk, ClassLoader cl) {
        int n = 0;
        try {
            Class<?> c = DexLocator.cls(cl,
                    "com.oplus.settings.adaptor.AppButtonsPreferenceControllerAdaptor");
            for (Method m : DexLocator.methods(c, "setUninstallButtonEnabled")) {
                hk.hook(m, (thiz, args) -> {
                    if (Cfg.on(FeatureKeys.ALLOW_DISABLE_SYSAPPS) && args.length > 0) {
                        args[0] = Boolean.TRUE;
                    }
                    return Hooker.PASS;
                });
                n++;
            }
        } catch (Throwable ignored) {
        }
        ok(hk, "disable-sysapps", n);
    }

    private static void hookPermissionUnlock(Hooker hk, ClassLoader cl) {
        try {
            Class<?> act = DexLocator.cls(cl,
                    "com.oplusos.securitypermission.permission.PermissionGroupsActivity");
            for (Method m : DexLocator.methods(act, "onCreate")) {
                hk.hook(m, (thiz, args) -> {
                    if (Cfg.on(FeatureKeys.UNLOCK_RESTRICTED) && thiz instanceof Activity
                            && args.length > 0 && args[0] instanceof Intent) {
                        try {
                            Bundle ex = ((Intent) args[0]).getExtras();
                            String pkg = null;
                            if (ex != null) {
                                pkg = ex.getString("packageName");
                                if (pkg == null) {
                                    pkg = ex.getString("mPackageName");
                                }
                            }
                            if (pkg != null) {
                                EcmLike.autoUnlock((Activity) thiz, pkg);
                            }
                        } catch (Throwable t) {
                            hk.log("ecm unlock failed", t);
                        }
                    }
                    return Hooker.PASS;
                });
            }
            ok(hk, "perm-unlock", 1);
        } catch (Throwable t) {
            hk.log("perm-unlock failed", t);
        }
    }

    // ─────────────────────────── 小布扫一扫 ───────────────────────────

    /** 全屏翻译：绕过不支持应用判定 */
    private static void hookFullScreenTranslation(Hooker hk, ClassLoader cl) {
        if (!Cfg.on(FeatureKeys.FULL_SCREEN_TRANSLATION)) {
            return;
        }
        int n = 0;
        try {
            Class<?> root = DexLocator.cls(cl,
                    "com.oplus.scanner.screentrans.ui.ScreenTranslationRootView");
            for (Method m : root.getDeclaredMethods()) {
                if ("s0".equals(m.getName()) && m.getParameterCount() == 1
                        && m.getReturnType() == boolean.class) {
                    hk.hook(m, (thiz, args) -> Boolean.FALSE);
                    n++;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> inner = DexLocator.cls(cl,
                    "com.oplus.scanner.screentrans.ui.ScreenTranslationRootView$onNotSupportApp$1");
            for (Method m : inner.getDeclaredMethods()) {
                if ("invokeSuspend".equals(m.getName()) && m.getParameterCount() == 1) {
                    hk.hook(m, (thiz, args) -> Boolean.FALSE);
                    n++;
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> cap = DexLocator.cls(cl,
                    "com.oplus.scanner.screentrans.ui.ScreenTranslationToolCapsule");
            for (Method m : cap.getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == boolean.class) {
                    hk.hook(m, (thiz, args) -> Boolean.TRUE);
                    n++;
                }
            }
        } catch (Throwable ignored) {
        }
        ok(hk, "full-screen-translation", n);
    }

    // ─────────────────────────── 主题商店 ───────────────────────────

    private static void hookThemeStore(Hooker hk, ClassLoader cl) {
        if (Cfg.on(FeatureKeys.THEME_VIP)) {
            hookThemeVip(hk, cl);
        }
        if (Cfg.on(FeatureKeys.THEME_SPLASH_ADS)) {
            hookThemeSplashAds(hk, cl);
        }
        if (Cfg.on(FeatureKeys.THEME_UPGRADE)) {
            replaceIn(hk, cl, "com.heytap.upgrade.UpgradeSDK",
                    FeatureKeys.THEME_UPGRADE, Hooker.RETURN_NULL, "checkUpgrade");
        }
    }

    /** 解锁 VIP：DTO 字段改写 + 用户状态伪造 + 价格归零 */
    private static void hookThemeVip(Hooker hk, ClassLoader cl) {
        try {
            Class<?> wpr = DexLocator.cls(cl,
                    "com.oppo.cdo.card.theme.dto.page.WeatherPageResponseDto");
            final Field wf = DexLocator.field(wpr, "vipStatus");
            for (Method m : DexLocator.methods(wpr, "getVipStatus")) {
                hk.hook(m, (thiz, args) -> {
                    if (thiz != null && wf != null) {
                        wf.setInt(thiz, 1);
                    }
                    return 1;
                });
            }
            Class<?> vud = DexLocator.cls(cl,
                    "com.oppo.cdo.card.theme.dto.vip.VipUserDto");
            final Field fs = DexLocator.field(vud, "vipStatus");
            final Field fd = DexLocator.field(vud, "vipDays");
            final Field fe = DexLocator.field(vud, "endTime");
            for (Method m : DexLocator.methods(vud, "getVipStatus", "getVipDays")) {
                final boolean status = "getVipStatus".equals(m.getName());
                hk.hook(m, (thiz, args) -> {
                    if (thiz != null) {
                        if (fs != null) {
                            fs.setInt(thiz, 1);
                        }
                        if (fd != null) {
                            fd.setInt(thiz, 99999);
                        }
                        if (fe != null) {
                            fe.setLong(thiz, 999999999L);
                        }
                    }
                    return status ? 1 : 99999;
                });
            }
            Class<?> rid = DexLocator.cls(cl,
                    "com.oppo.cdo.theme.domain.dto.response.ResourceItemDto");
            final Field f1 = DexLocator.field(rid, "isVip");
            final Field f2 = DexLocator.field(rid, "isVipAvailable");
            for (Method m : DexLocator.methods(rid, "getIsVip", "getIsVipAvailable")) {
                hk.hook(m, (thiz, args) -> {
                    if (thiz != null) {
                        if (f1 != null) {
                            f1.setInt(thiz, 1);
                        }
                        if (f2 != null) {
                            f2.setInt(thiz, 1);
                        }
                    }
                    return 1;
                });
            }
            ok(hk, "theme-vip-dto", 1);
        } catch (Throwable t) {
            hk.log("theme-vip-dto failed", t);
        }
        try {
            Class<?> uim = DexLocator.cls(cl, "com.nearme.themespace.UserInfoManager");
            for (Method m : uim.getDeclaredMethods()) {
                if ("w".equals(m.getName()) && m.getParameterCount() == 0) {
                    hk.hook(m, (thiz, args) -> 1);
                }
            }
            Class<?> vs = DexLocator.cls(cl,
                    "com.nearme.themespace.account.VipUserStatus");
            final Field valid = DexLocator.field(vs, "VALID");
            for (Method m : uim.getDeclaredMethods()) {
                if ("D".equals(m.getName()) && m.getParameterCount() == 0) {
                    hk.hook(m, new Hooker.Callback() {
                        @Override
                        public Object before(Object thiz, Object[] args) {
                            return Hooker.PASS;
                        }

                        @Override
                        public Object after(Object thiz, Object[] args, Object result) {
                            if (valid != null && result != null) {
                                try {
                                    return valid.get(result);
                                } catch (Throwable ignored) {
                                }
                            }
                            return null;
                        }
                    });
                }
            }
            ok(hk, "theme-vip-user", 1);
        } catch (Throwable ignored) {
        }
        try {
            Class<?> dr = DexLocator.cls(cl,
                    "com.nearme.themespace.download.mvvm.DownloadRepository");
            for (Method m : dr.getDeclaredMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length != 1 || !ps[0].getName().contains("LocalProductInfo")) {
                    continue;
                }
                hk.hook(m, (thiz, args) -> {
                    Object info = args.length > 0 ? args[0] : null;
                    if (info != null) {
                        Field a = DexLocator.field(info.getClass(), "mPurchaseStatus");
                        if (a != null) {
                            a.setInt(info, 1);
                        }
                        Field b = DexLocator.field(info.getClass(), "mResourceVipType");
                        if (b != null) {
                            b.setInt(info, 0);
                        }
                        Field c = DexLocator.field(info.getClass(), "forceVip");
                        if (c != null) {
                            c.setInt(info, 0);
                        }
                    }
                    return Hooker.PASS;
                });
            }
        } catch (Throwable ignored) {
        }
        try {
            Class<?> ter = DexLocator.cls(cl,
                    "com.nearme.themespace.trial.ThemeTrialExpireReceiver");
            for (Method m : DexLocator.methods(ter, "onReceive")) {
                hk.hook(m, (thiz, args) -> {
                    if (args.length > 1 && args[1] instanceof Intent) {
                        ((Intent) args[1]).setAction("");
                    }
                    return Hooker.PASS;
                });
            }
        } catch (Throwable ignored) {
        }
        try {
            List<Method> found = DexIndex.global(cl, new DexIndex.Query()
                    .named("getPrice").params(0).returns("D"));
            for (Method m : found) {
                hk.hook(m, (thiz, args) -> 0.0d);
            }
            ok(hk, "theme-vip-price", found.size());
        } catch (Throwable t) {
            hk.log("theme-vip-price failed", t);
        }
    }

    /** 移除开屏广告：广告数据置空 + 时间窗错开 */
    private static void hookThemeSplashAds(Hooker hk, ClassLoader cl) {
        try {
            Class<?> sd = DexLocator.cls(cl, "com.oppo.cdo.card.theme.dto.SplashDto");
            replace(hk, sd, FeatureKeys.THEME_SPLASH_ADS, Hooker.RETURN_NULL,
                    "getAdData", "getImage");
            for (Method m : DexLocator.methods(sd, "getStartTime")) {
                hk.hook(m, (thiz, args) -> Cfg.on(FeatureKeys.THEME_SPLASH_ADS)
                        ? System.currentTimeMillis() + 86400000L : Hooker.PASS);
            }
            for (Method m : DexLocator.methods(sd, "getEndTime")) {
                hk.hook(m, (thiz, args) -> Cfg.on(FeatureKeys.THEME_SPLASH_ADS)
                        ? System.currentTimeMillis() - 86400000L : Hooker.PASS);
            }
        } catch (Throwable ignored) {
        }
    }

    // ─────────────────────────── 智慧侧边栏 ───────────────────────────

    private static void hookSmartSidebar(Hooker hk, ClassLoader cl) {
        int n = 0;
        try {
            Class<?> upv = DexLocator.cls(cl,
                    "com.oplus.smartsidebar.panelview.edgepanel.mainpanel.UserPanelView");
            if (Cfg.on(FeatureKeys.SIDEBAR_ADD_LIMIT)) {
                DexLocator.setStaticInt(upv, "MAX_USER_ENTRY", 999);
            }
            n += repCanAdd(hk, upv, FeatureKeys.SIDEBAR_ADD_LIMIT);
            for (Method m : DexLocator.methods(upv, "performAdd")) {
                if (m.getParameterCount() != 1 || !m.getParameterTypes()[0]
                        .getName().endsWith("AppLabelData")) {
                    continue;
                }
                hk.hook(m, (thiz, args) -> {
                    if (!Cfg.on(FeatureKeys.SIDEBAR_ADD_LIMIT)) {
                        return Hooker.PASS;
                    }
                    try {
                        if (thiz != null) {
                            Field lf = DexLocator.field(thiz.getClass(), "mPanelData");
                            Field it = DexLocator.field(thiz.getClass(),
                                    "mEditOccupancyData");
                            Object list = lf == null ? null : lf.get(thiz);
                            Object item = it == null ? null : it.get(thiz);
                            if (list instanceof List && item != null) {
                                //noinspection unchecked
                                ((List<Object>) list).add(item);
                            }
                        }
                    } catch (Throwable t) {
                        hk.log("sidebar performAdd failed", t);
                    }
                    return Hooker.PASS;
                });
                n++;
            }
        } catch (Throwable t) {
            hk.log("smartsidebar hook failed", t);
        }
        try {
            Class<?> pmv = DexLocator.cls(cl,
                    "com.oplus.smartsidebar.panelview.edgepanel.PanelMainView");
            n += repCanAdd(hk, pmv, FeatureKeys.SIDEBAR_ADD_LIMIT);
        } catch (Throwable ignored) {
        }
        ok(hk, "smartsidebar", n);
    }

    private static int repCanAdd(Hooker hk, Class<?> c, String key) {
        int n = 0;
        for (Method m : DexLocator.methods(c, "canAdd")) {
            if (m.getParameterCount() != 1
                    || m.getParameterTypes()[0] != String.class
                    || m.getReturnType() != boolean.class) {
                continue;
            }
            hk.hook(m, (thiz, args) -> Cfg.on(key) ? Boolean.TRUE : Hooker.PASS);
            n++;
        }
        return n;
    }

    // ─────────────────────────── 桌面 ───────────────────────────

    private static void hookFolderBg(Hooker hk, ClassLoader cl) {
        try {
            Class<?> bg = DexLocator.cls(cl,
                    "com.android.launcher3.folder.OplusPreviewBackground");
            final Field f = DexLocator.field(bg, "mBgDrawable");
            for (Method m : DexLocator.methods(bg, "setBackground", "setup")) {
                hk.hook(m, (thiz, args) -> {
                    if (Cfg.on(FeatureKeys.FOLDER_BG) && thiz != null && f != null) {
                        f.set(thiz, null);
                    }
                    return Hooker.PASS;
                });
            }
            for (Method m : DexLocator.methods(bg, "drawBackground")) {
                hk.hook(m, (thiz, args) -> Cfg.on(FeatureKeys.FOLDER_BG)
                        ? Hooker.RETURN_NULL : Hooker.PASS);
            }
            ok(hk, "folder-bg", 1);
        } catch (Throwable t) {
            hk.log("folder-bg failed", t);
        }
        try {
            Class<?> am = DexLocator.cls(cl,
                    "com.android.launcher3.folder.OplusFolderAnimationManager");
            for (Method m : DexLocator.methods(am, "getFolderBackgroundAnimator")) {
                hk.hook(m, (thiz, args) -> Cfg.on(FeatureKeys.FOLDER_BG)
                        ? Hooker.RETURN_NULL : Hooker.PASS);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookLauncherLayout(Hooker hk, ClassLoader cl) {
        try {
            Class<?> ui = DexLocator.cls(cl, "com.android.launcher.UiConfig");
            replace(hk, ui, FeatureKeys.LAYOUT_CUSTOM, Boolean.FALSE, "isSupportLayout");
            if (Build.VERSION.SDK_INT >= 37) {
                for (Method m : DexLocator.methods(ui, "getSupportLayout")) {
                    hk.hook(m, (thiz, args) -> {
                        if (!Cfg.on(FeatureKeys.LAYOUT_CUSTOM)) {
                            return Hooker.PASS;
                        }
                        int maxCols = Cfg.num(FeatureKeys.P_LAYOUT_COLS);
                        int maxRows = Cfg.num(FeatureKeys.P_LAYOUT_ROWS);
                        ArrayList<Pair<Integer, Pair<Integer, Integer>>> out =
                                new ArrayList<>();
                        for (int col = 4; col <= maxCols; col++) {
                            for (int row = 6; row <= maxRows; row++) {
                                out.add(new Pair<>(col, new Pair<>(row, row + 1)));
                            }
                        }
                        return out;
                    });
                }
                try {
                    Class<?> ifu = DexLocator.cls(cl,
                            "com.android.launcher.iconfallen.IconFallenUtils");
                    for (Method m : DexLocator.methods(ifu, "getLogicCellX")) {
                        hk.hook(m, new Hooker.Callback() {
                            @Override
                            public Object before(Object thiz, Object[] args) {
                                return Hooker.PASS;
                            }

                            @Override
                            public Object after(Object thiz, Object[] args, Object result) {
                                if (Cfg.on(FeatureKeys.LAYOUT_CUSTOM)
                                        && result instanceof Integer
                                        && (Integer) result > 4) {
                                    return 4;
                                }
                                return null;
                            }
                        });
                    }
                } catch (Throwable ignored) {
                }
            } else {
                try {
                    Class<?> tb = DexLocator.cls(cl,
                            "com.android.launcher.togglebar.adapter.ToggleBarLayoutAdapter");
                    final Field cols = DexLocator.field(tb, "MIN_MAX_COLUMN");
                    final Field rows = DexLocator.field(tb, "MIN_MAX_ROW");
                    for (Method m : DexLocator.methods(tb,
                            "initToggleBarLayoutConfigs")) {
                        hk.hook(m, (thiz, args) -> {
                            if (Cfg.on(FeatureKeys.LAYOUT_CUSTOM) && thiz != null) {
                                setArr(cols, thiz, Cfg.num(FeatureKeys.P_LAYOUT_COLS));
                                setArr(rows, thiz, Cfg.num(FeatureKeys.P_LAYOUT_ROWS));
                            }
                            return Hooker.PASS;
                        });
                    }
                } catch (Throwable ignored) {
                }
            }
            ok(hk, "layout", 1);
        } catch (Throwable t) {
            hk.log("layout failed", t);
        }
    }

    private static void setArr(Field f, Object self, int v) {
        try {
            int[] arr = (int[]) f.get(self);
            if (arr != null && arr.length > 1) {
                arr[1] = v;
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookBadges(Hooker hk, ClassLoader cl) {
        try {
            Class<?> bi = DexLocator.cls(cl, "com.android.launcher3.icons.BitmapInfo");
            final Field flagsF = DexLocator.field(bi, "flags");
            final Field badgeF = DexLocator.field(bi, "badgeInfo");
            for (Method m : DexLocator.methods(bi, "applyFlags")) {
                hk.hook(m, (thiz, args) -> {
                    boolean any = Cfg.on(FeatureKeys.BADGE_SHORTCUT)
                            || Cfg.on(FeatureKeys.BADGE_WORK)
                            || Cfg.on(FeatureKeys.BADGE_CLONE);
                    if (!any || thiz == null || flagsF == null) {
                        return Hooker.PASS;
                    }
                    int flag = flagsF.getInt(thiz);
                    int creation = 0;
                    for (Object a : args) {
                        if (a instanceof Integer) {
                            creation = (Integer) a;
                        }
                    }
                    if ((creation & 2) != 0) {
                        return Hooker.PASS;
                    }
                    Object badge = badgeF == null ? null : badgeF.get(thiz);
                    if (badge != null && Cfg.on(FeatureKeys.BADGE_SHORTCUT)) {
                        return Hooker.RETURN_NULL;
                    }
                    if ((flag & 4) == 0) {
                        if ((flag & 1) != 0 && Cfg.on(FeatureKeys.BADGE_WORK)) {
                            return Hooker.RETURN_NULL;
                        }
                    } else if (Cfg.on(FeatureKeys.BADGE_CLONE)) {
                        return Hooker.RETURN_NULL;
                    }
                    return Hooker.PASS;
                });
            }
            ok(hk, "badges", 1);
        } catch (Throwable t) {
            hk.log("badges failed", t);
        }
        try {
            List<Method> found = DexIndex.global(cl, new DexIndex.Query()
                    .params(1).returns("Landroid/graphics/drawable/Drawable;")
                    .paramTypes("Landroid/os/UserHandle;"));
            for (Method m : found) {
                hk.hook(m, (thiz, args) -> Cfg.on(FeatureKeys.BADGE_CLONE)
                        ? Hooker.RETURN_NULL : Hooker.PASS);
            }
            ok(hk, "clone-badge", found.size());
        } catch (Throwable ignored) {
        }
    }

    // ─────────────────────────── 安全中心 SafeProvider ───────────────────────────

    private static final String SAFE_AUTHORITY = "com.oplus.provider.SafeProvider";
    private static final String[] SAFE_MODULE_NAMES = {
            "total", "clean", "traffic", "blacklist", "permission", "virus", "power"};
    private static final Set<String> SAFE_VD_KEYS = new HashSet<>(Arrays.asList(
            "vd_auto_update", "vd_last_scan_time", "vd_last_scan_result"));
    private static final java.util.Map<String, String> SAFE_VD_VALUES =
            new java.util.HashMap<>();
    private static final Pattern SAFE_PKG_PATTERN =
            Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+");

    static {
        SAFE_VD_VALUES.put("vd_auto_update", "1");
        SAFE_VD_VALUES.put("vd_last_scan_result", "0");
    }

    private static void hookSafeProvider(Hooker hk, ClassLoader cl) {
        int n = 0;
        try {
            Class<?> c = DexLocator.cls(cl,
                    "com.oplus.safecenter.common.provider.SafeProvider");
            for (Method m : DexLocator.methods(c, "query")) {
                hk.hook(m, (thiz, args) -> {
                    Object r = safeQueryResult(args);
                    return r != null ? r : Hooker.PASS;
                });
                n++;
            }
            for (Method m : DexLocator.methods(c, "insert")) {
                hk.hook(m, (thiz, args) -> {
                    safeMutateValues(args);
                    return Hooker.PASS;
                });
                n++;
            }
            for (Method m : DexLocator.methods(c, "update")) {
                hk.hook(m, (thiz, args) -> {
                    safeMutateValues(args);
                    Integer r = safeUpdateResult(args);
                    return r != null ? r : Hooker.PASS;
                });
                n++;
            }
            for (Method m : DexLocator.methods(c, "delete")) {
                hk.hook(m, (thiz, args) -> {
                    Integer r = safeDeleteResult(args);
                    return r != null ? r : Hooker.PASS;
                });
                n++;
            }
        } catch (Throwable t) {
            hk.log("safecenter provider hook failed", t);
        }
        ok(hk, "safecenter-provider", n);
    }

    private static String[] safeCols(String[] projection, String... def) {
        return projection != null && projection.length > 0 ? projection : def;
    }

    private static Object safeQueryResult(Object[] args) {
        if (args == null || args.length < 1 || !(args[0] instanceof Uri)) {
            return null;
        }
        String path = ((Uri) args[0]).getLastPathSegment();
        if (path == null) {
            return null;
        }
        String[] projection = args.length > 1 && args[1] instanceof String[]
                ? (String[]) args[1] : null;
        String selection = args.length > 2 && args[2] instanceof String
                ? (String) args[2] : null;
        String[] selArgs = args.length > 3 && args[3] instanceof String[]
                ? (String[]) args[3] : null;
        switch (path) {
            case "settings": {
                if (!Cfg.on(FeatureKeys.FAKE_SCAN_RESULT)) {
                    return null;
                }
                Set<String> keys = safeVdKeys(selection, selArgs);
                if (keys.isEmpty()) {
                    return null;
                }
                String[] cols = safeCols(projection, "_id", "key", "value");
                MatrixCursor cursor = new MatrixCursor(cols);
                int id = 0;
                for (String key : keys) {
                    Object[] row = new Object[cols.length];
                    for (int j = 0; j < cols.length; j++) {
                        String col = cols[j];
                        if ("_id".equals(col)) {
                            row[j] = id;
                        } else if ("key".equals(col)) {
                            row[j] = key;
                        } else if ("value".equals(col)) {
                            row[j] = "vd_last_scan_time".equals(key)
                                    ? String.valueOf(System.currentTimeMillis())
                                    : SAFE_VD_VALUES.get(key);
                        }
                    }
                    cursor.addRow(row);
                    id++;
                }
                return cursor;
            }
            case "vd_virus": {
                if (!Cfg.on(FeatureKeys.CLEAR_RISK_APPS)) {
                    return null;
                }
                return new MatrixCursor(safeCols(projection,
                        "_id", "apkType", "packageName", "path"));
            }
            case "ssm_status_track": {
                if (!Cfg.on(FeatureKeys.CLEAR_STATUS_ALERTS)) {
                    return null;
                }
                String[] cols = safeCols(projection,
                        "_id", "module_name", "status_dot", "status_hint", "status_alert");
                MatrixCursor cursor = new MatrixCursor(cols);
                int id = 1;
                for (String name : SAFE_MODULE_NAMES) {
                    Object[] row = new Object[cols.length];
                    for (int j = 0; j < cols.length; j++) {
                        String col = cols[j];
                        if ("_id".equals(col)) {
                            row[j] = id;
                        } else if ("module_name".equals(col)) {
                            row[j] = name;
                        }
                    }
                    cursor.addRow(row);
                    id++;
                }
                return cursor;
            }
            case "virus_whitelist": {
                if (!Cfg.on(FeatureKeys.VIRUS_WHITELIST)) {
                    return null;
                }
                LinkedHashSet<String> pkgs =
                        safeExtractPackages(selection, "pkg_name", selArgs);
                String[] cols = safeCols(projection, "_id", "pkg_name", "path", "type");
                MatrixCursor cursor = new MatrixCursor(cols);
                int id = 1;
                for (String pkg : pkgs) {
                    Object[] row = new Object[cols.length];
                    for (int j = 0; j < cols.length; j++) {
                        String col = cols[j];
                        if ("_id".equals(col)) {
                            row[j] = id;
                        } else if ("pkg_name".equals(col)) {
                            row[j] = pkg;
                        } else if ("path".equals(col)) {
                            row[j] = "";
                        } else if ("type".equals(col)) {
                            row[j] = 0;
                        }
                    }
                    cursor.addRow(row);
                    id++;
                }
                return cursor;
            }
            case "packageinstaller_whitelist": {
                if (!Cfg.on(FeatureKeys.INSTALLER_WHITELIST)) {
                    return null;
                }
                LinkedHashSet<String> pkgs =
                        safeExtractPackages(selection, "packageName", selArgs);
                String[] cols = safeCols(projection, "_id", "packageName", "switch");
                MatrixCursor cursor = new MatrixCursor(cols);
                int id = 1;
                for (String pkg : pkgs) {
                    Object[] row = new Object[cols.length];
                    for (int j = 0; j < cols.length; j++) {
                        String col = cols[j];
                        if ("_id".equals(col)) {
                            row[j] = id;
                        } else if ("packageName".equals(col)) {
                            row[j] = pkg;
                        } else if ("switch".equals(col)) {
                            row[j] = 1;
                        }
                    }
                    cursor.addRow(row);
                    id++;
                }
                return cursor;
            }
            default:
                return null;
        }
    }

    /** insert/update 共用：按路径补全 ContentValues 默认值 */
    private static void safeMutateValues(Object[] args) {
        if (args == null || args.length < 2 || !(args[0] instanceof Uri)) {
            return;
        }
        Uri uri = (Uri) args[0];
        if (!SAFE_AUTHORITY.equals(uri.getAuthority())) {
            return;
        }
        String path = uri.getLastPathSegment();
        ContentValues values = args[1] instanceof ContentValues
                ? (ContentValues) args[1] : null;
        if (path == null || values == null) {
            return;
        }
        if ("settings".equals(path)) {
            if (Cfg.on(FeatureKeys.FAKE_SCAN_RESULT)) {
                safeFillVdDefaults(values);
            }
        } else if ("virus_whitelist".equals(path)) {
            if (Cfg.on(FeatureKeys.VIRUS_WHITELIST)) {
                if (!values.containsKey("path")) {
                    values.put("path", "");
                }
                if (!values.containsKey("type")) {
                    values.put("type", 0);
                }
            }
        } else if ("packageinstaller_whitelist".equals(path)) {
            if (Cfg.on(FeatureKeys.INSTALLER_WHITELIST)
                    && !values.containsKey("switch")) {
                values.put("switch", 1);
            }
        }
    }

    /** update：values 为空且命中伪造键集合时直接返回 1 行受影响 */
    private static Integer safeUpdateResult(Object[] args) {
        if (args == null || args.length < 2 || !(args[0] instanceof Uri)) {
            return null;
        }
        Uri uri = (Uri) args[0];
        if (!SAFE_AUTHORITY.equals(uri.getAuthority())) {
            return null;
        }
        if (args[1] instanceof ContentValues) {
            return null;
        }
        String path = uri.getLastPathSegment();
        if (!"settings".equals(path) || !Cfg.on(FeatureKeys.FAKE_SCAN_RESULT)) {
            return null;
        }
        String selection = args.length > 2 && args[2] instanceof String
                ? (String) args[2] : null;
        String[] selArgs = args.length > 3 && args[3] instanceof String[]
                ? (String[]) args[3] : null;
        return safeVdKeys(selection, selArgs).isEmpty() ? null : 1;
    }

    /** delete：开启对应开关时对三条业务路径一律返回已删除 */
    private static Integer safeDeleteResult(Object[] args) {
        if (args == null || args.length < 1 || !(args[0] instanceof Uri)) {
            return null;
        }
        Uri uri = (Uri) args[0];
        if (!SAFE_AUTHORITY.equals(uri.getAuthority())) {
            return null;
        }
        String path = uri.getLastPathSegment();
        if ("settings".equals(path) && Cfg.on(FeatureKeys.FAKE_SCAN_RESULT)) {
            return 1;
        }
        if ("virus_whitelist".equals(path) && Cfg.on(FeatureKeys.VIRUS_WHITELIST)) {
            return 1;
        }
        if ("packageinstaller_whitelist".equals(path)
                && Cfg.on(FeatureKeys.INSTALLER_WHITELIST)) {
            return 1;
        }
        return null;
    }

    private static Set<String> safeVdKeys(String selection, String[] selArgs) {
        Set<String> out = new LinkedHashSet<>();
        if (selArgs != null) {
            for (String a : selArgs) {
                if (a != null && SAFE_VD_KEYS.contains(a)) {
                    out.add(a);
                }
            }
        }
        if (out.isEmpty() && selection != null) {
            for (String k : SAFE_VD_KEYS) {
                if (selection.contains(k)) {
                    out.add(k);
                }
            }
        }
        return out;
    }

    private static LinkedHashSet<String> safeExtractPackages(String selection,
            String colName, String[] selArgs) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (selArgs != null) {
            for (String a : selArgs) {
                if (a != null && a.indexOf('.') >= 0) {
                    out.add(a);
                }
            }
        }
        if (out.isEmpty() && selection != null && selection.contains(colName)) {
            java.util.regex.Matcher m = SAFE_PKG_PATTERN.matcher(selection);
            while (m.find()) {
                out.add(m.group());
            }
        }
        return out;
    }

    private static void safeFillVdDefaults(ContentValues values) {
        String key = values.getAsString("key");
        if (key == null) {
            return;
        }
        String value;
        if ("vd_last_scan_time".equals(key)) {
            value = String.valueOf(System.currentTimeMillis());
        } else {
            value = SAFE_VD_VALUES.get(key);
            if (value == null) {
                return;
            }
        }
        values.put("value", value);
    }

    // ─────────────────────────── 通用工具 ───────────────────────────

    private static void ok(Hooker hk, String what, int n) {
        hk.log(what + " hooked x" + n);
    }

    /** 在类上按方法名集合替换返回值（开关关闭时 PASS） */
    private static void replace(Hooker hk, Class<?> c, String key, Object val,
            String... names) {
        if (c == null) {
            return;
        }
        for (Method m : DexLocator.methods(c, names)) {
            hk.hook(m, (thiz, args) -> Cfg.on(key) ? val : Hooker.PASS);
        }
    }

    private static void replaceIn(Hooker hk, ClassLoader cl, String className,
            String key, Object val, String... names) {
        try {
            replace(hk, DexLocator.cls(cl, className), key, val, names);
        } catch (Throwable ignored) {
        }
    }
}
