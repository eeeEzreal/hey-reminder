# Hey!

Hey! 是一个本地运行的 Android 连续使用超时提醒工具。它不会锁定或强制退出其他 App，只在连续使用达到设定时长后发送轻量提醒。

当前进度：**Phase 3 — 已安装 App 列表、名单选择与本地保存**，并已加入每日提醒次数统计基础模块。

## 构建

环境基线：JDK 17+、Android SDK Platform 36、Build Tools 36.0.0。

```powershell
$env:ANDROID_USER_HOME="$PWD\.android"
.\gradlew.bat :app:assembleDebug
```

技术决策和已知风险见 [`docs/TECHNICAL_PLAN.md`](docs/TECHNICAL_PLAN.md)。
