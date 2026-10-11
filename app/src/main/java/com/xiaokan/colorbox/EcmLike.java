package com.xiaokan.colorbox;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;

import java.lang.reflect.Method;

/**
 * 权限管理「受限制的设置」自动解锁（进程内反射实现）。
 *
 * 两条路径：
 *   - ECM 模式（Android 15+ enhancedConfirmation）：ecm 服务 isRestricted 判定后逐级放行
 *   - 传统模式：AppOps OPSTR_ACCESS_RESTRICTED_SETTINGS note 后 setMode 归零
 */
final class EcmLike {

    private static final String RESTRICTED_OP =
            "OPSTR_ACCESS_RESTRICTED_SETTINGS";

    static void autoUnlock(Activity activity, String packName) {
        if (isEcmMode()) {
            Object ecm = activity.getSystemService("ecm_enhanced_confirmation");
            if (ecm == null) {
                return;
            }
            try {
                Method isRestricted = ecm.getClass().getMethod("isRestricted",
                        String.class, String.class);
                boolean restricted = (Boolean) isRestricted.invoke(ecm, packName,
                        "android:bind_accessibility_service");
                if (!restricted) {
                    return;
                }
                invokeEcm(ecm, "isClearRestrictionAllowed", packName);
                invokeEcm(ecm, "setClearRestrictionAllowed", packName);
                invokeEcm(ecm, "clearRestriction", packName);
            } catch (Throwable ignored) {
            }
        } else {
            try {
                AppOpsManager appOps =
                        (AppOpsManager) activity.getSystemService(Context.APP_OPS_SERVICE);
                java.lang.reflect.Field f = AppOpsManager.class.getDeclaredField(RESTRICTED_OP);
                f.setAccessible(true);
                String op = (String) f.get(null);
                int uid = activity.getPackageManager().getPackageUid(packName, 0);
                int note = appOps.noteOpNoThrow(op, uid, packName, null, null);
                if (note == 0 || note == 3) {
                    return;
                }
                Method setMode = appOps.getClass().getMethod("setMode",
                        int.class, int.class, String.class, int.class);
                setMode.invoke(appOps, 119, uid, packName, 0);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void invokeEcm(Object ecm, String name, String arg) throws Exception {
        Method m = ecm.getClass().getMethod(name, String.class);
        m.invoke(ecm, arg);
    }

    private static boolean isEcmMode() {
        try {
            Class<?> flags = Class.forName(
                    "com.android.internal.hidden_from_bootclasspath.android.permission.flags.Flags");
            Method m = flags.getMethod("enhancedConfirmationModeApisEnabled");
            return (Boolean) m.invoke(null);
        } catch (Throwable t) {
            return false;
        }
    }

    private EcmLike() {
    }
}
