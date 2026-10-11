package com.xiaokan.colorbox;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 轻量 DEX 方法索引（零依赖）。
 *
 * 部分功能的 hook 目标是混淆方法，只能靠「方法体引用的字符串常量 / 调用方方法名 /
 * 引用的字段类型 / 参数形状」识别。本类在目标进程内解析宿主 classes*.dex 建立
 * 方法级索引，再把命中项还原为反射 Method，供 MultiFeatures 等功能组查询。
 */
final class DexIndex {

    /** 查询条件：全部满足才命中 */
    static final class Query {
        private String[] names;
        private Integer params;
        private Boolean voidReturn;
        private String returnDesc;
        private String[] paramTypes;
        private final Set<String> callerNames = new HashSet<>();
        private final Set<String> fieldTypes = new HashSet<>();
        private final Set<String> strings = new HashSet<>();

        Query named(String... n) {
            this.names = n;
            return this;
        }

        Query params(int n) {
            this.params = n;
            return this;
        }

        Query voidReturn() {
            this.voidReturn = Boolean.TRUE;
            return this;
        }

        Query returns(String desc) {
            this.returnDesc = desc;
            return this;
        }

        /** 按位置校验参数类型描述符（-1 结尾前的顺序匹配） */
        Query paramTypes(String... descs) {
            this.paramTypes = descs;
            return this;
        }

        Query calledBy(String name) {
            callerNames.add(name);
            return this;
        }

        Query touchesFieldTypes(String... descs) {
            for (String d : descs) {
                fieldTypes.add(d);
            }
            return this;
        }

        Query hasStrings(String... all) {
            for (String s : all) {
                strings.add(s);
            }
            return this;
        }

        boolean accept(String name, int paramCount, String retDesc, List<String> mParamTypes,
                Set<String> mStrings, Set<String> mFieldTypes, Set<String> mCallers) {
            if (names != null) {
                boolean hit = false;
                for (String n : names) {
                    if (n.equals(name)) {
                        hit = true;
                        break;
                    }
                }
                if (!hit) {
                    return false;
                }
            }
            if (params != null && paramCount != params) {
                return false;
            }
            if (voidReturn != null && voidReturn && !"V".equals(retDesc)) {
                return false;
            }
            if (returnDesc != null && !returnDesc.equals(retDesc)) {
                return false;
            }
            if (paramTypes != null) {
                if (mParamTypes.size() < paramTypes.length) {
                    return false;
                }
                for (int i = 0; i < paramTypes.length; i++) {
                    if (!paramTypes[i].equals(mParamTypes.get(i))) {
                        return false;
                    }
                }
            }
            for (String s : strings) {
                if (!mStrings.contains(s)) {
                    return false;
                }
            }
            for (String f : fieldTypes) {
                if (!mFieldTypes.contains(f)) {
                    return false;
                }
            }
            for (String c : callerNames) {
                if (!mCallers.contains(c)) {
                    return false;
                }
            }
            return true;
        }
    }

    static List<Method> inClass(ClassLoader cl, Class<?> clazz, Query q) {
        List<Method> out = new ArrayList<>();
        try {
            Host host = host(cl);
            if (host == null) {
                return out;
            }
            String desc = "L" + clazz.getName().replace('.', '/') + ";";
            for (Dex d : host.dexes) {
                int typeIdx = d.findTypeIdx(desc);
                if (typeIdx < 0) {
                    continue;
                }
                for (MethodInfo mi : d.methodsOf(typeIdx)) {
                    if (q.accept(mi.name, mi.paramCount, mi.returnDesc, mi.paramTypes,
                            mi.strings, mi.fieldTypes, mi.callerNames)) {
                        Method m = resolveIn(clazz, mi.name, mi.paramCount);
                        if (m != null) {
                            out.add(m);
                        }
                    }
                }
                if (!out.isEmpty()) {
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    static Method firstInClass(ClassLoader cl, Class<?> clazz, Query q) {
        List<Method> l = inClass(cl, clazz, q);
        return l.isEmpty() ? null : l.get(0);
    }

    /** 全 dex 扫描（跳过系统前缀类），首个命中 dex 内的全部命中方法 */
    static List<Method> global(ClassLoader cl, Query q) {
        List<Method> out = new ArrayList<>();
        try {
            Host host = host(cl);
            if (host == null) {
                return out;
            }
            for (Dex d : host.dexes) {
                for (int i = 0; i < d.classDefsSize; i++) {
                    int off = d.classDefsOff + i * 32;
                    int typeIdx = d.u4(off);
                    String td = d.typeDesc(typeIdx);
                    if (td == null || td.length() < 3
                            || td.startsWith("Landroid/") || td.startsWith("Ljava/")) {
                        continue;
                    }
                    String cn = td.substring(1, td.length() - 1).replace('/', '.');
                    for (MethodInfo mi : d.methodsOf(typeIdx)) {
                        if (q.accept(mi.name, mi.paramCount, mi.returnDesc, mi.paramTypes,
                                mi.strings, mi.fieldTypes, mi.callerNames)) {
                            Method m = resolve(cl, cn, mi.name, mi.paramCount);
                            if (m != null) {
                                out.add(m);
                            }
                        }
                    }
                }
                if (!out.isEmpty()) {
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    static Method firstGlobal(ClassLoader cl, Query q) {
        List<Method> l = global(cl, q);
        return l.isEmpty() ? null : l.get(0);
    }

    /** 按简单类名容错查找（OEM 改包名场景） */
    static Method firstBySimpleName(ClassLoader cl, String simpleName, Query q) {
        try {
            Host host = host(cl);
            if (host == null) {
                return null;
            }
            String suffix = "/" + simpleName + ";";
            for (Dex d : host.dexes) {
                for (int i = 0; i < d.classDefsSize; i++) {
                    int off = d.classDefsOff + i * 32;
                    String td = d.typeDesc(d.u4(off));
                    if (td == null || !td.endsWith(suffix)) {
                        continue;
                    }
                    String cn = td.substring(1, td.length() - 1).replace('/', '.');
                    for (MethodInfo mi : d.methodsOf(d.u4(off))) {
                        if (q.accept(mi.name, mi.paramCount, mi.returnDesc, mi.paramTypes,
                                mi.strings, mi.fieldTypes, mi.callerNames)) {
                            Method m = resolve(cl, cn, mi.name, mi.paramCount);
                            if (m != null) {
                                return m;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static Method first(ClassLoader cl, String className, Query q) {
        Class<?> c = DexLocator.cls(cl, className);
        return c == null ? null : firstInClass(cl, c, q);
    }

    // ─────────────────────────── 反射还原 ───────────────────────────

    private static Method resolve(ClassLoader cl, String className, String name, int paramCount) {
        try {
            Class<?> clazz = cl.loadClass(className);
            return resolveIn(clazz, name, paramCount);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method resolveIn(Class<?> clazz, String name, int paramCount) {
        Method fallback = null;
        for (Method m : clazz.getDeclaredMethods()) {
            if (!m.getName().equals(name)) {
                continue;
            }
            if (m.getParameterTypes().length == paramCount) {
                m.setAccessible(true);
                return m;
            }
            if (fallback == null) {
                fallback = m;
            }
        }
        if (fallback != null) {
            fallback.setAccessible(true);
        }
        return fallback;
    }

    // ─────────────────────────── 宿主 APK 读取 ───────────────────────────

    private static final class Host {
        final List<Dex> dexes = new ArrayList<>();
    }

    private static final Map<String, Host> HOST_CACHE = new HashMap<>();

    private static Host host(ClassLoader cl) {
        String path = apkPath(cl);
        if (path == null) {
            return null;
        }
        Host cached = HOST_CACHE.get(path);
        if (cached != null) {
            return cached;
        }
        synchronized (HOST_CACHE) {
            cached = HOST_CACHE.get(path);
            if (cached != null) {
                return cached;
            }
            Host h = new Host();
            try (ZipFile z = new ZipFile(path)) {
                Enumeration<? extends ZipEntry> en = z.entries();
                List<String> names = new ArrayList<>();
                while (en.hasMoreElements()) {
                    String n = en.nextElement().getName();
                    if (n.equals("classes.dex")
                            || (n.startsWith("classes") && n.endsWith(".dex"))) {
                        names.add(n);
                    }
                }
                names.sort((a, b) -> a.length() != b.length()
                        ? a.length() - b.length() : a.compareTo(b));
                for (String n : names) {
                    ZipEntry e = z.getEntry(n);
                    if (e != null) {
                        try (InputStream in = z.getInputStream(e)) {
                            h.dexes.add(new Dex(readAll(in)));
                        }
                    }
                }
            } catch (Throwable t) {
                return h.dexes.isEmpty() ? null : h;
            }
            if (h.dexes.isEmpty()) {
                return null;
            }
            HOST_CACHE.put(path, h);
            return h;
        }
    }

    private static String apkPath(ClassLoader cl) {
        try {
            Object pathList = field(cl, "pathList");
            Object[] elements = (Object[]) field(pathList, "dexElements");
            for (Object e : elements) {
                Object p = tryField(e, "path");
                if (p instanceof File) {
                    return ((File) p).getAbsolutePath();
                }
                Object df = tryField(e, "dexFile");
                if (df != null) {
                    Object name = tryField(df, "mFileName");
                    if (name instanceof String) {
                        return (String) name;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            Object app = Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null);
            if (app instanceof android.content.Context) {
                return ((android.content.Context) app).getApplicationInfo().sourceDir;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream(1 << 20);
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) {
            bo.write(buf, 0, n);
        }
        return bo.toByteArray();
    }

    private static Object field(Object o, String name) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static Object tryField(Object o, String name) {
        try {
            return field(o, name);
        } catch (Throwable t) {
            return null;
        }
    }

    // ─────────────────────────── DEX 解析 ───────────────────────────

    private static final class MethodInfo {
        String name;
        int paramCount = -1;
        String returnDesc = "";
        final List<String> paramTypes = new ArrayList<>();
        Set<String> strings = new HashSet<>();
        Set<String> fieldTypes = new HashSet<>();
        Set<String> callerNames = new HashSet<>();
    }

    private static final class Dex {
        final byte[] b;
        final int stringIdsSize;
        final int stringIdsOff;
        final int typeIdsSize;
        final int typeIdsOff;
        final int protoIdsSize;
        final int protoIdsOff;
        final int fieldIdsOff;
        final int methodIdsSize;
        final int methodIdsOff;
        final int classDefsSize;
        final int classDefsOff;
        private final String[] strings;
        private final String[] typeDescs;
        private final int[][] protos;
        private final int[][] methodIds;

        Dex(byte[] data) {
            b = data;
            stringIdsSize = u4(0x38);
            stringIdsOff = u4(0x3C);
            typeIdsSize = u4(0x40);
            typeIdsOff = u4(0x44);
            protoIdsSize = u4(0x48);
            protoIdsOff = u4(0x4C);
            fieldIdsOff = u4(0x54);
            methodIdsSize = u4(0x58);
            methodIdsOff = u4(0x5C);
            classDefsSize = u4(0x60);
            classDefsOff = u4(0x64);
            strings = new String[stringIdsSize];
            typeDescs = new String[typeIdsSize];
            protos = new int[protoIdsSize][];
            methodIds = new int[methodIdsSize][];
        }

        int u4(int off) {
            return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8)
                    | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
        }

        int u2(int off) {
            return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
        }

        int uleb(int[] pos) {
            int p = pos[0];
            int result = b[p] & 0x7F;
            int shift = 7;
            while ((b[p] & 0x80) != 0) {
                p++;
                result |= (b[p] & 0x7F) << shift;
                shift += 7;
            }
            pos[0] = p + 1;
            return result;
        }

        String string(int idx) {
            if (idx < 0 || idx >= stringIdsSize) {
                return "";
            }
            if (strings[idx] != null) {
                return strings[idx];
            }
            int off = u4(stringIdsOff + idx * 4);
            int[] pos = {off};
            uleb(pos);
            ByteArrayOutputStream bo = new ByteArrayOutputStream(32);
            int p = pos[0];
            while (b[p] != 0) {
                bo.write(b[p]);
                p++;
            }
            String s = new String(bo.toByteArray(), StandardCharsets.UTF_8);
            strings[idx] = s;
            return s;
        }

        String typeDesc(int idx) {
            if (idx < 0 || idx >= typeIdsSize) {
                return null;
            }
            if (typeDescs[idx] != null) {
                return typeDescs[idx];
            }
            typeDescs[idx] = string(u4(typeIdsOff + idx * 4));
            return typeDescs[idx];
        }

        int findTypeIdx(String desc) {
            int lo = 0;
            int hi = typeIdsSize - 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                String d = typeDesc(mid);
                int cmp = d == null ? 1 : d.compareTo(desc);
                if (cmp == 0) {
                    return mid;
                }
                if (cmp < 0) {
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            return -1;
        }

        int[] proto(int idx) {
            if (idx < 0 || idx >= protoIdsSize) {
                return null;
            }
            if (protos[idx] != null) {
                return protos[idx];
            }
            int off = protoIdsOff + idx * 12;
            int returnIdx = u4(off + 4);
            int paramsOff = u4(off + 8);
            int paramSize = paramsOff == 0 ? 0 : u4(paramsOff);
            protos[idx] = new int[]{returnIdx, paramSize, paramsOff};
            return protos[idx];
        }

        int[] methodId(int idx) {
            if (idx < 0 || idx >= methodIdsSize) {
                return null;
            }
            if (methodIds[idx] != null) {
                return methodIds[idx];
            }
            int off = methodIdsOff + idx * 8;
            methodIds[idx] = new int[]{u2(off), u2(off + 2), u4(off + 4)};
            return methodIds[idx];
        }

        /** 类内全部带码方法，含调用方反查 */
        List<MethodInfo> methodsOf(int classTypeIdx) {
            int classDataOff = -1;
            for (int i = 0; i < classDefsSize; i++) {
                int off = classDefsOff + i * 32;
                if (u4(off) == classTypeIdx) {
                    classDataOff = u4(off + 24);
                    break;
                }
            }
            List<MethodInfo> out = new ArrayList<>();
            if (classDataOff <= 0) {
                return out;
            }
            int[] pos = {classDataOff};
            int staticFields = uleb(pos);
            int instanceFields = uleb(pos);
            int directMethods = uleb(pos);
            int virtualMethods = uleb(pos);
            for (int i = 0; i < staticFields + instanceFields; i++) {
                uleb(pos);
                uleb(pos);
            }
            int total = directMethods + virtualMethods;
            int[] methodIdxs = new int[total];
            int[] codeOffs = new int[total];
            int n = 0;
            for (int round = 0; round < 2; round++) {
                int count = round == 0 ? directMethods : virtualMethods;
                int prev = 0;
                for (int i = 0; i < count && n < total; i++) {
                    int idx = prev + uleb(pos);
                    prev = idx;
                    uleb(pos);
                    codeOffs[n] = uleb(pos);
                    methodIdxs[n] = idx;
                    n++;
                }
            }
            Map<Integer, Set<Integer>> callers = new HashMap<>();
            for (int i = 0; i < n; i++) {
                if (codeOffs[i] <= 0) {
                    continue;
                }
                for (int target : scanInvokes(codeOffs[i])) {
                    Set<Integer> s = callers.get(target);
                    if (s == null) {
                        s = new HashSet<>();
                        callers.put(target, s);
                    }
                    s.add(methodIdxs[i]);
                }
            }
            for (int i = 0; i < n; i++) {
                if (codeOffs[i] <= 0) {
                    continue;
                }
                int midx = methodIdxs[i];
                int[] mi = methodId(midx);
                MethodInfo info = new MethodInfo();
                info.name = string(mi[2]);
                int protoIdx = mi[1];
                int[] pr = proto(protoIdx);
                if (pr != null) {
                    String rt = typeDesc(pr[0]);
                    info.returnDesc = rt == null ? "" : rt;
                    info.paramCount = pr[1];
                    if (pr[2] != 0) {
                        for (int k = 0; k < pr[1]; k++) {
                            String pt = typeDesc(u2(pr[2] + 4 + k * 2));
                            info.paramTypes.add(pt == null ? "" : pt);
                        }
                    }
                }
                info.strings = scanStrings(codeOffs[i]);
                info.fieldTypes = scanFieldTypes(codeOffs[i]);
                Set<Integer> cl = callers.get(midx);
                if (cl != null) {
                    for (int cm : cl) {
                        int[] cmi = methodId(cm);
                        if (cmi != null) {
                            info.callerNames.add(string(cmi[2]));
                        }
                    }
                }
                out.add(info);
            }
            return out;
        }

        Set<String> scanStrings(int codeOff) {
            Set<String> out = new HashSet<>();
            int end = u4(codeOff + 12);
            int p = codeOff + 16;
            int stop = p + end;
            while (p < stop) {
                int unit = u2(p);
                int op = unit & 0xFF;
                if (op == 0x1a && p + 4 <= stop) {
                    out.add(string(u2(p + 2)));
                } else if (op == 0x1b && p + 6 <= stop) {
                    out.add(string(u2(p + 2) | (u2(p + 4) << 16)));
                } else if (isPayload(unit)) {
                    break;
                }
                p += sizeOf(op) * 2;
            }
            return out;
        }

        Set<Integer> scanInvokes(int codeOff) {
            Set<Integer> out = new HashSet<>();
            int end = u4(codeOff + 12);
            int p = codeOff + 16;
            int stop = p + end;
            while (p < stop) {
                int unit = u2(p);
                int op = unit & 0xFF;
                if ((op >= 0x6e && op <= 0x72) || (op >= 0x74 && op <= 0x78)) {
                    if (p + 4 <= stop) {
                        out.add(u2(p + 2));
                    }
                } else if (isPayload(unit)) {
                    break;
                }
                p += sizeOf(op) * 2;
            }
            return out;
        }

        Set<String> scanFieldTypes(int codeOff) {
            Set<String> out = new HashSet<>();
            int end = u4(codeOff + 12);
            int p = codeOff + 16;
            int stop = p + end;
            while (p < stop) {
                int unit = u2(p);
                int op = unit & 0xFF;
                if (op >= 0x52 && op <= 0x6d && p + 4 <= stop) {
                    String t = typeDesc(u2(fieldIdsOff + u2(p + 2) * 8 + 2));
                    if (t != null) {
                        out.add(t);
                    }
                } else if (isPayload(unit)) {
                    break;
                }
                p += sizeOf(op) * 2;
            }
            return out;
        }

        private static boolean isPayload(int unit) {
            int op = unit & 0xFF;
            return op == 0x00 && (unit == 0x0100 || unit == 0x0200 || unit == 0x0300);
        }

        /** dalvik 指令长度（码元），线性扫描对齐用 */
        static int sizeOf(int op) {
            if (op == 0x00 || op == 0x01 || op == 0x04 || op == 0x07 || op == 0x0a
                    || op == 0x0b || op == 0x0c || op == 0x0d || op == 0x0e
                    || (op >= 0x10 && op <= 0x12) || op == 0x1d || op == 0x1e
                    || op == 0x21 || op == 0x27 || op == 0x28 || (op >= 0x73 && op <= 0x79)
                    || op == 0x7a || (op >= 0xb0 && op <= 0xcf)) {
                return 1;
            }
            if (op == 0x02 || op == 0x05 || op == 0x08 || op == 0x13 || op == 0x15
                    || op == 0x16 || op == 0x19 || op == 0x1a || op == 0x1c || op == 0x1f
                    || op == 0x20 || op == 0x22 || op == 0x23 || op == 0x29
                    || (op >= 0x2d && op <= 0x37) || (op >= 0x38 && op <= 0x3d)
                    || (op >= 0x44 && op <= 0x5f) || (op >= 0x60 && op <= 0x6d)
                    || (op >= 0x90 && op <= 0xaf) || (op >= 0xd0 && op <= 0xe2)
                    || op == 0xfe || op == 0xff) {
                return 2;
            }
            if (op == 0x03 || op == 0x06 || op == 0x09 || op == 0x14 || op == 0x17
                    || op == 0x1b || op == 0x24 || op == 0x25 || op == 0x26 || op == 0x2a
                    || (op >= 0x2b && op <= 0x2c) || (op >= 0x6e && op <= 0x72)
                    || (op >= 0x74 && op <= 0x78) || op == 0xfc || op == 0xfd) {
                return 3;
            }
            if (op == 0x18) {
                return 5;
            }
            if (op == 0xfa || op == 0xfb) {
                return 4;
            }
            return 1;
        }
    }

    private DexIndex() {
    }
}
