# Hey! v0.1 技术方案

## 已确认的基线

- Kotlin、Jetpack Compose、Material 3、ViewModel、DataStore。
- `minSdk 26`，`compileSdk 36`，`targetSdk 36`。
- AGP 9.1.0、Gradle 9.3.1、JDK 17 字节码目标。
- 单 Activity，按 `UI → ViewModel → Repository / Monitor → Android System APIs` 分层。

API 36 是当前机器已安装并能验证的稳定 SDK。API 37 尚未安装，因此 Phase 1 不依赖要求 API 37 的 Compose 1.12。

## 监控方案

1. Hey! 在已授权且名单非空时，从可见界面启动 `specialUse` 类型的前台服务。
2. 服务每秒轮询 `UsageStatsManager.queryEvents()`，用 `ACTIVITY_RESUMED` / `ACTIVITY_PAUSED` / `ACTIVITY_STOPPED` 维护当前恢复的 Activity 集合。
3. 使用 `PowerManager.isInteractive` 和 `KeyguardManager.isDeviceLocked` 识别锁屏；锁屏、系统界面、Hey! 自身和未选 App 都会结束当前会话。
4. 纯 Kotlin 状态机以 `SystemClock.elapsedRealtime()` 计时，目标 App 改变或离开时立即重置。
5. 默认阈值为 10 分钟；每个连续使用会话只产生一次 `ReminderConditionReachedEvent`。
6. 阈值事件通过独立的高优先级渠道发送使用提醒；只有通知权限、全局通知和渠道均可用且通知成功提交后，才调用 `ReminderStatsRepository.recordTriggeredReminder()`。
7. 前台服务的低优先级常驻通知仅用于满足 Android 后台运行要求，与使用超时提醒使用不同渠道。

## 系统通知

- Android 13+ 在 Usage Access 之后单独说明并请求 `POST_NOTIFICATIONS`；没有通知权限时不启动监控服务，避免后台计时却无法提醒。
- 通知标题为 `Hey!`，正文包含被监控 App 的显示名称和本次连续使用分钟数；名称读取失败时安全回退到包名。
- Phase 6 的通知点击仅打开 Hey!，不提前包含“收到”“再给我 X 分钟”或“暂停 X 分钟”，这些操作留在 Phase 7。
- 通知投递与提醒次数写入由可测试协调器串联，投递失败不增加“今天已提醒”。

## 每日提醒次数

- 使用 Preferences DataStore 按 ISO 本地日期保存独立计数桶，例如 `reminder_count_2026-09-21`。
- 唯一写入口为 `ReminderStatsRepository.recordTriggeredReminder()`；仅在系统通知真正触发后调用。
- 进入 App、开始计时以及处理“收到”“暂停”等通知操作不得调用计数入口。
- 数据模型支持任意闭区间汇总，后续周/月统计无需迁移已有每日数据。

## App 名单

- 通过 `ACTION_MAIN` + `CATEGORY_LAUNCHER` 查询用户可启动的 App，并显示名称、图标和包名。
- Android 11+ 使用 manifest `<queries>` 声明对应 intent 可见性，不申请 `QUERY_ALL_PACKAGES`。
- Hey! 自身从候选列表排除；名称或图标读取失败时使用安全回退，不因单个 App 异常中断列表。
- 已选包名集合保存在本机 Preferences DataStore，首页实时显示当前选择数量。

## App 图标

- Adaptive Icon 沿用现有 foreground/background 资源结构。
- 背景为附件 Logo 对应的柔和桃橙至珊瑚粉渐变，前景为米白色描边感叹号。
- 感叹号保持在 Adaptive Icon 安全区域内，并复用为 Android 13+ monochrome 图层。

不使用 AccessibilityService。WorkManager 的执行时机不精确，不适合持续的前台 App 判断；AlarmManager 仅在后续验证确有必要时用于暂停恢复兜底。

## 主要风险

1. Android 12+ 限制后台启动前台服务；监控必须由用户在可见界面主动开启，进程被停止后不能保证静默自启。
2. Android 14+ 要求前台服务声明类型；本用例只能使用 `specialUse`，发布到 Google Play 时需要额外审核说明。
3. Android 11+ 的包可见性限制会影响 App 查询；v0.1 仅声明 launcher intent 可见性，因此名单聚焦用户可直接启动的 App。
4. 厂商电池管理可能终止长期服务；需要在真机上逐厂商验证，并向用户明确展示运行状态。
5. Usage 事件不是跨设备完全一致的实时回调；锁屏、桌面、多窗口和快速切换必须用日志与真机测试校准。

## 阶段边界

- Phase 1：可构建、可启动、名称/图标/基础首页正确。
- Phase 2：Usage Access 解释、跳转和返回后状态刷新。已完成并通过 API 35 模拟器验证。
- Phase 3：可启动 App 列表、图标/名称、名单选择与本地保存。已完成并通过 API 35 模拟器重启验证。
- Phase 4：前台 App 检测、锁屏判断、连续使用状态机和单次阈值事件。已完成并通过 API 35 模拟器切换/锁屏验证。
- Phase 5：连续使用计时已与 Phase 4 合并完成，进入、离开、重新进入及目标 App 间切换均由状态机覆盖。
- Phase 6：达到阈值后发送系统通知，并在成功投递后记录每日提醒次数。已完成单元测试及 API 35 系统通知验证。
- 后续阶段严格按任务书顺序推进，每阶段先测试再继续。
