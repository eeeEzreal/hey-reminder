# Hey! 版本与测试 APK 规范

Git commit 与 App 版本分别管理：有意义的代码或文档改动正常提交，但不因每次提交而升级 App 版本。提交沿用本机 Git 用户身份；代码、文档和测试的提交历史不重写。

## 已核验的版本起点

- 从建项提交 `8c7b51c` 到 2026-09-27，本仓库的 Android 配置始终是 `versionName=0.1.0`、`versionCode=1`；没有可核验的版本标签，不为中间提交补造版本号。
- 本规范首次落地时新增设置页“关于 Hey!”版本信息，下一份测试构建从 `versionName=0.1.1`、`versionCode=2` 开始。界面与交付文件使用 `v0.1.1` 的展示形式。

## 何时升级与交付

- 仅 README、文档、注释或测试变化：正常 commit，不升级 App 版本，也不因这些改动单独生成测试 APK。
- 实际运行逻辑变化、影响真机行为的 Bug 修复或用户可见功能变化：生成新测试 Build，并递增 `versionCode`，保证旧版上可直接覆盖安装。
- 小 Bug 修复递增补丁位，例如 `0.1.3 → 0.1.4`；明显新增功能或阶段版本递增次版本位并归零补丁位，例如 `0.1.x → 0.2.0`。具体版本号必须依据当前 Gradle 配置和真实版本历史决定。
- 构建前先完成测试和代码提交，并确认交付所需源码无未提交改动；`BuildConfig.GIT_COMMIT` 在构建时读取 Git HEAD 的 7 位短哈希。不要把未提交源码的 APK 描述为该 commit 的构建。
- 原始产物仍是 `app/build/outputs/apk/debug/app-debug.apk`。交付时运行 `:app:exportDebugApk`，它先构建，再额外复制为 `artifacts/apk/Hey-v<versionName>-debug.apk`；仅交付带版本号的文件，不用含糊的 `app-debug.apk` 供验收。
- 安装后在“设置 → 关于 Hey!”核对版本、Build 和 Git Commit；必要时也用 Android 包信息核对 `versionName` 与 `versionCode`。APK 文件名不是验证安装版本的唯一依据。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:exportDebugApk
```

测试 APK 是本机交付物，默认不纳入 Git。
