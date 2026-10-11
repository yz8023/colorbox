package com.xiaokan.colorbox;

import java.lang.reflect.Member;

/**
 * Hook 接线抽象。
 *
 * 模块有两个入口（HookEntry = libxposed API 102，LegacyEntry = 经典 XposedBridge），
 * 每个入口向功能层提供一个 Hooker 实现；功能代码只针对 Hooker 编写一份，
 * 彻底消除"现代/经典"两套镜像实现。
 *
 * 决策协议（Callback.before）：
 *   返回 PASS            → 继续原方法；args 数组内的修改会回写到调用
 *   返回 RETURN_NULL     → 直接返回 null
 *   返回 Boolean/其他对象 → 作为方法返回值
 *
 * Callback.after 返回 null 表示保留原结果，返回其他值则替换结果。
 */
public interface Hooker {

    Object PASS = new Object() {
        @Override
        public String toString() {
            return "PASS";
        }
    };

    Object RETURN_NULL = new Object() {
        @Override
        public String toString() {
            return "RETURN_NULL";
        }
    };

    void hook(Member member, Callback callback);

    /** 让方法退出 JIT 内联（仅现代入口支持，经典实现为空操作） */
    void deoptimize(Member member);

    void log(String message);

    void log(String message, Throwable error);

    interface Callback {
        Object before(Object thisObject, Object[] args) throws Throwable;

        default Object after(Object thisObject, Object[] args, Object result)
                throws Throwable {
            return null;
        }

        /**
         * 原方法（或 before/after）抛出异常时的兜底：
         * 返回 PASS 重新抛出；RETURN_NULL 吞掉异常返回 null；其他值作为返回值。
         */
        default Object onError(Throwable error) throws Throwable {
            return PASS;
        }
    }
}
