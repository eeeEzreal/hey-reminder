# Hey!

Hey! 是一个本地运行的 Android 连续使用超时提醒工具。它不会锁定或强制退出其他 App，只在连续使用达到设定时长后发送轻量提醒。

当前进度：**Phase 7 — 通知操作**。系统通知现已支持“收到”“再给我 5 分钟”和“暂停 30 分钟”，并保留本地暂停状态；可配置设置将在 Phase 8 实现。

## 构建

环境基线：JDK 17+、Android SDK Platform 36、Build Tools 36.0.0。

```powershell
$env:ANDROID_USER_HOME="$PWD\.android"
.\gradlew.bat :app:assembleDebug
```

技术决策和已知风险见 [`docs/TECHNICAL_PLAN.md`](docs/TECHNICAL_PLAN.md)，重要产品演进及其原因见 [`docs/product-evolution.md`](docs/product-evolution.md)。
