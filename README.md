# Hey!

Hey! 是一个本地运行的 Android 连续使用超时提醒工具。它不会锁定或强制退出其他 App，而是在连续使用达到设定时长后直接显示强视觉提醒。

当前进度：**Phase 9 — Overlay-first Reminder Engine V2**。到期时优先在当前 App 上显示包含“收到、再给我 X 分钟、暂停 X 分钟”的大尺寸 Overlay；未处理并继续使用 5 分钟会升级为二级提醒，系统通知只作为无声 fallback 和记录。首页会检查强提醒权限，设置页可用“测试强提醒”立即验证实际 Overlay。

## 构建

环境基线：JDK 17+、Android SDK Platform 36、Build Tools 36.0.0。

```powershell
$env:ANDROID_USER_HOME="$PWD\.android"
.\gradlew.bat :app:assembleDebug
```

技术决策和已知风险见 [`docs/TECHNICAL_PLAN.md`](docs/TECHNICAL_PLAN.md)，重要产品演进及其原因见 [`docs/product-evolution.md`](docs/product-evolution.md)。
