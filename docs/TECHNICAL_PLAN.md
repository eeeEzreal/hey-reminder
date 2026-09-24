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
4. 纯 Kotlin 状态机以 `SystemClock.elapsedRealtime()` 计时，目标 App、监控模式或时间设置改变时立即重置。
5. 默认阈值为 10 分钟；实际阈值来自本地设置，每次达到当前到期时间只产生一个 `ReminderConditionReachedEvent`，通知操作确认后才安排下一次到期时间。
6. 阈值事件进入纯 Kotlin `ReminderEngine`，由它维护首次到期、已呈现、用户处理、延迟、暂停、未响应升级和 Session 结束，不再用 `notify()` 成功代表提醒已完成。
7. Presentation 按 `OverlayReminderPresenter → UsageReminderNotifier` 顺序执行；优先使用 `TYPE_APPLICATION_OVERLAY` 显示大尺寸操作卡片，Notification 仅作 fallback 与系统记录。
8. 首次 Overlay 或 Notification 至少一种成功呈现后才调用 `ReminderStatsRepository.recordTriggeredReminder()`；同一 Session 的二级升级不重复计数。
9. 一级提醒呈现后用户若未操作且仍停留同一 App，5 分钟后升级为更强的二级 Overlay；离开目标 App、切换 Session、暂停、关闭或 Service 销毁都会清除旧 Overlay。
10. 前台服务的低优先级常驻通知仅用于满足 Android 后台运行要求，与使用超时提醒使用不同渠道。
11. Overlay 与 Notification 均投递失败时不会吞掉本轮提醒；只要用户仍停留在同一目标 App，就会在 30 秒后再次尝试呈现。

## Reminder Engine V2 与 Overlay

- `ReminderEngine` 不依赖 Android API，状态覆盖 Monitoring、FirstReminderDue、ReminderVisible、UserAcknowledged、Snoozed、Paused、EscalationDue 和 SessionEnded，可用 JVM 单元测试验证。
- Overlay 使用 `SYSTEM_ALERT_WINDOW`、`Settings.canDrawOverlays()` 与 `WindowManager.TYPE_APPLICATION_OVERLAY`，全屏半透明遮罩承载中央大卡片；一级与二级采用不同尺寸、配色、文案和震动节奏，默认不播放声音。
- Overlay 直接提供“收到、再给我 X 分钟、暂停 X 分钟”，并复用既有 `ReminderActionCommand`、`ReminderActionReducer` 和过期 Target 校验；旧 Session 或重复操作不得影响当前提醒。
- 首页将 Overlay 权限视为核心健康状态，缺失时明确显示“强提醒权限未开启”并进入系统授权页；设置页“测试强提醒”直接展示真实 Overlay，测试操作不改变监控状态或提醒统计。
- 已核对 Android 15 [后台 Activity 启动限制](https://developer.android.com/guide/components/activities/background-starts)：即使 `SYSTEM_ALERT_WINDOW` 在部分条件下属于例外，自动抢占式启动 Activity 的跨厂商可靠性和发布风险仍不适合作为默认方案，因此二级大型 Overlay 是正式升级方案。

## 系统通知 fallback

- Android 13+ 在 Usage Access 之后单独说明并请求 `POST_NOTIFICATIONS`；没有通知权限时不启动监控服务，避免后台计时却无法提醒。
- 首页同时检查 App 通知总开关和提醒渠道重要性；渠道被关闭或降级时不再显示“监控运行中”，而是引导用户进入对应系统通知设置。
- 使用新的 `visual_reminder_fallback_v2` 高重要性无声渠道，保留震动；标题和正文区分一级与二级提醒，并包含被监控 App 名称和连续使用分钟数。
- 通知提供“收到”“再给我 X 分钟”和“暂停 X 分钟”；按钮文字和核心行为均读取当前本地时间设置。
- Notification 成功只表示 fallback 已呈现，不会结束 Reminder Session；只有用户操作或离开目标 App 才改变 Session。
- 每个操作携带包名、连续会话开始时间和本轮提醒到期时间；只有与当前待处理提醒完全一致才会改变状态，重复点击和旧通知操作会被拒绝。

## 通知操作与暂停

- “收到”保留当前连续使用会话，并从点击时刻起安排一个完整提醒周期。
- “再给我 5 分钟”保留当前连续使用会话，并从点击时刻起按延迟时长安排下一次提醒；中途离开目标 App 仍立即清零。
- “暂停 30 分钟”清空当前会话，以系统时间保存暂停截止点；暂停期间不查询前台 App，到期后删除暂停记录并从新的会话开始。
- 暂停截止点使用 Preferences DataStore 本地保存，进程重建后仍可恢复；当前版本仍受前台服务无法在强制停止后自行启动的系统限制。

## 每日提醒次数

- 使用 Preferences DataStore 按 ISO 本地日期保存独立计数桶，例如 `reminder_count_2026-09-21`。
- 唯一写入口为 `ReminderStatsRepository.recordTriggeredReminder()`；仅在首次 Overlay 或 Notification 至少一种真正呈现后调用。
- 进入 App、开始计时以及处理“收到”“暂停”等通知操作不得调用计数入口。
- 数据模型支持任意闭区间汇总，后续周/月统计无需迁移已有每日数据。

## App 名单

- 通过 `ACTION_MAIN` + `CATEGORY_LAUNCHER` 查询用户可启动的 App，并显示名称、图标和包名。
- Android 11+ 使用 manifest `<queries>` 声明对应 intent 可见性，不申请 `QUERY_ALL_PACKAGES`。
- Hey! 自身从候选列表排除；名称或图标读取失败时使用安全回退，不因单个 App 异常中断列表。
- 已选包名集合保存在本机 Preferences DataStore；黑名单模式监控可启动 App 集合减去已选名单，白名单模式只监控已选且仍可启动的 App。
- 首页显示实际受监控 App 数量，名单管理页会根据当前模式说明“选中即监控”或“选中即排除”。

## 设置

- 总开关、名单模式、连续使用提醒时间、延迟提醒时间和临时暂停时间与 App 名单共用本地 `user_preferences` DataStore，不引入账号或后端。
- 默认值保持黑名单、10 分钟提醒、5 分钟延迟、30 分钟暂停；空黑名单默认监控所有可启动 App，避免首次使用看似就绪但实际没有任何监控目标。
- 总开关关闭后立即停止前台服务和提醒，关闭状态下不强制进入权限引导；重新开启后在权限齐全且受监控 App 非空时恢复服务。
- 模式或时间参数变化会结束当前连续会话并按新设置从 0 开始，防止同一会话混用新旧规则。
- 首页只有在权限完整且实际监控 App 数量大于 0 时才显示“监控运行中”，不再把空名单误报为就绪。

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
6. 系统设置等安全敏感页面可以主动隐藏第三方 Overlay；这属于 Android 防点击劫持机制，无法也不应绕过，此时由 Notification fallback 保底。API 35 端到端验收使用普通 Chrome 前台场景，避免把系统设置页误当作常规 App 能力。

## 阶段边界

- Phase 1：可构建、可启动、名称/图标/基础首页正确。
- Phase 2：Usage Access 解释、跳转和返回后状态刷新。已完成并通过 API 35 模拟器验证。
- Phase 3：可启动 App 列表、图标/名称、名单选择与本地保存。已完成并通过 API 35 模拟器重启验证。
- Phase 4：前台 App 检测、锁屏判断、连续使用状态机和单次阈值事件。已完成并通过 API 35 模拟器切换/锁屏验证。
- Phase 5：连续使用计时已与 Phase 4 合并完成，进入、离开、重新进入及目标 App 间切换均由状态机覆盖。
- Phase 6：达到阈值后发送系统通知，并在成功投递后记录每日提醒次数。已完成单元测试及 API 35 系统通知验证。
- Phase 7：实现“收到”“再给我 5 分钟”“暂停 30 分钟”，包含过期操作防护和暂停截止时间持久化。已完成单元测试及 API 35 通知操作验证。
- Phase 8：设置页、黑/白名单、三个时间参数和总开关已接入同一套本地配置并实时影响核心监控。已完成单元测试及 API 35 持久化、服务启停验证。
- 核心提醒闭环加固：纠正名单语义和默认空目标问题，增加旧设置迁移、通知可用性检查、投递重试和即时测试提醒；已通过 API 35 真实前台 App 连续使用 1 分钟的端到端验证。
- Phase 9：提醒语义重构为 Overlay-first Reminder Engine V2；一级大卡片、5 分钟未响应升级、明确处理、离开清理、无声 Notification fallback 和强提醒权限状态均已接入，并通过纯状态机测试及 API 35 真实前台 App 覆盖测试。
- 后续阶段严格按任务书顺序推进，每阶段先测试再继续。
