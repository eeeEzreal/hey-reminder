# Hey!

Hey! 是一个本地运行的 Android 连续使用超时提醒工具。它不会锁定或强制退出其他 App，而是在连续使用达到设定时长后直接显示强视觉提醒。

当前进度：**Phase 9 — Overlay-first Reminder Engine V2**。到期时优先在当前 App 上显示包含“收到、再给我 X 分钟、暂停 X 分钟”的大尺寸 Overlay；未处理并继续使用 5 分钟会升级为二级提醒，系统通知只作为无声 fallback 和记录。首页会检查强提醒权限，设置页可用“测试强提醒”立即验证实际 Overlay。

## 最新修复记录

- 2026-09-27｜真机故障定位与环境修复：一加/OPlus 系统曾在 Hey! 的前台服务通知仍显示时冻结其后台进程，导致连续计时停止、无法自动弹窗；允许 Hey! 后台运行及自动启动后，同一 Debug APK 在小红书连续使用约 30 秒时自动显示 Overlay，继续使用又成功显示第二次。此前的代码加固不是这次冻结的根因修复，本次未更改 APK 逻辑。
- 后续计划：在权限引导中说明厂商电源管理，并增加监控心跳超时提示；当前版本尚不能自动开启厂商专有的后台运行许可，也不能仅凭前台服务通知判断计时仍在运行。

## 构建

环境基线：JDK 17+、Android SDK Platform 36、Build Tools 36.0.0。

```powershell
$env:ANDROID_USER_HOME="$PWD\.android"
.\gradlew.bat :app:assembleDebug
```

技术决策和已知风险见 [`docs/TECHNICAL_PLAN.md`](docs/TECHNICAL_PLAN.md)，重要产品演进及其原因见 [`docs/product-evolution.md`](docs/product-evolution.md)。

版本递增、Build 身份和带版本号的测试 APK 交付规则见 [`docs/VERSIONING.md`](docs/VERSIONING.md)。
