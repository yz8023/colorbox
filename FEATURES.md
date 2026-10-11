# ColorBox 功能总清单

> 模块包名 `com.xiaokan.colorbox` · 双入口 LSPosed 模块（libxposed API 102 + 经典 XposedBridge）
> 本文档完整记录：全部功能、每项功能的来源项目（GitHub 直链）、UI 模板来源。
> 数据与代码同步日期：2026-10-11（ColorBox v1.0.0，自 coloros-force-recorder v1.3.2 迁移）

---

## 一、来源项目总表

| 来源项目 | GitHub | 用途 | 对应提交 |
| --- | --- | --- | --- |
| **OShin** | https://github.com/suqi8/OShin | 系统服务、卸载黑名单、安装/卸载器重定向、文件管理器接管、分屏小窗、小布扫一扫、主题商店、智慧侧边栏、安全中心 SafeProvider（v16.8 源码 + v16.13 APK 逆向，lsparanoid 解密 3224/3224） | `ab8fae7`、`1ba0b98`、`e91178a` |
| **LuckyTool** | https://github.com/luckyzyx/LuckyTool | 桌面组、核心破解安装器组、系统组、磁贴脚本等 22 项功能（v1.3.0 功能移植） | `045fe27` |
| **CorePatch** | https://github.com/LSPosed/CorePatch | PMS 签名校验绕过 11 项（方案来源；键位按 OShin 的 `package_manager_services` 页对齐移植） | `ab8fae7` |
| **DisableFlagSecure（Enable Screenshot）** | https://github.com/LSPosed/DisableFlagSecure | 截屏/录屏解锁方案（v1.1.0 起按此方案重写，framework 层 12+ 处常驻解除） | `76b6bc0`~`8989d59` |
| **EcmUtils**（LuckyTool 内置工具类） | https://github.com/luckyzyx/LuckyTool/blob/main/app/src/main/java/com/luckyzyx/luckytool/utils/EcmUtils.kt | 受限设置自动解锁的 ECM 增强确认模式（`EcmLike.java` 反射移植） | v1.3.0 |
| **coloros 专用强制录屏 v1.0.4** | https://github.com/yz8023/coloros-force-recorder（v1.0.4 复原记录） | 本模块起点：对原 APK 源码级逆向复原，截屏功能根基 | `ffe5351` |

模块自研部分：双入口架构（HookEntry/LegacyEntry）、HookStateProvider 配置通道（`cfgall`，b_/i_/s_ 前缀 + Cfg 2 秒缓存）、DexLocator（自含式 dex 定位器）、`hook_master` 总开关门控、全部 UI 代码。

逆向辅助工具（非功能来源）：lsparanoid 解密器 https://github.com/LSPosed/lsparanoid

---

## 二、UI 模板说明

| 项 | 内容 |
| --- | --- |
| **UI 模板** | **套用 OShin 的应用界面**：https://github.com/suqi8/OShin |
| 模板结构 | OShin 主界面 = 底部导航（BottomTabs/NavigationBar）+ 卡片分组设置页 + 页面导航（AppNavHost）；本模块按同一页面结构复刻：顶部标题栏 + 底部导航三页（状态/功能/工具）+ 彩色圆标卡片分组 + 开关列表 |
| 实现差异 | OShin 本体用 Jetpack Compose + miuix-kmp 组件库（`top.yukonga.miuix.kmp`）；本模块为兼容最小依赖，用原生 View（LinearLayout/GradientDrawable/Switch）自绘同款布局，零第三方 UI 库 |
| 数据驱动 | `FeatureKeys.SWITCHES`（key/标题/分组）、`GROUP_ORDER`、`GROUP_COLORS`、`GROUP_ICONS`、`GROUP_DESCS` 由 MainActivity 循环渲染，新增开关零 UI 改动 |
| 主题 | 深色/浅色自适应（uiMode 检测），分组彩色圆标 + 卡片圆角 + 波纹 |
| 配置存储 | UI 写 `SharedPreferences("cfg")`（明文键）与 `("settings")`（`hook_包名` 作用域停用、`hook_master`）；`HookStateProvider` 以 `b_/i_/s_` 前缀扁平化下发，hook 侧 `Cfg` 读取（2 秒缓存，改动约 2 秒内生效） |

---

## 三、功能清单（10 组 53 项开关 + 10 项参数 + 1 总开关）

### 0. 总开关（自研）

| 功能 | 键 | 说明 |
| --- | --- | --- |
| 模块总开关 | `hook_master` | 关闭后所有作用域 hook 直接放行 |

### 1. 桌面（6 项）

| 功能 | 开关键 | 来源 | 引入 |
| --- | --- | --- | --- |
| 移除文件夹预览背景 | `folder_preview_bg_remove` | [LuckyTool](https://github.com/luckyzyx/LuckyTool) | v1.3.0 |
| 桌面布局行列数自定义（4-7 列 / 6-10 行） | `launcher_layout_custom` | [LuckyTool](https://github.com/luckyzyx/LuckyTool) | v1.3.0 |
| 移除快捷方式徽标 | `remove_app_shortcut_badge` | [LuckyTool](https://github.com/luckyzyx/LuckyTool) | v1.3.0 |
| 移除工作空间徽标 | `remove_app_work_badge` | [LuckyTool](https://github.com/luckyzyx/LuckyTool) | v1.3.0 |
| 移除应用分身徽标 | `remove_app_clone_badge` | [LuckyTool](https://github.com/luckyzyx/LuckyTool) | v1.3.0 |
| 移除应用卸载黑名单 | `remove_app_uninstall_blacklist` | [OShin](https://github.com/suqi8/OShin)（xm0） | v1.3.1 |

### 2. 核心破解（23 项）

**基础安装限制（12 项）— [LuckyTool](https://github.com/luckyzyx/LuckyTool)**

| 功能 | 开关键 | 引入 |
| --- | --- | --- |
| 强制启用 32 位支持（重启生效） | `force_enable_32_bit_support` | v1.3.0 |
| 移除 ADB 安装确认 | `remove_adb_install_confirm` | v1.3.0 |
| 允许降级安装 | `allow_downgrade_install` | v1.3.0 |
| 跳过签名/病毒验证（重启生效） | `disable_signature_verify` | v1.3.0 |
| 修复安装按钮显示异常 | `fix_install_button_display` | v1.3.0 |
| 跳过 APK 安全扫描 | `skip_apk_scan` | v1.3.0 |
| 禁止启动 AppDetail 定制扫描 | `disable_start_appdetail` | v1.3.0 |
| 自动点击安装按钮 | `auto_click_install_button` | v1.3.0 |
| 自动点击卸载按钮 | `auto_click_uninstall_button` | v1.3.0 |
| 移除安装完成广告 | `remove_install_ads` | v1.3.0 |
| 安装页顶部显示包信息 | `show_more_apk_info` | v1.3.0 |
| 允许卸载/停用系统应用 | `allow_disabling_system_apps` | v1.3.0 |

**PMS 签名校验绕过（11 项）— 方案 [CorePatch](https://github.com/LSPosed/CorePatch)，键位按 [OShin](https://github.com/suqi8/OShin) `package_manager_services` 移植**

| 功能 | 开关键 | 引入 |
| --- | --- | --- |
| 禁用安装包验证（重启生效） | `disable_install_verification` | v1.3.1 |
| 禁用 JAR 签名校验（重启生效） | `disable_jar_verifier` | v1.3.1 |
| 禁用消息摘要比对（重启生效） | `disable_message_digest` | v1.3.1 |
| 绕过 resources.arsc 压缩检查 | `bypass_arsc_uncompressed_check` | v1.3.1 |
| 绕过最低签名方案版本检查 | `bypass_min_signature_version_check` | v1.3.1 |
| 忽略 V1 签名错误（CorePatch 方式） | `bypass_v1_signature_errors` | v1.3.1 |
| 允许覆盖安装签名不一致 | `allow_signature_mismatch_on_update` | v1.3.1 |
| 允许 Split APK 签名不一致 | `allow_mismatched_split_apk_signatures` | v1.3.1 |
| 允许非系统应用共享 UID | `allow_nonsystem_shared_uid` | v1.3.1 |
| 允许系统应用使用隐藏 API | `allow_system_app_hidden_api` | v1.3.1 |
| 启用 adb shell pm pms 调试命令 | `enable_pms_command` | v1.3.1 |

### 3. 截屏（2 项 + 常驻解除）

| 功能 | 开关键 | 来源 | 引入 |
| --- | --- | --- | --- |
| 解除长截图/隐私页截屏限制 | `remove_screenshot_privacy_limit` | [OShin](https://github.com/suqi8/OShin) 同名键 | v1.3.0 |
| 移除系统截屏长按延迟 | `remove_system_screenshot_delay` | [OShin](https://github.com/suqi8/OShin) 同名键 | v1.3.0 |

> 另有无需开关、随模块常驻的 `SecureCaptureHooks`（WindowState.isSecureLocked、CaptureArgs、SurfaceView.setSecure 等 12+ 处 framework 解除），方案来源：[DisableFlagSecure](https://github.com/LSPosed/DisableFlagSecure)，详见 README「工作原理」表。

### 4. 系统（2 项）

| 功能 | 开关键 | 来源 | 引入 |
| --- | --- | --- | --- |
| 自动解锁受限制的设置 | `auto_unlock_restricted_settings` | [LuckyTool](https://github.com/luckyzyx/LuckyTool)（ECM 模式经 [EcmUtils](https://github.com/luckyzyx/LuckyTool/blob/main/app/src/main/java/com/luckyzyx/luckytool/utils/EcmUtils.kt) 移植） | v1.3.0 |
| 护眼模式纸质纹理支持（重启生效） | `enable_eyeprotect_paper_texture_support` | [LuckyTool](https://github.com/luckyzyx/LuckyTool) | v1.3.0 |

### 5. 系统服务（8 项）— [OShin](https://github.com/suqi8/OShin)

| 功能 | 开关键 | 来源 hooker | 引入 |
| --- | --- | --- | --- |
| 移除 72 小时密码验证 | `disable_pin_verify_per_72_hours` | OShin xm0 | v1.3.1 |
| 允许不受信任的触摸事件 | `allow_untrusted_touch` | OShin xm0 | v1.3.1 |
| 忽略音频焦点请求 | `ignore_audio_focus` | OShin | v1.3.1 |
| 禁用系统 Root 检测 | `disable_root_check` | OShin | v1.3.1 |
| 文件选择器由自定义文件管理器接管（重启生效） | `enable_custom_file_manager` | OShin | v1.3.1 |
| 禁止系统接管文件/日历选择器（重启生效） | `forbid_file_and_calendar` | OShin | v1.3.1 |
| 安装器重定向到自定义安装器（重启生效） | `enable_installer_redirect` | OShin d80 | v1.3.1 |
| 卸载器重定向到自定义卸载器（重启生效） | `enable_uninstaller_redirect` | OShin c80 | v1.3.1 |

### 6. 分屏与小窗（2 项开关 + 4 参数）— [OShin](https://github.com/suqi8/OShin)（`split_screen_multi_window` 页）

| 功能 | 开关键 | 引入 |
| --- | --- | --- |
| 移除全部小窗限制（重启生效） | `remove_all_small_window_restrictions` | v1.3.1 |
| 强制支持多窗口模式 | `force_multi_window_mode` | v1.3.1 |

### 7. 小布扫一扫（1 项）— [OShin](https://github.com/suqi8/OShin)（qd0，com.coloros.ocrscanner）

| 功能 | 开关键 | 引入 |
| --- | --- | --- |
| 全屏翻译（任意应用可用） | `full_screen_translation` | v1.3.1 |

### 8. 智慧侧边栏（1 项）— [OShin](https://github.com/suqi8/OShin)（tz1，com.coloros.smartsidebar）

| 功能 | 开关键 | 引入 |
| --- | --- | --- |
| 移除侧边栏应用添加上限（重启生效） | `remove_app_add_limit` | v1.3.1 |

### 9. 安全中心（5 项）— [OShin](https://github.com/suqi8/OShin) v16.13（hz1，com.oplus.safecenter SafeProvider）

| 功能 | 开关键 | 引入 |
| --- | --- | --- |
| 清空安全扫描风险应用列表 | `clear_risk_apps` | v1.3.2 |
| 清空安全状态提醒项 | `clear_status_alerts` | v1.3.2 |
| 伪造安全扫描结果（总显示已通过） | `fake_scan_result` | v1.3.2 |
| 强制包安装器白名单（任意来源可装） | `force_packageinstaller_whitelist` | v1.3.2 |
| 强制病毒扫描白名单 | `force_virus_whitelist` | v1.3.2 |

### 10. 主题商店（3 项）— [OShin](https://github.com/suqi8/OShin)（e02，com.heytap.themestore）

| 功能 | 开关键 | 引入 |
| --- | --- | --- |
| 解锁主题商店 VIP 特性 | `unlock_themestore_vip_features` | v1.3.1 |
| 移除主题商店开屏广告 | `remove_themestore_splash_ads` | v1.3.1 |
| 移除主题商店升级弹窗 | `remove_themestore_upgrade` | v1.3.1 |

### 参数（10 项，UI「工具」页设置）

| 参数 | 键 | 类型 | 默认 | 来源 |
| --- | --- | --- | --- | --- |
| 桌面布局行数 | `launcher_layout_max_rows` | int | 6 | [LuckyTool](https://github.com/luckyzyx/LuckyTool) |
| 桌面布局列数 | `launcher_layout_max_columns` | int | 4 | [LuckyTool](https://github.com/luckyzyx/LuckyTool) |
| 小窗同时个数上限 | `max_simultaneous_small_windows` | int(-1=默认) | -1 | [OShin](https://github.com/suqi8/OShin) |
| 小窗圆角半径（px） | `small_window_corner_radius` | int(-1=默认) | -1 | [OShin](https://github.com/suqi8/OShin) |
| 小窗聚焦阴影 | `small_window_focused_shadow` | int(-1=默认) | -1 | [OShin](https://github.com/suqi8/OShin) |
| 小窗失焦阴影 | `small_window_unfocused_shadow` | int(-1=默认) | -1 | [OShin](https://github.com/suqi8/OShin) |
| 快捷磁贴脚本命令 | `tile_script_command` | string | 空 | [LuckyTool](https://github.com/luckyzyx/LuckyTool) |
| 自定义文件管理器包名 | `custom_file_manager_package` | string | bin.mt.plus | [OShin](https://github.com/suqi8/OShin) |
| 安装重定向目标包名 | `installer_target_packages` | string(逗号分隔) | 空 | [OShin](https://github.com/suqi8/OShin) |
| 卸载重定向目标包名 | `uninstaller_target_packages` | string(逗号分隔) | 空 | [OShin](https://github.com/suqi8/OShin) |

---

## 四、已逆向、待移植（推迟中）— 均来自 [OShin](https://github.com/suqi8/OShin) v16.13

| 功能 | hooker | 推迟原因 |
| --- | --- | --- |
| 支付保护（禁用安全扫描/移除风险云提醒） | pz1（com.coloros.securepay，2 开关） | hook 点为 DexKit 运行时查找，环境无 securepay APK、未打包 DexKit |
| COUI 全局界面定制（去分割线/开关音效/15+ 尺寸颜色键） | hw1/gw1（prefs `allscope`） | DexKit 依赖大组，键已全部解密，待独立批次 |
| AppDetail 定制（移除版本检查/安全检查） | appdetail.kt（v16.8 源码 185 行可参考） | 环境无 com.oplus.appdetail APK；security_check 依赖 DexKit |

## 五、已排除项（核实后放弃）

| 功能 | 结论 |
| --- | --- |
| 文件选择器排序记忆（`remember_file_picker_sort`，资源 ID 0x6411059f/5a0） | OShin v16.13 中仅为孤儿字符串资源，无 hooker，v16.8 源码亦无实现 → 放弃移植 |

## 六、版本演进

| 版本 | 内容 |
| --- | --- |
| v1.0.4 | 「coloros 专用强制录屏」APK 源码级逆向复原 |
| v1.1.0~v1.2.6 | libxposed API 102 重写、双入口、按 [DisableFlagSecure](https://github.com/LSPosed/DisableFlagSecure) 方案重写、SurfaceView/SurfacePackage 深入解除 |
| v1.3.0 | [LuckyTool](https://github.com/luckyzyx/LuckyTool) 功能移植（22 项）、自含 DexLocator、UI 参照 [OShin](https://github.com/suqi8/OShin) 界面自绘 |
| v1.3.1 | [OShin](https://github.com/suqi8/OShin) 批量移植：系统服务/PMS 绕过/扫一扫/主题商店/卸载黑名单/侧边栏/分屏小窗/文件管理器接管/安装卸载器重定向 |
| v1.3.2 | 安全中心 SafeProvider 5 项（OShin v16.13 hz1）、UI 数据驱动三页重构、INT 参数下发修复 |
