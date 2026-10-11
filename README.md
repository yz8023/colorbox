# ColorBox

**ColorOS 多功能工具箱** —— 一个 LSPosed 模块，聚合 10 组 53 项功能开关。截屏/录屏解锁只是其中一个分区，另含安装限制破解、系统服务增强、分屏与小窗、安全中心伪装、主题商店、小布扫一扫、智慧侧边栏、桌面定制等。

- 包名：`com.xiaokan.colorbox`
- 双入口：现代 **libxposed API 102**（`META-INF/xposed/java_init.list`）+ 经典 **XposedBridge**（`assets/xposed_init`），兼容所有 Xposed 系框架
- 配置通道：`HookStateProvider`（`content://com.xiaokan.colorbox.hookstate`，`cfgall` 全量下发 b_/i_/s_ 前缀扁平键，hook 侧 `Cfg` 2 秒缓存读取，改动约 2 秒内生效）
- Manifest 零 uses-permission，无网络、无存储

## 功能分组

| 分组 | 开关数 | 功能摘要 | 来源 |
| --- | --- | --- | --- |
| 桌面 | 6 | 文件夹预览背景、布局行列数、角标、卸载黑名单 | LuckyTool / OShin |
| 核心破解 | 23 | 安装限制、安装器增强、PMS 签名校验绕过 | LuckyTool / CorePatch |
| 截屏 | 2+常驻 | 隐私页长截图、延迟移除 + framework 层 FLAG_SECURE 全链路解除 | DisableFlagSecure / 本项目 |
| 系统 | 2 | 受限设置解锁、护眼纸质纹理 | LuckyTool |
| 系统服务 | 8 | 72h 验证、触摸事件、音频焦点、Root 检测、文件管理器接管、安装/卸载器重定向 | OShin |
| 分屏与小窗 | 2+4参 | 小窗限制解除、多窗口模式、圆角/阴影参数 | OShin |
| 小布扫一扫 | 1 | 全屏翻译 | OShin |
| 智慧侧边栏 | 1 | 应用添加上限 | OShin |
| 安全中心 | 5 | SafeProvider 状态伪装（风险列表/提醒/扫描结果/双白名单） | OShin v16.13 |
| 主题商店 | 3 | VIP、开屏广告、升级弹窗 | OShin |

完整清单（含每项开关键、来源项目 GitHub 直链、引入版本）：[FEATURES.md](FEATURES.md)

## 截屏解锁工作原理（常驻部分）

模块入口 `com.xiaokan.colorbox.HookEntry` 继承 `io.github.libxposed.api.XposedModule`，在 `android`（system_server）与截图相关组件激活，按 [LSPosed DisableFlagSecure](https://github.com/LSPosed/DisableFlagSecure) 方案实现：

| Hook 目标 | 作用 |
| --- | --- |
| `WindowState.isSecureLocked` | 窗口级 FLAG_SECURE 解除（Surface 创建路径保留原语义，防止破坏 DRM） |
| `ScreenCapture[Internal]$CaptureArgs` | 捕获参数强制包含安全层，覆盖 `nativeCaptureDisplay` / `nativeCaptureLayers` |
| `ScreenshotHardwareBuffer.containsSecureLayers` | 捕获结果不再被判定为含安全层 |
| `DisplayControl.createVirtualDisplay` / `SurfaceControl.createDisplay` | 系统虚拟显示强制 secure 属性 |
| `VirtualDisplayAdapter.createVirtualDisplayLocked` | MediaProjection 录屏虚拟屏补 `VIRTUAL_DISPLAY_FLAG_SECURE`，录屏不再黑屏 |
| `WindowManagerService.registerScreenRecordingCallback` (V+) | 屏蔽录屏检测回调 |
| `ActivityTaskManagerService.registerScreenCaptureObserver` (U+) | 屏蔽截屏监听通知 |
| `ActivityManagerService.checkPermission` (S~T) | `CAPTURE_BLACKOUT_CONTENT` 放行为 `READ_FRAME_BUFFER` |
| HyperOS / One UI / ColorOS 私有限制 | `notAllowCaptureDisplay` / `WmScreenshotController` / `OplusLongshotMainWindow` 等 |
| `OplusScreenCapture$CaptureArgs$Builder.setUid` (ColorOS 15+) | 截图 uid 校验旁路 |
| `SurfaceView.setSecure`（作用域内普通应用） | 解除抖音等 secure SurfaceView 白屏问题 |

system_server 内先对 `WindowStateAnimator.createSurfaceLocked`、`WindowManagerService.relayoutWindow` 及 lambda 合成类做 **deoptimize**，保证 hook 生效。覆盖 ColorOS 16 / HyperOS / One UI / AOSP 路径。

## 来源项目

| 项目 | GitHub |
| --- | --- |
| OShin | https://github.com/suqi8/OShin |
| LuckyTool | https://github.com/luckyzyx/LuckyTool |
| CorePatch | https://github.com/LSPosed/CorePatch |
| DisableFlagSecure | https://github.com/LSPosed/DisableFlagSecure |
| lsparanoid（逆向辅助） | https://github.com/LSPosed/lsparanoid |

## 构建

```bash
./gradlew :app:assembleRelease
```

- compileSdk = 36，minSdk = 28，targetSdk = 36
- Xposed API：libxposed API 102（`app/libs/libxposed-api-102.0.0.jar`，内置）
- Java 11 兼容

## 安装要求

1. 已 root 并安装 LSPosed（支持 libxposed 新 API 或经典 API 均可）
2. 在 LSPosed 中启用模块，作用域按需勾选（建议全选内置 17 项），系统服务类开关需重启手机
3. 在应用内打开各功能开关；标注「重启生效」的开关需重启对应应用或手机
4. 在 LSPosed 停用模块并重启即可完全恢复系统默认行为

## 免责声明

仅供学习 Android framework / libxposed 技术研究，请勿用于侵犯他人隐私或违反目标应用服务条款的场景，使用带来的后果由使用者自行承担。
