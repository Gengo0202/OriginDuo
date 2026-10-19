# 文档索引

仓库根目录只留两份 README（`README.md` 面向用户、`module-README.md` 是发布到 LSPosed
模块仓的首页），其余文档都在这里。

## 主要文档

| 文件 | 内容 |
| --- | --- |
| [DEVELOPMENT.md](DEVELOPMENT.md) | 开发文档。架构、渲染管线、配置键参考、测试方法、发布流水线，以及逐轮的改动记录与踩坑。**多数「为什么这样写」的答案在这里。** |
| [SWITCHES-REVIEW.md](SWITCHES-REVIEW.md) | 开关与设置项的系统性复盘：每一项的门控是否与渲染一致、哪些是死代码、门禁逻辑的边界。 |
| [CAPSULE-INTEGRATION.md](CAPSULE-INTEGRATION.md) | 胶囊样式的接入说明。 |
| [ISSUE-CODE-REVIEW-2026-10-16.md](ISSUE-CODE-REVIEW-2026-10-16.md) | 针对一批 issue 的代码审查记录与修复方案。 |
| [1003-todo.md](1003-todo.md) | 开发交接记录。大体积、按轮次推进，含未完成事项与历史决策。换环境继续开发时先读它。 |

## 发版说明

`docs/release-notes/release-notes-<version>.md` 是每次发版时的 changelog 源文件，由
`release.ps1 -NotesFile` 读取，内容即 GitHub Release 正文。

**这些文件必须入库。** 两个仓库的 Releases 页都只保留最新一版（旧 Release 会被脚本清理），
所以删掉 Release 之后，这里就是旧版本 changelog 的**唯一副本**。

发新版时照着已有文件的格式写一份放进该目录，然后：

```powershell
git push origin main
.\scripts\release.ps1 -Version 1.7 -NotesFile .\docs\release-notes\release-notes-1.7.md
```

## 验证与素材

| 路径 | 内容 |
| --- | --- |
| `validation/` | 各轮改动的验证记录：截图清单、测量数据、逐项核对结果。文件名带日期。 |
| `images/` | README 引用的截图（状态栏效果、设置界面等）。 |
| `ref-5ga.png` | 「5GA 中 A 的比例」这个设置项的参考量测图。 |
| `wifi-presence/` | Wi-Fi 图标显隐的实拍对照。 |
| `wifi-refresh-recovery/` | Wi-Fi 刷新异常的复现与恢复记录。 |

## 相关但不在这里

- `scripts/` — 构建、安装与发版脚本，说明见下。
- `tests/` — 离线验证脚本（真值表、渲染探针），每个都能 `exit 0` 即为通过。
- `work/` — 不入库的工作区，存放一次性的工装与中间产物。
- `.ref/` — 参考实现与反编译源码，不入库。

## scripts/

| 脚本 | 用途 |
| --- | --- |
| `env.ps1` | 设置 `JAVA_HOME` / `ANDROID_HOME` / `GRADLE_HOME` 并加一个 `gradle` 函数。**先 source 它**：`. .\scripts\env.ps1` |
| `install.ps1` | 装 debug APK 到设备、打开设置页、跟进日志。`-InstallOnly` / `-LogOnly` 可拆开用。 |
| `release.ps1` | 发版主入口：构建 release APK、推 tag、建 GitHub Release，末尾自动调 `release-module.ps1`。 |
| `release-module.ps1` | 把同一 APK 发到 LSPosed 模块仓。一般由 `release.ps1` 调用，主仓已发布时可单独补跑。 |
| `legacy/build.ps1` | 早期手写的 javac/d8/aapt2 离线管线，**已弃用**——它编译不了 Kotlin/Compose 与 `res/`，保留仅供查阅。 |

脚本内的仓库根路径是硬编码的 `C:\code\HyperDuo`，换目录需要一并改。

### 发版

```powershell
. .\scripts\env.ps1
git push origin main
.\scripts\release.ps1 -Version 1.7.0 -NotesFile .\docs\release-notes\release-notes-1.7.md
```

发版脚本只推 tag，**不推分支**，所以 `git push` 要单独先做；否则 GitHub 上有包和 tag，但默认分支还没有对应提交。
