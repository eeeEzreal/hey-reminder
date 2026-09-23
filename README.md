# Hey!

Hey! 是一个本地运行的 Android 连续使用超时提醒工具。它不会锁定或强制退出其他 App，只在连续使用达到设定时长后发送轻量提醒。

当前进度：**Phase 8 — 设置与监控策略**。总开关、黑/白名单模式以及提醒、延迟和暂停时间均已持久化，并会实时影响监控服务与通知操作。

## 构建

环境基线：JDK 17+、Android SDK Platform 36、Build Tools 36.0.0。

```powershell
$env:ANDROID_USER_HOME="$PWD\.android"
.\gradlew.bat :app:assembleDebug
```

技术决策和已知风险见 [`docs/TECHNICAL_PLAN.md`](docs/TECHNICAL_PLAN.md)，重要产品演进及其原因见 [`docs/product-evolution.md`](docs/product-evolution.md)。
