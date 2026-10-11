package com.xiaokan.colorbox;

import java.lang.reflect.Member;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Hooker 的经典 XposedBridge 后端（LSPosed/XposedBridge 兼容）。
 *
 * 语义与 ModernHooker 一致；无 deoptimize 能力（空实现）。
 */
final class LegacyHooker implements Hooker {

    @Override
    public void hook(Member member, Callback cb) {
        try {
            XposedBridge.hookMethod(member, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object decision;
                    try {
                        decision = cb.before(param.thisObject, param.args);
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                        decision = Hooker.PASS;
                    }
                    if (decision == Hooker.RETURN_NULL) {
                        param.setResult(null);
                    } else if (decision != Hooker.PASS) {
                        param.setResult(decision);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) {
                        Object fallback;
                        try {
                            fallback = cb.onError(param.throwable);
                        } catch (Throwable t) {
                            fallback = Hooker.PASS;
                        }
                        if (fallback != Hooker.PASS) {
                            param.setThrowable(null);
                            param.setResult(fallback == Hooker.RETURN_NULL
                                    ? null : fallback);
                        }
                        return;
                    }
                    Object after;
                    try {
                        after = cb.after(param.thisObject, param.args, param.getResult());
                    } catch (Throwable t) {
                        XposedBridge.log(t);
                        return;
                    }
                    if (after != null) {
                        param.setResult(after);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    @Override
    public void deoptimize(Member member) {
        // 经典 API 无 deoptimize；不执行任何操作
    }

    @Override
    public void log(String message) {
        XposedBridge.log("ColorBox: " + message);
    }

    @Override
    public void log(String message, Throwable error) {
        XposedBridge.log("ColorBox: " + message);
        XposedBridge.log(error);
    }
}
