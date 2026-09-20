# Hey! v0.1 技术方案

## 已确认的基线

- Kotlin、Jetpack Compose、Material 3、ViewModel、DataStore。
- `minSdk 26`，`compileSdk 36`，`targetSdk 36`。
- AGP 9.1.0、Gradle 9.3.1、JDK 17 字节码目标。
- 单 Activity，按 `UI → ViewModel → Repository / Monitor → Android System APIs` 分层。

API 36 是当前机器已安装并能验证的稳定 SDK。API 37 尚未安装，因此 Phase 1 不依赖要求 API 37 的 Compose 1.12。

## 监控方案

1. 用户在 Hey! 前台主动打开总开关。
2. `specialUse` 类型的前台服务以低频轮询 `UsageStatsManager.queryEvents()`。
3. 读取最新的 `ACTIVITY_RESUMED` / `ACTIVITY_PAUSED`，并结合屏幕交互与锁屏状态判断当前前台包。
4. 纯 Kotlin 计时状态机负责“进入开始、离开清零、收到后完整周期、延迟提醒、全局暂停”。
5. 通知动作通过显式 `PendingIntent` 发送给不导出的 `BroadcastReceiver`。
6. 配置和暂停截止时间使用 Preferences DataStore 保存。

不使用 AccessibilityService。WorkManager 的执行时机不精确，不适合持续的前台 App 判断；AlarmManager 仅在后续验证确有必要时用于暂停恢复兜底。

## 主要风险

1. Android 12+ 限制后台启动前台服务；监控必须由用户在可见界面主动开启，进程被停止后不能保证静默自启。
2. Android 14+ 要求前台服务声明类型；本用例只能使用 `specialUse`，发布到 Google Play 时需要额外审核说明。
3. Android 11+ 的包可见性限制会影响“列出全部已安装 App”。个人侧载可评估 `QUERY_ALL_PACKAGES`，但公开上架会受 Play 政策限制。
4. 厂商电池管理可能终止长期服务；需要在真机上逐厂商验证，并向用户明确展示运行状态。
5. Usage 事件不是跨设备完全一致的实时回调；锁屏、桌面、多窗口和快速切换必须用日志与真机测试校准。

## 阶段边界

- Phase 1：可构建、可启动、名称/图标/基础首页正确。
- Phase 2：Usage Access 解释、跳转和返回后状态刷新。
- 后续阶段严格按任务书顺序推进，每阶段先测试再继续。

