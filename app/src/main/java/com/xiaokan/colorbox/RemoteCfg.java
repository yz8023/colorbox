package com.xiaokan.colorbox;

/**
 * Cfg.Remote 的扁平淡出实现：把偏好存储内容映射为 b_/i_/s_ 前缀 Bundle。
 * 双入口各自提供 Reader 适配（SharedPreferences / XSharedPreferences）。
 */
final class RemoteCfg {

    interface Reader {
        boolean contains(String key);

        boolean getBoolean(String key, boolean def);

        int getInt(String key, int def);

        String getString(String key, String def);
    }

    private RemoteCfg() {
    }

    /** key=cfgall 时返回全量扁平化结果；无任何数据返回 null */
    static android.os.Bundle flatten(Reader r, String key) {
        if (!"cfgall".equals(key)) {
            return null;
        }
        try {
            android.os.Bundle b = new android.os.Bundle();
            boolean any = false;
            for (String[] s : FeatureKeys.SWITCHES) {
                if (r.contains(s[0])) {
                    b.putBoolean("b_" + s[0],
                            r.getBoolean(s[0], (Boolean) FeatureKeys.defaultValue(s[0])));
                    any = true;
                }
            }
            for (String ik : FeatureKeys.INT_PARAMS) {
                if (r.contains(ik)) {
                    b.putInt("i_" + ik,
                            r.getInt(ik, (Integer) FeatureKeys.defaultValue(ik)));
                    any = true;
                }
            }
            for (String sk : FeatureKeys.STR_PARAMS) {
                if (r.contains(sk)) {
                    b.putString("s_" + sk,
                            r.getString(sk, (String) FeatureKeys.defaultValue(sk)));
                    any = true;
                }
            }
            return any ? b : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
