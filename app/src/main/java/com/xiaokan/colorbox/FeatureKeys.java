package com.xiaokan.colorbox;

import java.util.HashMap;
import java.util.Map;

/**
 * 功能注册表：ColorBox 全部开关、参数、默认值与分组。
 * 设置 UI、HookStateProvider、hook 侧 Cfg 三方共用这一份数据。
 *
 * 开关键沿用各来源项目的偏好键名（OShin / LuckyTool / CorePatch），
 * 便于用户在来源项目间迁移配置习惯。
 */
public final class FeatureKeys {

    // ── 分组名 ──
    public static final String G_LAUNCHER = "桌面";
    public static final String G_CORE = "核心破解";
    public static final String G_CAPTURE = "截屏";
    public static final String G_SYSTEM = "系统";
    public static final String G_SERVICE = "系统服务";
    public static final String G_WINDOW = "分屏与小窗";
    public static final String G_SIDEBAR = "智慧侧边栏";
    public static final String G_SAFECENTER = "安全中心";
    public static final String G_SCAN = "小布扫一扫";
    public static final String G_THEME = "主题商店";

    /** 分组展示顺序（UI 用） */
    public static final String[] GROUP_ORDER = {
            G_LAUNCHER, G_CORE, G_CAPTURE, G_SYSTEM, G_SERVICE,
            G_WINDOW, G_SIDEBAR, G_SAFECENTER, G_SCAN, G_THEME};

    // ── 桌面 ──
    public static final String FOLDER_BG = "folder_preview_bg_remove";
    public static final String LAYOUT_CUSTOM = "launcher_layout_custom";
    public static final String BADGE_SHORTCUT = "remove_app_shortcut_badge";
    public static final String BADGE_WORK = "remove_app_work_badge";
    public static final String BADGE_CLONE = "remove_app_clone_badge";
    public static final String UNINSTALL_BLACKLIST = "remove_app_uninstall_blacklist";

    // ── 核心破解（安装限制 / 安装器增强） ──
    public static final String ENABLE_32BIT = "force_enable_32_bit_support";
    public static final String REMOVE_ADB_CONFIRM = "remove_adb_install_confirm";
    public static final String ALLOW_DOWNGRADE = "allow_downgrade_install";
    public static final String DISABLE_VERIFY = "disable_signature_verify";
    public static final String FIX_INSTALL_BUTTON = "fix_install_button_display";
    public static final String SKIP_APK_SCAN = "skip_apk_scan";
    public static final String DISABLE_APPDETAIL = "disable_start_appdetail";
    public static final String AUTO_INSTALL = "auto_click_install_button";
    public static final String AUTO_UNINSTALL = "auto_click_uninstall_button";
    public static final String REMOVE_INSTALL_ADS = "remove_install_ads";
    public static final String SHOW_APK_INFO = "show_more_apk_info";
    public static final String ALLOW_DISABLE_SYSAPPS = "allow_disabling_system_apps";
    public static final String DISABLE_INSTALL_VERIFICATION = "disable_install_verification";
    public static final String DISABLE_JAR_VERIFIER = "disable_jar_verifier";
    public static final String DISABLE_MESSAGE_DIGEST = "disable_message_digest";
    public static final String BYPASS_ARSC_CHECK = "bypass_arsc_uncompressed_check";
    public static final String BYPASS_MIN_SIG_VERSION = "bypass_min_signature_version_check";
    public static final String BYPASS_V1_SIG_ERRORS = "bypass_v1_signature_errors";
    public static final String ALLOW_SIG_MISMATCH_UPDATE = "allow_signature_mismatch_on_update";
    public static final String ALLOW_SPLIT_SIG_MISMATCH = "allow_mismatched_split_apk_signatures";
    public static final String ALLOW_NONSYSTEM_SHARED_UID = "allow_nonsystem_shared_uid";
    public static final String ALLOW_HIDDEN_API = "allow_system_app_hidden_api";
    public static final String PMS_COMMAND = "enable_pms_command";

    // ── 截屏（常驻解除之外的开关） ──
    public static final String CAPTURE_PRIVACY = "remove_screenshot_privacy_limit";
    public static final String CAPTURE_NO_DELAY = "remove_system_screenshot_delay";

    // ── 系统 ──
    public static final String UNLOCK_RESTRICTED = "auto_unlock_restricted_settings";
    public static final String EYE_TEXTURE = "enable_eyeprotect_paper_texture_support";

    // ── 系统服务 ──
    public static final String DISABLE_PIN_72H = "disable_pin_verify_per_72_hours";
    public static final String ALLOW_UNTRUSTED_TOUCH = "allow_untrusted_touch";
    public static final String IGNORE_AUDIO_FOCUS = "ignore_audio_focus";
    public static final String DISABLE_ROOT_CHECK = "disable_root_check";
    public static final String CUSTOM_FILE_MANAGER = "enable_custom_file_manager";
    public static final String FORBID_PICKER = "forbid_file_and_calendar";
    public static final String INSTALLER_REDIRECT = "enable_installer_redirect";
    public static final String UNINSTALLER_REDIRECT = "enable_uninstaller_redirect";

    // ── 分屏与小窗 ──
    public static final String SMALL_WIN_UNRESTRICT = "remove_all_small_window_restrictions";
    public static final String FORCE_MULTI_WINDOW = "force_multi_window_mode";

    // ── 智慧侧边栏 ──
    public static final String SIDEBAR_ADD_LIMIT = "remove_app_add_limit";

    // ── 安全中心 ──
    public static final String CLEAR_RISK_APPS = "clear_risk_apps";
    public static final String CLEAR_STATUS_ALERTS = "clear_status_alerts";
    public static final String FAKE_SCAN_RESULT = "fake_scan_result";
    public static final String INSTALLER_WHITELIST = "force_packageinstaller_whitelist";
    public static final String VIRUS_WHITELIST = "force_virus_whitelist";

    // ── 小布扫一扫 ──
    public static final String FULL_SCREEN_TRANSLATION = "full_screen_translation";

    // ── 主题商店 ──
    public static final String THEME_VIP = "unlock_themestore_vip_features";
    public static final String THEME_SPLASH_ADS = "remove_themestore_splash_ads";
    public static final String THEME_UPGRADE = "remove_themestore_upgrade";

    // ── 参数键（非开关） ──
    public static final String P_LAYOUT_ROWS = "launcher_layout_max_rows";
    public static final String P_LAYOUT_COLS = "launcher_layout_max_columns";
    public static final String P_MAX_SMALL_WINDOWS = "max_simultaneous_small_windows";
    public static final String P_WIN_CORNER = "small_window_corner_radius";
    public static final String P_WIN_FOCUS_SHADOW = "small_window_focused_shadow";
    public static final String P_WIN_UNFOCUS_SHADOW = "small_window_unfocused_shadow";
    public static final String P_TILE_SCRIPT = "tile_script_command";
    public static final String P_FILE_MANAGER_PKG = "custom_file_manager_package";
    public static final String P_INSTALLER_TARGETS = "installer_target_packages";
    public static final String P_UNINSTALLER_TARGETS = "uninstaller_target_packages";

    /** 总门控：关闭后所有作用域直接放行 */
    public static final String MASTER = "hook_master";

    /** 开关行：{ 键, 展示标题, 分组 } */
    public static final String[][] SWITCHES = {
            // 桌面
            {FOLDER_BG, "移除文件夹预览背景", G_LAUNCHER},
            {LAYOUT_CUSTOM, "桌面布局行列数自定义", G_LAUNCHER},
            {BADGE_SHORTCUT, "移除快捷方式徽标", G_LAUNCHER},
            {BADGE_WORK, "移除工作空间徽标", G_LAUNCHER},
            {BADGE_CLONE, "移除应用分身徽标", G_LAUNCHER},
            {UNINSTALL_BLACKLIST, "移除应用卸载黑名单（需重启）", G_LAUNCHER},
            // 核心破解
            {ENABLE_32BIT, "强制启用 32 位支持（需重启）", G_CORE},
            {REMOVE_ADB_CONFIRM, "移除 ADB 安装确认", G_CORE},
            {ALLOW_DOWNGRADE, "允许降级安装", G_CORE},
            {DISABLE_VERIFY, "跳过签名/病毒验证（需重启）", G_CORE},
            {FIX_INSTALL_BUTTON, "修复安装按钮显示异常", G_CORE},
            {SKIP_APK_SCAN, "跳过 APK 安全扫描", G_CORE},
            {DISABLE_APPDETAIL, "禁止启动 AppDetail 定制扫描", G_CORE},
            {AUTO_INSTALL, "自动点击安装按钮", G_CORE},
            {AUTO_UNINSTALL, "自动点击卸载按钮", G_CORE},
            {REMOVE_INSTALL_ADS, "移除安装完成广告", G_CORE},
            {SHOW_APK_INFO, "安装页顶部显示包信息", G_CORE},
            {ALLOW_DISABLE_SYSAPPS, "允许卸载/停用系统应用", G_CORE},
            {DISABLE_INSTALL_VERIFICATION, "禁用安装包验证（需重启）", G_CORE},
            {DISABLE_JAR_VERIFIER, "禁用 JAR 签名校验（需重启）", G_CORE},
            {DISABLE_MESSAGE_DIGEST, "禁用消息摘要比对（需重启）", G_CORE},
            {BYPASS_ARSC_CHECK, "绕过 resources.arsc 压缩检查", G_CORE},
            {BYPASS_MIN_SIG_VERSION, "绕过最低签名方案版本检查", G_CORE},
            {BYPASS_V1_SIG_ERRORS, "忽略 V1 签名错误（CorePatch 方式）", G_CORE},
            {ALLOW_SIG_MISMATCH_UPDATE, "允许覆盖安装签名不一致", G_CORE},
            {ALLOW_SPLIT_SIG_MISMATCH, "允许 Split APK 签名不一致", G_CORE},
            {ALLOW_NONSYSTEM_SHARED_UID, "允许非系统应用共享 UID", G_CORE},
            {ALLOW_HIDDEN_API, "允许系统应用使用隐藏 API", G_CORE},
            {PMS_COMMAND, "启用 adb shell pm pms 调试命令", G_CORE},
            // 截屏
            {CAPTURE_PRIVACY, "解除长截图/隐私页截屏限制", G_CAPTURE},
            {CAPTURE_NO_DELAY, "移除系统截屏长按延迟", G_CAPTURE},
            // 系统
            {UNLOCK_RESTRICTED, "自动解锁受限制的设置", G_SYSTEM},
            {EYE_TEXTURE, "护眼模式纸质纹理支持（需重启）", G_SYSTEM},
            // 系统服务
            {DISABLE_PIN_72H, "移除 72 小时密码验证", G_SERVICE},
            {ALLOW_UNTRUSTED_TOUCH, "允许不受信任的触摸事件", G_SERVICE},
            {IGNORE_AUDIO_FOCUS, "忽略音频焦点请求", G_SERVICE},
            {DISABLE_ROOT_CHECK, "禁用系统 Root 检测", G_SERVICE},
            {CUSTOM_FILE_MANAGER, "文件选择器由自定义文件管理器接管（需重启）", G_SERVICE},
            {FORBID_PICKER, "禁止系统接管文件/日历选择器（需重启）", G_SERVICE},
            {INSTALLER_REDIRECT, "安装器重定向到自定义安装器（需重启）", G_SERVICE},
            {UNINSTALLER_REDIRECT, "卸载器重定向到自定义卸载器（需重启）", G_SERVICE},
            // 分屏与小窗
            {SMALL_WIN_UNRESTRICT, "移除全部小窗限制（需重启）", G_WINDOW},
            {FORCE_MULTI_WINDOW, "强制支持多窗口模式", G_WINDOW},
            // 智慧侧边栏
            {SIDEBAR_ADD_LIMIT, "移除侧边栏应用添加上限（需重启）", G_SIDEBAR},
            // 安全中心
            {CLEAR_RISK_APPS, "清空安全扫描风险应用列表", G_SAFECENTER},
            {CLEAR_STATUS_ALERTS, "清空安全状态提醒项", G_SAFECENTER},
            {FAKE_SCAN_RESULT, "伪造安全扫描结果（总显示已通过）", G_SAFECENTER},
            {INSTALLER_WHITELIST, "强制包安装器白名单（任意来源可装）", G_SAFECENTER},
            {VIRUS_WHITELIST, "强制病毒扫描白名单", G_SAFECENTER},
            // 小布扫一扫
            {FULL_SCREEN_TRANSLATION, "全屏翻译（任意应用可用）", G_SCAN},
            // 主题商店
            {THEME_VIP, "解锁主题商店 VIP 特性", G_THEME},
            {THEME_SPLASH_ADS, "移除主题商店开屏广告", G_THEME},
            {THEME_UPGRADE, "移除主题商店升级弹窗", G_THEME},
    };

    /** 整型参数（Cfg.intv 读取 i_ 前缀；-1 = 跟随系统默认） */
    public static final String[] INT_PARAMS = {
            P_LAYOUT_ROWS, P_LAYOUT_COLS,
            P_MAX_SMALL_WINDOWS, P_WIN_CORNER, P_WIN_FOCUS_SHADOW, P_WIN_UNFOCUS_SHADOW};

    /** 字符串参数（Cfg.str 读取 s_ 前缀） */
    public static final String[] STR_PARAMS = {
            P_TILE_SCRIPT, P_FILE_MANAGER_PKG, P_INSTALLER_TARGETS, P_UNINSTALLER_TARGETS};

    private static final Map<String, Object> DEFAULTS = new HashMap<>();
    private static final Map<String, String> TITLES = new HashMap<>();

    static {
        for (String[] s : SWITCHES) {
            DEFAULTS.put(s[0], Boolean.FALSE);
            TITLES.put(s[0], s[1]);
        }
        put(P_LAYOUT_ROWS, 6);
        put(P_LAYOUT_COLS, 4);
        put(P_MAX_SMALL_WINDOWS, -1);
        put(P_WIN_CORNER, -1);
        put(P_WIN_FOCUS_SHADOW, -1);
        put(P_WIN_UNFOCUS_SHADOW, -1);
        put(P_TILE_SCRIPT, "");
        put(P_FILE_MANAGER_PKG, "bin.mt.plus");
        put(P_INSTALLER_TARGETS, "");
        put(P_UNINSTALLER_TARGETS, "");
        put(MASTER, Boolean.TRUE);
    }

    private static void put(String key, Object value) {
        DEFAULTS.put(key, value);
    }

    public static Object defaultValue(String key) {
        Object v = DEFAULTS.get(key);
        return v != null ? v : Boolean.FALSE;
    }

    public static String title(String key) {
        String t = TITLES.get(key);
        return t != null ? t : key;
    }

    /** 指定分组下的全部开关键（按 SWITCHES 声明顺序） */
    public static String[] groupSwitches(String group) {
        int n = 0;
        for (String[] s : SWITCHES) if (s[2].equals(group)) n++;
        String[] out = new String[n];
        int i = 0;
        for (String[] s : SWITCHES) if (s[2].equals(group)) out[i++] = s[0];
        return out;
    }

    public static boolean isSwitch(String key) {
        return TITLES.containsKey(key);
    }

    private FeatureKeys() {
    }
}
