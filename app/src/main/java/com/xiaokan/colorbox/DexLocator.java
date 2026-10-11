package com.xiaokan.colorbox;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 反射工具箱：类/方法/字段定位、静态字段强写（Unsafe 支持 final）、缓存。
 * 供各功能组在 OEM 混淆/多版本环境下稳健地找到 hook 目标。
 */
final class DexLocator {

    private static final Map<String, Class<?>> CLASS_CACHE = new HashMap<>();
    private static Object theUnsafe;
    private static Method objectFieldOffset;
    private static Method staticFieldBase;
    private static Method staticFieldOffset;
    private static Method putObject;
    private static Method putInt;
    private static Method putBoolean;

    static {
        try {
            Class<?> u = Class.forName("sun.misc.Unsafe");
            Field f = u.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            theUnsafe = f.get(null);
            objectFieldOffset = u.getMethod("objectFieldOffset", Field.class);
            staticFieldBase = u.getMethod("staticFieldBase", Field.class);
            staticFieldOffset = u.getMethod("staticFieldOffset", Field.class);
            putObject = u.getMethod("putObject", Object.class, long.class, Object.class);
            putInt = u.getMethod("putInt", Object.class, long.class, int.class);
            putBoolean = u.getMethod("putBoolean", Object.class, long.class, boolean.class);
        } catch (Throwable ignored) {
        }
    }

    /** loadClass 带缓存；失败返回 null */
    static Class<?> cls(ClassLoader cl, String name) {
        String key = cl + "@" + name;
        Class<?> c = CLASS_CACHE.get(key);
        if (c != null) {
            return c;
        }
        try {
            c = Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
        CLASS_CACHE.put(key, c);
        return c;
    }

    /** 类中按名字取全部声明方法（含私有） */
    static List<Method> methods(Class<?> c, String... names) {
        List<Method> out = new ArrayList<>();
        if (c == null) {
            return out;
        }
        for (Method m : c.getDeclaredMethods()) {
            for (String n : names) {
                if (n.equals(m.getName())) {
                    m.setAccessible(true);
                    out.add(m);
                    break;
                }
            }
        }
        return out;
    }

    /** 按名字+参数个数找第一个声明方法 */
    static Method method(Class<?> c, String name, int paramCount) {
        if (c == null) {
            return null;
        }
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    /** 按名字+精确参数类型找声明方法 */
    static Method method(Class<?> c, String name, Class<?>... params) {
        if (c == null) {
            return null;
        }
        try {
            Method m = c.getDeclaredMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            return null;
        }
    }

    static Field field(Class<?> c, String name) {
        if (c == null) {
            return null;
        }
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    static Object getStatic(Class<?> c, String name) {
        Field f = field(c, name);
        if (f == null) {
            return null;
        }
        try {
            return f.get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 强写静态字段；final 字段（AOT/JIT 内联值）走 Unsafe 直写内存。
     * 用于 OShin 等项目里的 static final 常量替换（如上限值、默认包名数组）。
     */
    static boolean setStaticValue(Class<?> c, String name, Object value) {
        Field f = field(c, name);
        if (f == null || !Modifier.isStatic(f.getModifiers())) {
            return false;
        }
        try {
            f.set(null, value);
            return true;
        } catch (Throwable accessFail) {
            if (theUnsafe == null) {
                return false;
            }
            try {
                Object base = staticFieldBase.invoke(theUnsafe, f);
                long offset = (Long) staticFieldOffset.invoke(theUnsafe, f);
                putObject.invoke(theUnsafe, base, offset, value);
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
    }

    static boolean setStaticInt(Class<?> c, String name, int v) {
        Field f = field(c, name);
        if (f == null || !Modifier.isStatic(f.getModifiers())) {
            return false;
        }
        boolean boolField = f.getType() == boolean.class;
        try {
            if (boolField) {
                f.setBoolean(null, v != 0);
            } else {
                f.setInt(null, v);
            }
            return true;
        } catch (Throwable accessFail) {
            if (theUnsafe == null) {
                return false;
            }
            try {
                Object base = staticFieldBase.invoke(theUnsafe, f);
                long offset = (Long) staticFieldOffset.invoke(theUnsafe, f);
                if (boolField) {
                    putBoolean.invoke(theUnsafe, base, offset, v != 0);
                } else {
                    putInt.invoke(theUnsafe, base, offset, v);
                }
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
    }

    private DexLocator() {
    }
}
