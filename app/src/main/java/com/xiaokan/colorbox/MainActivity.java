package com.xiaokan.colorbox;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 模块设置页（套用 OShin 应用界面结构：顶栏 + 三页底部导航 + 卡片分区）。
 *
 * 配置直接写入明文键：prefs "cfg" 存开关与参数，prefs "settings" 存
 * hook_master 与 hook_<pkg> 作用域停用；经 HookStateProvider 下发给 hook 侧。
 */
public class MainActivity extends Activity {

    private static final String[] TABS = {"状态", "功能", "工具"};
    private static final int[] TAB_ICONS = {
            R.drawable.ic_tab_status, R.drawable.ic_tab_core, R.drawable.ic_tab_system};

    private static final int[] GROUP_COLORS = {
            0xFF34C3FF, 0xFF7C5CFF, 0xFF00B8A9, 0xFF3D8BFF, 0xFFFF5C7A,
            0xFF3D8BFF, 0xFF9F6CFF, 0xFFFF9F43, 0xFF8E9BFF, 0xFFFF9F43};
    private static final int[] GROUP_ICONS = {
            R.drawable.ic_sec_folder, R.drawable.ic_sec_shield, R.drawable.ic_sec_screenshot,
            R.drawable.ic_sec_eye, R.drawable.ic_sec_sliders, R.drawable.ic_sec_layout,
            R.drawable.ic_sec_master, R.drawable.ic_sec_lock, R.drawable.ic_sec_eye,
            R.drawable.ic_sec_badge};
    private static final String[] GROUP_DESCS = {
            "文件夹 · 布局 · 角标", "安装校验与签名绕过", "隐私页与延迟限制",
            "受限设置与护眼", "选择器接管与重定向", "多窗口限制解除",
            "侧边栏能力扩展", "SafeProvider 状态伪装", "全屏翻译", "VIP 与广告清理"};

    private static final int ACCENT_FALLBACK = 0xFF5B6CFF;

    private int pageBg, ink, sub, line, accent, cardBg, fieldBg, idle, navBg, ripple;
    private int heroA, heroB;
    private boolean dark;

    private FrameLayout pageHost;
    private LinearLayout bar;
    private View indicator;
    private LinearLayout[] tabItems;
    private ImageView[] tabIcons;
    private TextView[] tabLabels;
    private int curTab = -1;

    private SharedPreferences cfg;
    private SharedPreferences hookPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int mask = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        dark = mask == Configuration.UI_MODE_NIGHT_YES;
        theme();

        cfg = getSharedPreferences("cfg", MODE_PRIVATE);
        hookPrefs = getSharedPreferences("settings", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(pageBg);
        root.addView(topBar(), rowOf(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pageHost = new FrameLayout(this);
        scroll.addView(pageHost, new ScrollView.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, rowOf(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(bottomNav(), rowOf(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        getWindow().setStatusBarColor(pageBg);
        getWindow().setNavigationBarColor(navBg);
        if (Build.VERSION.SDK_INT >= 23 && !dark) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        bar.post(() -> select(0, false));
    }

    // ─────────────────────────── 主题 ───────────────────────────

    private void theme() {
        pageBg = dark ? 0xFF101014 : 0xFFF3F4F8;
        accent = dark ? 0xFF8E9BFF : 0xFF5B6CFF;
        ink = dark ? 0xFFF2F2F4 : 0xFF191A20;
        sub = dark ? 0xFF96979F : 0xFF84858E;
        line = (dark ? 0x1F : 0x12) << 24;
        cardBg = dark ? 0xFF1A1A20 : 0xFFFFFFFF;
        fieldBg = dark ? 0xFF232329 : 0xFFF1F2F6;
        idle = dark ? 0xFF787980 : 0xFFB0B1BA;
        navBg = (dark ? 0xF5 : 0xFA) << 24 | (dark ? 0x17171C : 0xFFFFFF);
        ripple = (dark ? 0x30 : 0x1E) << 24 | (accent & 0xFFFFFF);
        heroA = accent;
        heroB = dark ? 0xFF9C5CFF : 0xFF9C5CFF;
    }

    // ─────────────────────────── 顶栏与导航 ───────────────────────────

    private View topBar() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(20), dp(14), dp(20), dp(6));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("ColorBox");
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(ink);
        col.addView(title, wrap());
        TextView subT = new TextView(this);
        subT.setText("ColorOS 工具箱 · v" + versionName());
        subT.setTextSize(11);
        subT.setTextColor(sub);
        col.addView(subT, wrap(0, dp(2), 0, 0));
        top.addView(col, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView chip = new TextView(this);
        chip.setText("LSPosed");
        chip.setTextSize(10);
        chip.setTypeface(Typeface.DEFAULT_BOLD);
        chip.setTextColor(accent);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor((accent & 0xFFFFFF) | 0x1C000000);
        chip.setBackground(g);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        top.addView(chip, wrap());
        return top;
    }

    private View bottomNav() {
        FrameLayout host = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadii(new float[]{dp(22), dp(22), dp(22), dp(22), 0, 0, 0, 0});
        bg.setColor(navBg);
        host.setBackground(bg);
        host.setElevation(dp(14));

        indicator = new View(this);
        GradientDrawable ig = new GradientDrawable();
        ig.setCornerRadius(dp(2));
        ig.setColor(accent);
        indicator.setBackground(ig);
        FrameLayout.LayoutParams ip =
                new FrameLayout.LayoutParams(dp(18), dp(3), Gravity.TOP);
        ip.topMargin = dp(2);
        host.addView(indicator, ip);

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(8), dp(8), dp(8), dp(10));
        host.addView(bar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        tabItems = new LinearLayout[TABS.length];
        tabIcons = new ImageView[TABS.length];
        tabLabels = new TextView[TABS.length];
        for (int i = 0; i < TABS.length; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setBackground(ripple(dp(18)));
            final int idx = i;
            item.setOnClickListener(v -> select(idx, true));

            FrameLayout iconWrap = new FrameLayout(this);
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setCornerRadius(dp(14));
            chipBg.setColor(0x00000000);
            iconWrap.setBackground(chipBg);
            ImageView icon = new ImageView(this);
            icon.setImageResource(TAB_ICONS[i]);
            iconWrap.addView(icon, new FrameLayout.LayoutParams(
                    dp(20), dp(20), Gravity.CENTER));
            LinearLayout.LayoutParams iwp = new LinearLayout.LayoutParams(dp(40), dp(32));
            iwp.topMargin = dp(3);
            item.addView(iconWrap, iwp);

            TextView label = new TextView(this);
            label.setText(TABS[i]);
            label.setTextSize(10);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.CENTER);
            item.addView(label, wrap(0, dp(3), 0, 0));

            bar.addView(item, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f));
            tabItems[i] = item;
            tabIcons[i] = icon;
            tabLabels[i] = label;
        }
        return host;
    }

    private void select(int idx, boolean animate) {
        boolean first = curTab == -1;
        curTab = idx;
        for (int i = 0; i < TABS.length; i++) {
            boolean on = i == idx;
            tabIcons[i].setColorFilter(on ? accent : idle);
            tabLabels[i].setTextColor(on ? accent : idle);
            View chipWrap = (View) tabIcons[i].getParent();
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(14));
            g.setColor(on ? (accent & 0xFFFFFF) | 0x22000000 : 0x00000000);
            chipWrap.setBackground(g);
        }
        bar.post(() -> {
            if (bar.getWidth() <= 0) {
                return;
            }
            View item = tabItems[idx];
            float target = bar.getX() + item.getX() + item.getWidth() / 2f - dp(9);
            if (animate && !first) {
                ValueAnimator va = ValueAnimator.ofFloat(indicator.getX(), target);
                va.addUpdateListener(a -> indicator.setX((Float) a.getAnimatedValue()));
                va.setInterpolator(new DecelerateInterpolator());
                va.setDuration(280);
                va.start();
            } else {
                indicator.setX(target);
            }
        });
        swapPage(idx, animate && !first);
    }

    private void swapPage(int idx, boolean animate) {
        LinearLayout pageCol = new LinearLayout(this);
        pageCol.setOrientation(LinearLayout.VERTICAL);
        pageCol.setPadding(dp(16), dp(8), dp(16), dp(20));
        if (idx == 0) {
            buildStatusPage(pageCol);
        } else if (idx == 1) {
            buildFeaturesPage(pageCol);
        } else {
            buildToolsPage(pageCol);
        }
        pageHost.removeAllViews();
        pageHost.addView(pageCol, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        if (animate) {
            pageCol.setAlpha(0f);
            pageCol.setTranslationY(dp(12));
            pageCol.animate().alpha(1f).translationY(0f).setDuration(220)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    // ─────────────────────────── 状态页 ───────────────────────────

    private void buildStatusPage(LinearLayout col) {
        col.addView(heroCard(), rowOf(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, dp(14)));
        col.addView(scopeCard(), rowOf(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, dp(14)));

        LinearLayout steps = card();
        steps.addView(head(accent, R.drawable.ic_tab_status, "使用步骤", "三步开始使用"));
        addRows(steps, tip("1. 在 LSPosed 中启用本模块并勾选作用域\n"
                + "2. 打开各分区中的功能开关\n"
                + "3. 标注「重启生效」的开关需重启对应应用或手机"));
        col.addView(steps, rowOf(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, dp(8)));
    }

    private View heroCard() {
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{heroA, heroB});
        g.setCornerRadius(dp(22));
        hero.setBackground(g);
        hero.setPadding(dp(20), dp(18), dp(20), dp(18));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText("模块总开关");
        t.setTextSize(17);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(0xFFFFFFFF);
        col.addView(t, wrap());
        TextView s = new TextView(this);
        s.setText("关闭后所有作用域的 hook 直接放行");
        s.setTextSize(11);
        s.setTextColor(0xB8FFFFFF);
        col.addView(s, wrap(0, dp(2), 0, 0));
        row.addView(col, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch master = new Switch(this);
        master.setChecked(hookPrefs.getBoolean(FeatureKeys.MASTER, true));
        tintWhite(master, master.isChecked());
        master.setOnCheckedChangeListener((b, v) -> {
            hookPrefs.edit().putBoolean(FeatureKeys.MASTER, v).apply();
            tintWhite(master, v);
        });
        row.addView(master, wrap());
        hero.addView(row, match());

        LinearLayout stat = new LinearLayout(this);
        stat.setOrientation(LinearLayout.HORIZONTAL);
        stat.setGravity(Gravity.CENTER_VERTICAL);
        ImageView dot = new ImageView(this);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(0xFF7DFFB2);
        dot.setBackground(dg);
        stat.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));
        TextView cap = new TextView(this);
        cap.setText("  配置通道实时生效 · 已启用 "
                + enabledCount() + " / " + FeatureKeys.SWITCHES.length + " 项功能");
        cap.setTextSize(11);
        cap.setTextColor(0xB8FFFFFF);
        stat.addView(cap, wrap());
        hero.addView(stat, match(0, dp(14), 0, 0));
        return hero;
    }

    private int enabledCount() {
        int n = 0;
        for (String[] s : FeatureKeys.SWITCHES) {
            if (cfg.getBoolean(s[0], false)) {
                n++;
            }
        }
        return n;
    }

    private View scopeCard() {
        LinearLayout card = card();
        card.addView(head(0xFFFF9F43, R.drawable.ic_sec_shield,
                "作用域应用", "在 LSPosed 勾选后可在此单独停用"));
        java.util.List<View> rows = new java.util.ArrayList<>();
        String[] scopes = getResources().getStringArray(R.array.xposed_scope);
        for (String pkg : scopes) {
            if ("android".equals(pkg)) {
                rows.add(staticRow("系统框架（android）", "始终启用"));
            } else {
                rows.add(appToggleRow(pkg));
            }
        }
        addRows(card, rows.toArray(new View[0]));
        return card;
    }

    // ─────────────────────────── 功能页 ───────────────────────────

    private void buildFeaturesPage(LinearLayout col) {
        String[] groups = FeatureKeys.GROUP_ORDER;
        for (int g = 0; g < groups.length; g++) {
            LinearLayout card = card();
            int color = GROUP_COLORS[g % GROUP_COLORS.length];
            card.addView(head(color, GROUP_ICONS[g % GROUP_ICONS.length],
                    groups[g], GROUP_DESCS[g % GROUP_DESCS.length]));
            java.util.List<View> rows = new java.util.ArrayList<>();
            for (String key : FeatureKeys.groupSwitches(groups[g])) {
                rows.add(toggleRow(color, key));
                if (FeatureKeys.LAYOUT_CUSTOM.equals(key) && cfg.getBoolean(key, false)) {
                    rows.add(stepperRow("行数", FeatureKeys.P_LAYOUT_ROWS, 6, 10, color));
                    rows.add(stepperRow("列数", FeatureKeys.P_LAYOUT_COLS, 4, 7, color));
                }
            }
            addRows(card, rows.toArray(new View[0]));
            int bottom = g == groups.length - 1 ? dp(8) : dp(14);
            col.addView(card, rowOf(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, bottom));
        }
    }

    // ─────────────────────────── 工具页 ───────────────────────────

    private void buildToolsPage(LinearLayout col) {
        LinearLayout params = card();
        params.addView(head(0xFFFF9F43, R.drawable.ic_sec_sliders,
                "参数设置", "整型与文本参数（默认 表示跟随系统）"));
        java.util.List<View> rows = new java.util.ArrayList<>();
        for (String key : FeatureKeys.INT_PARAMS) {
            if (FeatureKeys.P_LAYOUT_ROWS.equals(key)
                    || FeatureKeys.P_LAYOUT_COLS.equals(key)) {
                continue;
            }
            int min;
            int max;
            if (FeatureKeys.P_MAX_SMALL_WINDOWS.equals(key)) {
                min = -1;
                max = 9;
            } else if (FeatureKeys.P_WIN_CORNER.equals(key)) {
                min = -1;
                max = 60;
            } else {
                min = -1;
                max = 30;
            }
            rows.add(stepperRow(paramLabel(key), key, min, max, 0xFFFF9F43));
        }
        for (String key : FeatureKeys.STR_PARAMS) {
            rows.add(textRow(paramLabel(key), key));
        }
        addRows(params, rows.toArray(new View[0]));
        col.addView(params, rowOf(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, dp(14)));

        LinearLayout about = card();
        about.addView(head(0xFF3D8BFF, R.drawable.ic_sec_master,
                "关于模块", "运行信息与快捷入口"));
        addRows(about,
                staticRow("当前版本", "v" + versionName()
                        + " · 已启用 " + enabledCount() + " / "
                        + FeatureKeys.SWITCHES.length + " 项功能"),
                staticRow("配置通道", "HookStateProvider 实时下发，改动约 2 秒内生效"),
                linkRow("打开开发者选项", () -> startActivity(new Intent(
                        android.provider.Settings
                                .ACTION_APPLICATION_DEVELOPMENT_SETTINGS))),
                linkRow("打开 LSPosed 管理器", () -> {
                    Intent it = getPackageManager()
                            .getLaunchIntentForPackage("org.lsposed.manager");
                    if (it != null) {
                        startActivity(it);
                    } else {
                        Toast.makeText(this, "未检测到 LSPosed",
                                Toast.LENGTH_SHORT).show();
                    }
                }));
        col.addView(about, rowOf(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, dp(8)));
    }

    private static String paramLabel(String key) {
        switch (key) {
            case FeatureKeys.P_MAX_SMALL_WINDOWS:
                return "小窗同时个数上限";
            case FeatureKeys.P_WIN_CORNER:
                return "小窗圆角半径（px）";
            case FeatureKeys.P_WIN_FOCUS_SHADOW:
                return "小窗聚焦阴影";
            case FeatureKeys.P_WIN_UNFOCUS_SHADOW:
                return "小窗失焦阴影";
            case FeatureKeys.P_TILE_SCRIPT:
                return "快捷磁贴脚本命令";
            case FeatureKeys.P_FILE_MANAGER_PKG:
                return "自定义文件管理器包名";
            case FeatureKeys.P_INSTALLER_TARGETS:
                return "安装重定向目标包名（逗号分隔）";
            case FeatureKeys.P_UNINSTALLER_TARGETS:
                return "卸载重定向目标包名（逗号分隔）";
            default:
                return key;
        }
    }

    // ─────────────────────────── 行构建 ───────────────────────────

    private View head(int color, int iconRes, String title, String desc) {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(10), dp(12), dp(10), dp(10));

        FrameLayout badge = new FrameLayout(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor((color & 0xFFFFFF) | 0x26000000);
        badge.setBackground(g);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(color);
        badge.addView(icon, new FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER));
        head.addView(badge, new LinearLayout.LayoutParams(dp(30), dp(30)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(ink);
        col.addView(t, wrap(dp(10), 0, 0, 0));
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTextSize(10);
        d.setTextColor(sub);
        col.addView(d, wrap(dp(10), dp(1), 0, 0));
        head.addView(col, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return head;
    }

    private View toggleRow(int color, final String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(50));
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(ripple(dp(14)));

        TextView t = new TextView(this);
        t.setText(FeatureKeys.title(key));
        t.setTextSize(14);
        t.setTextColor(ink);
        row.addView(t, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new Switch(this);
        sw.setChecked(cfg.getBoolean(key, false));
        tint(sw, color, sw.isChecked());
        sw.setOnCheckedChangeListener((b, v) -> {
            cfg.edit().putBoolean(key, v).apply();
            tint(sw, color, v);
            if (FeatureKeys.LAYOUT_CUSTOM.equals(key)) {
                select(curTab, false);
            }
        });
        row.addView(sw, wrap());
        return row;
    }

    private View stepperRow(String label, final String key, int min, int max, int color) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(fieldBg);
        row.setBackground(g);

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(12);
        t.setTextColor(ink);
        row.addView(t, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final TextView val = new TextView(this);
        val.setText(intLabel(cfg.getInt(key, (Integer) FeatureKeys.defaultValue(key))));
        val.setTextSize(14);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        val.setTextColor(color);
        val.setMinWidth(dp(26));
        val.setGravity(Gravity.CENTER);

        row.addView(stepBtn("−", color, () -> {
            int v = cfg.getInt(key, (Integer) FeatureKeys.defaultValue(key));
            if (v > min) {
                cfg.edit().putInt(key, v - 1).apply();
                val.setText(intLabel(v - 1));
            }
        }), new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout.LayoutParams vp = wrap();
        vp.setMargins(dp(10), 0, dp(10), 0);
        row.addView(val, vp);
        row.addView(stepBtn("+", color, () -> {
            int v = cfg.getInt(key, (Integer) FeatureKeys.defaultValue(key));
            if (v < max) {
                cfg.edit().putInt(key, v + 1).apply();
                val.setText(intLabel(v + 1));
            }
        }), new LinearLayout.LayoutParams(dp(34), dp(34)));
        return row;
    }

    private String intLabel(int v) {
        return v < 0 ? "默认" : String.valueOf(v);
    }

    private View textRow(String label, final String key) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(50));
        row.setPadding(dp(12), dp(6), dp(12), dp(6));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(12));
        g.setColor(fieldBg);
        row.setBackground(g);

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(12);
        t.setTextColor(ink);
        row.addView(t, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        String cur = cfg.getString(key, (String) FeatureKeys.defaultValue(key));
        TextView state = new TextView(this);
        boolean has = cur != null && !cur.isEmpty();
        state.setText(has ? "已设置" : "未设置");
        state.setTextSize(11);
        state.setTypeface(Typeface.DEFAULT_BOLD);
        state.setTextColor(has ? 0xFFFF9F43 : sub);
        row.addView(state, wrap());

        row.setOnClickListener(v -> {
            final EditText input = new EditText(this);
            input.setText(cur);
            input.setSelection(input.getText() == null ? 0 : input.getText().length());
            input.setSingleLine(FeatureKeys.P_TILE_SCRIPT.equals(key));
            new AlertDialog.Builder(this)
                    .setTitle(label)
                    .setView(input)
                    .setPositiveButton("保存", (d, w) -> {
                        String s = input.getText() == null
                                ? "" : input.getText().toString().trim();
                        cfg.edit().putString(key, s).apply();
                        state.setText(s.isEmpty() ? "未设置" : "已设置");
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
        return row;
    }

    private TextView stepBtn(String text, int color, Runnable onClick) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(color);
        b.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(cardBg);
        b.setBackground(g);
        b.setOnClickListener(v -> onClick.run());
        return b;
    }

    private View appToggleRow(final String pkg) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(50));
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(ripple(dp(14)));

        ImageView icon = new ImageView(this);
        try {
            icon.setImageDrawable(getPackageManager().getApplicationIcon(pkg));
        } catch (Throwable ignored) {
        }
        row.addView(icon, new LinearLayout.LayoutParams(dp(30), dp(30)));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(appLabel(pkg));
        t.setTextSize(13);
        t.setTextColor(ink);
        t.setMaxLines(1);
        textCol.addView(t, wrap());
        TextView p = new TextView(this);
        p.setText(pkg);
        p.setTextSize(9);
        p.setTextColor(sub);
        p.setMaxLines(1);
        textCol.addView(p, wrap(0, dp(1), 0, 0));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = dp(10);
        row.addView(textCol, tp);

        Switch sw = new Switch(this);
        final String prefKey = "hook_" + pkg;
        sw.setChecked(hookPrefs.getBoolean(prefKey, true));
        tint(sw, accent, sw.isChecked());
        sw.setOnCheckedChangeListener((b, v) -> {
            hookPrefs.edit().putBoolean(prefKey, v).apply();
            tint(sw, accent, v);
        });
        row.addView(sw, wrap());
        return row;
    }

    private View staticRow(String title, String subText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(50));
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(13);
        t.setTextColor(ink);
        textCol.addView(t, wrap());
        TextView s = new TextView(this);
        s.setText(subText);
        s.setTextSize(10);
        s.setTextColor(sub);
        textCol.addView(s, wrap(0, dp(1), 0, 0));
        row.addView(textCol, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    private View linkRow(String title, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(ripple(dp(14)));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(14);
        t.setTextColor(ink);
        row.addView(t, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView chev = new TextView(this);
        chev.setText("›");
        chev.setTextSize(16);
        chev.setTextColor(sub);
        row.addView(chev, wrap());
        row.setOnClickListener(v -> onClick.run());
        return row;
    }

    private TextView tip(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setLineSpacing(dp(2), 1f);
        t.setTextColor(sub);
        t.setPadding(dp(10), dp(10), dp(10), dp(12));
        return t;
    }

    private void addRows(LinearLayout card, View... rows) {
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                View ln = new View(this);
                ln.setBackgroundColor(line);
                LinearLayout.LayoutParams lp = rowOf(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
                lp.setMargins(dp(12), 0, dp(12), 0);
                card.addView(ln, lp);
            }
            card.addView(rows[i], match());
        }
    }

    // ─────────────────────────── 容器与绘制 ───────────────────────────

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(cardBg);
        g.setStroke(dp(1), line);
        c.setBackground(g);
        c.setElevation(dark ? 0 : dp(1));
        c.setPadding(dp(2), dp(2), dp(2), dp(4));
        return c;
    }

    private RippleDrawable ripple(int radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(radiusDp));
        mask.setColor(0xFFFFFFFF);
        return new RippleDrawable(ColorStateList.valueOf(ripple), null, mask);
    }

    private void tint(Switch sw, int color, boolean on) {
        if (sw.getThumbDrawable() != null) {
            sw.getThumbDrawable().setTintList(ColorStateList.valueOf(
                    on ? color : 0xFFD4D4DC));
        }
        if (sw.getTrackDrawable() != null) {
            sw.getTrackDrawable().setTintList(ColorStateList.valueOf(
                    (on ? 0x4D : 0x26) << 24 | (color & 0xFFFFFF)));
        }
    }

    private void tintWhite(Switch sw, boolean on) {
        if (sw.getThumbDrawable() != null) {
            sw.getThumbDrawable().setTintList(ColorStateList.valueOf(
                    on ? 0xFFFFFFFF : 0x99FFFFFF));
        }
        if (sw.getTrackDrawable() != null) {
            sw.getTrackDrawable().setTintList(ColorStateList.valueOf(
                    on ? 0x66FFFFFF : 0x33FFFFFF));
        }
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo info = pm.getPackageInfo(pkg, 0);
            CharSequence name = pm.getApplicationLabel(info.applicationInfo);
            if (name != null) {
                return name.toString();
            }
        } catch (Throwable ignored) {
        }
        return pkg;
    }

    private String versionName() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName;
        } catch (Throwable t) {
            return "?";
        }
    }

    // ─────────────────────────── 布局参数 ───────────────────────────

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrap(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = wrap();
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private LinearLayout.LayoutParams match() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams match(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = match();
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private LinearLayout.LayoutParams rowOf(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private LinearLayout.LayoutParams rowOf(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private LinearLayout.LayoutParams rowOf(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
