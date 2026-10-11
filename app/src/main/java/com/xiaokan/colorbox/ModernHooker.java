package com.xiaokan.colorbox;

import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Executable;
import java.lang.reflect.Member;

import io.github.libxposed.api.XposedModule;

/**
 * Hooker 的 libxposed API 102 后端。
 *
 * before 返回 PASS → 以（可能改写后的）args 继续原方法；
 * RETURN_NULL → 返回 null；其他值 → 作为返回值。
 * 原方法抛出异常时交由 Callback.onError 决定重抛或吞掉。
 */
final class ModernHooker implements Hooker {

    private static final String TAG = "ColorBox";

    private final XposedModule module;

    ModernHooker(XposedModule module) {
        this.module = module;
    }

    @Override
    public void hook(Member member, Callback cb) {
        try {
            module.hook((Executable) member).intercept(chain -> {
                Object[] args = chain.getArgs().toArray();
                Object decision;
                try {
                    decision = cb.before(chain.getThisObject(), args);
                } catch (Throwable t) {
                    module.log(Log.WARN, TAG, "before failed: "
                            + member.getDeclaringClass().getName()
                            + "." + member.getName(), t);
                    decision = Hooker.PASS;
                }
                Object result;
                if (decision == Hooker.PASS) {
                    try {
                        result = chain.proceed(args);
                    } catch (Throwable t) {
                        Object fallback;
                        try {
                            fallback = cb.onError(t);
                        } catch (Throwable t2) {
                            fallback = Hooker.PASS;
                        }
                        if (fallback == Hooker.PASS) {
                            throw t;
                        }
                        return fallback == Hooker.RETURN_NULL ? null : fallback;
                    }
                } else {
                    result = decision == Hooker.RETURN_NULL ? null : decision;
                }
                Object after;
                try {
                    after = cb.after(chain.getThisObject(), args, result);
                } catch (Throwable t) {
                    module.log(Log.WARN, TAG, "after failed: "
                            + member.getDeclaringClass().getName()
                            + "." + member.getName(), t);
                    after = null;
                }
                return after == null ? result : after;
            });
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "hook failed: "
                    + member.getDeclaringClass().getName() + "." + member.getName(), t);
        }
    }

    @Override
    public void deoptimize(Member member) {
        try {
            module.deoptimize((Executable) member);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void log(String message) {
        module.log(Log.INFO, TAG, message);
    }

    @Override
    public void log(String message, Throwable error) {
        module.log(Log.WARN, TAG, message, error);
    }

    /** 供入口把 getRemotePreferences 桥接到 Cfg.Remote */
    static Cfg.Remote remoteOf(final XposedModule module, final String name) {
        final SharedPreferences prefs = module.getRemotePreferences(name);
        return new Cfg.Remote() {
            @Override
            public android.os.Bundle read(String key) {
                try {
                    return RemoteCfg.flatten(new RemoteCfg.Reader() {
                        @Override
                        public boolean contains(String k) {
                            return prefs.contains(k);
                        }

                        @Override
                        public boolean getBoolean(String k, boolean def) {
                            return prefs.getBoolean(k, def);
                        }

                        @Override
                        public int getInt(String k, int def) {
                            return prefs.getInt(k, def);
                        }

                        @Override
                        public String getString(String k, String def) {
                            return prefs.getString(k, def);
                        }
                    }, key);
                } catch (Throwable t) {
                    return null;
                }
            }
        };
    }
}
