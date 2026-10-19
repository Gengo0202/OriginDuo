# “绘制”功能独立验证方案

> 版本说明：本方案和清单固定的是胶囊合入前的绘制实现，已保存在提交 `bf87d47`。胶囊合入后的
> 布局、测试集合与原生绘制路径已有变化，当前验证入口见 `docs/CAPSULE-INTEGRATION.md`。

本方案交给其他 agent 执行。目标是独立检验最新修复，并提供可复现证据。任务范围是绘制编辑器，不包含 GitHub 上其他 SystemUI/MIUI 兼容问题的验收。

## 1. 待测版本与共同约定

- 项目目录：`C:\code\HyperDuo`。
- Git HEAD：`68a8dcc4b868b7ebe1437f006470fc9dde57ccfc`。**待测版本是当前整个工作区，包括未提交修改和未跟踪文件。** 单独检出此提交得到的是另一个版本。
- 同目录的 `source-manifest.json` 固定 app、tests、构建配置的 SHA-256。开始和结束时均校验；源码变动后建立新快照并重测受影响项。
- 全部源码保持原样。额外探针、mutation 副本和报告分别写到 `work/verify-drawing/A`、`B`、`C`。
- A/B 可以并行。B 独占仓库现有测试脚本与 Gradle 构建；它们使用固定输出目录。A 自己的编译输出放在 A 目录。
- C 在 B 提供本次构建成功记录和 APK 哈希后进行。真实设备只由 C 操作。
- 验证所得问题直接报告；实现修复留给主线程。保留失败断言和原始证据。

本轮待验证的四项行为：

1. 清空区域后，有/无 Wi-Fi × 充电/未充电四类状态均没有可选组件。
2. 无 Wi-Fi 墨迹时，区域名称与画面一致，显示为空。
3. 长按大图中的已有组件进入部件设置；长按空白区域进入组件选择。
4. 已选组件暂时被当前预览状态覆盖时，显示原因；切到适用状态后组件出现且提示消失。

**“空”的定义**：可选组件为空。外环、矩形电量条是基础图形，保留这些像素符合预期。

## 2. 开始前核验

所有 agent 先运行下列源码检查，在自己的报告中记录结果。`$repoRoot` 可替换为完整工作区副本的路径。

```powershell
$repoRoot = 'C:\code\HyperDuo'
Set-Location $repoRoot
$manifest = Get-Content 'docs/validation/drawing-2026-10-16/source-manifest.json' -Raw | ConvertFrom-Json
$mismatch = @(
    foreach ($entry in $manifest.files) {
        $path = Join-Path $repoRoot $entry.path
        if (!(Test-Path -LiteralPath $path -PathType Leaf)) {
            "MISSING $($entry.path)"
        } elseif ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) {
            "CHANGED $($entry.path)"
        }
    }
)
if ($mismatch.Count) { $mismatch; throw 'Source snapshot mismatch' }
git rev-parse HEAD
git status --short
```

源码清单证明输入身份。APK 哈希证明安装产物身份。Debug/Release 的版本号可能都仍为本地默认 `1.1 / 2`，验收以哈希为准。

清单中的 `existing_artifacts` 只是方案生成时现有 APK 的身份记录。重新构建可能产生不同字节；B 记录新产物哈希，C 与该新产物比对。

## 3. Agent A：独立逻辑、像素与反例

### 责任与方法

负责 `TrioZones` 的区域内容、清空约束、最少修改和菜单可达性。采用下节明确的行为预期作为判据，再调用待测生产代码核对。现有脚本的成功输出作为补充证据。

主要文件：

- `app/src/main/java/io/github/yixing233/hyperduo/TrioZones.java`
- `TrioAppearance.java`、`TrioSettings.java`、`TrioRenderer.java`、`TrioGeometry.java`，路径同上。
- `tests/shims/` 中的桌面绘制替身。

探针独立编译到 `work/verify-drawing/A/classes`。可以复用桌面 shims 和生产 renderer；预期值来自测试表与像素差，避免由同一个 `occupant()` 函数同时生成预期与实际。

### 通用基线 F0

每条用例从独立基线起步，或记录完整设置差分。

```text
enabled=true
trioStyle=STYLE_RING (0)
showWifi=true; showMobile=true; showValue=true; showBolt=true
dualSim=false; valueCentred=false
mobileTypeMode=MOBILE_TYPE_OFF (0)
signalMode=SIGNAL_IN_RING (0); stackedSignal=false
其余尺寸/颜色取 TrioSettings.defaults()

合成运行态：battery=79, mobileLevel=4, slotLevels=[2,4], mobileType="5GA"
Wi-Fi 有墨迹代表值=3，无墨迹代表值=-1
```

SIM 数来自合成读数 `[2,4]`，与 `dualSim` 开关独立。Wi-Fi 开关与 Wi-Fi 运行态同样独立。

### 必测用例

| ID | 初始状态与操作 | 独立预期 |
| --- | --- | --- |
| A01 | F0，未充电、Wi-Fi=3 | 上部电量数字、中部 Wi-Fi、下部移动信号 |
| A02 | F0 改 `showValue=false`，Wi-Fi=-1、类型关闭 | 中部为空；与相同运行态下 `showWifi=false` 的渲染像素一致 |
| A03 | A02 分别把 Wi-Fi 改为 0、1、3 | 0 没有 Wi-Fi 墨迹与占位；1、3 有实际像素并标为 Wi-Fi，关闭开关后这些像素消失 |
| A04 | F0 改 `showValue=false, mobileTypeMode=IN_RING`，Wi-Fi=-1；清空中部 | 操作前中部为类型；操作后四类状态均为空，排除“只改居中开关仍留类型” |
| A05 | A04 再改 `valueCentred=true`，清空中部 | Wi-Fi=3 时原中部已空，但 Wi-Fi=-1 时有类型；清空仍应消除后者 |
| A06 | 对圆环/矩形、三分区逐个清空；初始含默认配置与类型回退配置 | 每个成功结果在充电 true/false × Wi-Fi -1/3 下均没有可选组件；保留基础环/条 |
| A07 | F0 改 `mobileTypeMode=OUT_RING (2)`；下部选择移动信号，再独立测试清空下部 | 选择原有信号零修改；清空只需关闭移动信号。两次都保留 OUT_RING |
| A08 | F0，双卡开关关闭、合成读数两张卡；上部选择 DUAL | 菜单可达，结果可画双卡；开关关闭不应把 SIM 事实变成 0 |
| A09 | 两种样式 × 三分区 × 六种组件 | 菜单可选项与真正存在的解一致；灰项有位置限制，矩形中部只接受数字、矩形没有 DUAL 槽位 |
| A10 | F0 改 `mobileTypeMode=IN_RING`，清空中部 | 独立穷举相关开关，返回结果的变更数量达到四帧同时为空的最小值 |

A09 的 `choices` 与 `assign` 互相一致只是必要条件；还需检查返回配置经过真实 renderer 后，组件在适用运行态有像素。

A10 搜索范围：Wi-Fi、移动信号、数字、闪电、双卡、数字居中六个布尔值，以及网络类型模式。已有 OUT_RING 可以保留；原先未开环外类型的请求不应以新增环外标签来清空图标。保持样式、信号位置、尺寸颜色等不相关设置。

先比较修改键数量，再检查并列时隐藏组件更少、结果稳定。分别报告“生产候选范围内最优”与“独立搜索更大范围内最优”，发现差异时列出具体候选，而非直接调整预期。

### 像素检查

- 圆环和矩形分别覆盖有/无 Wi-Fi、充电/未充电、数字 5/79/100。
- 对 Wi-Fi 做开关前后的像素差，证明“当前占位”确有墨迹；整幅图有环线并不证明 Wi-Fi 存在。
- 保留现有矩形 `100` 大字号边界、两侧信号列、inset 坐标映射断言。
- 记录桌面字体/抗锯齿与 Android 的差异；比较语义区域与像素归属，不要求 JVM 截图逐像素等同真机。

### 必做失败对照

在 A 的独立源码副本分别制造下列变化；每次从相同原版副本开始，工作区源码哈希保持一致。

| 对照 | 副本中的变化 | 应失败的用例 |
| --- | --- | --- |
| M1 | 将 `r.wifi && r.drawWifi && !r.wifiInGap` 改成 `r.drawWifi && !r.wifiInGap` | A02/A03 幽灵占位断言 |
| M2 | 清空只遍历有 Wi-Fi 帧，去掉 -1 代表值，保留充电两帧 | A04/A05 中无 Wi-Fi 的清空断言 |

记录修改位置、实际应用的源码差分、编译成功、具体失败断言及退出码。编译失败不算“抓到行为缺陷”。

交付：`work/verify-drawing/A/report.md`，以及探针源码、运行命令、日志、必要的像素图和 mutation 差分。

## 4. Agent B：构建、现有回归与产物身份

### 执行顺序

1. 校验源码清单，记录 JDK/Gradle 环境。
2. 顺序执行现有两个相关脚本，保留原始日志与退出码。
3. 构建 Debug 与 Release，记录最终成功/失败和关键诊断。
4. 记录本次 APK 路径、大小、SHA-256、版本与签名证书摘要。
5. 再校验源码清单，发布 `build-result.json` 供 C 读取。

```powershell
Set-Location 'C:\code\HyperDuo'
$evidence = 'C:\code\HyperDuo\work\verify-drawing\B'
New-Item -ItemType Directory -Force $evidence | Out-Null

# drawing-zones 会重建自己的固定 work/zonescheck 目录；先核验目标。
$probeRoot = [IO.Path]::GetFullPath((Join-Path (Get-Location) 'work/zonescheck'))
if (!$probeRoot.StartsWith('C:\code\HyperDuo\work\')) { throw 'Unexpected probe workspace' }

& powershell -NoProfile -ExecutionPolicy Bypass -File tests/drawing-zones.ps1 2>&1 |
    Tee-Object (Join-Path $evidence 'drawing-zones.log')
if ($LASTEXITCODE) { throw 'drawing-zones failed' }

& powershell -NoProfile -ExecutionPolicy Bypass -File tests/issue-regressions.ps1 2>&1 |
    Tee-Object (Join-Path $evidence 'issue-regressions.log')
if ($LASTEXITCODE) { throw 'issue-regressions failed' }

. .\scripts\env.ps1
gradle :app:assembleDebug :app:assembleRelease --offline --no-daemon --console=plain 2>&1 |
    Tee-Object (Join-Path $evidence 'build.log')
if ($LASTEXITCODE) { throw 'APK build failed' }

Get-FileHash app/build/outputs/apk/debug/app-debug.apk,app/build/outputs/apk/release/app-release.apk -Algorithm SHA256 |
    Format-Table -AutoSize | Out-String |
    Set-Content (Join-Path $evidence 'apk-sha256.txt')
```

方案生成时的已知结果，用来发现错版本或测试被缩减：

- drawing-zones：1104 条检查、24 种配置、979 次渲染。
- issue-regressions：overlay 27、signal 4、tint 15、assignment 5、wifi 3、clear 31，共 85 条执行断言。
- 另有 SIM 接线与长按/反馈源码契约。它们证明源码连接关系，真机事件由 C 验证。

数字变化时说明具体原因；通过计数与具体断言内容共同判断。B 的 PASS 只覆盖构建与现有回归。

`build-result.json` 至少包含：源清单文件 SHA-256、起止校验结果、构建退出码、两个测试退出码、Debug/Release APK 绝对路径及各自哈希、签名摘要、生成时间。

交付：`work/verify-drawing/B/report.md`、上述 JSON 和日志。

## 5. Agent C：真实 Android 手势与持久化

### 前置与恢复

- 使用 B 刚验证的 APK，记录设备型号、Android/ROM、屏幕尺寸/密度、语言、字体大小、模块激活状态。
- 先记录当前设置，再安装相同签名的测试包并保持应用数据。签名冲突等安装失败单列为 BLOCKED，保留现有数据。
- 通过安装路径核对 APK 哈希。B 的本地构建成功不证明设备运行了它。
- 记录所有被测试改变的有效设置值，结束后通过应用设置入口恢复，并确认本地与远端显示收敛。
- 测试中改变的语言、主题、字体大小、屏幕方向等环境设置同样记录并恢复。
- 应用设置文件为 `shared_prefs/hyperduo_settings.xml`；debug 包支持时可用 `run-as` 只读保存快照。未写入的键按 `TrioSettings.defaults()` 解释。
- 直接覆盖 XML 只改变一个存储副本；恢复使用应用原有 setter/UI 通道，使 LSPosed 远端配置同步。
- 预览状态是合成数据。切“无 Wi-Fi”或“充电”预览就能测本轮行为，设备网络和充电器无需跟随切换。

可用的启动入口：

```text
io.github.yixing233.hyperduo/.ui.MainActivity
```

包名：`io.github.yixing233.hyperduo`。本地设置关键键名：

| 字段 | 存储键 |
| --- | --- |
| showWifi / showMobile / showValue / showBolt | show_wifi / show_mobile / show_value / show_bolt |
| dualSim | dual_sim_signal |
| valueCentred | swap_wifi_value |
| mobileTypeMode | mobile_type_mode：0 关闭、1 环内、2 环外 |
| trioStyle | trio_style：0 圆环、1 矩形 |
| signalMode | signal_mode：0 环内、1 环外 |

### 必测交互

每个用例记录初始设置、实际操作、截图/短录屏与操作后的有效设置差分。长按应注入真实 down—hold—up 序列；直接调方法仅能作为逻辑证据。

| ID | 操作步骤 | 通过标准 |
| --- | --- | --- |
| C01 基础显示 | F0，正常预览 | 三个区域行与大图对应，上数字、中 Wi-Fi、下信号 |
| C02 无 Wi-Fi 空白 | 关闭数字、类型关闭，切无 Wi-Fi | 中部行与菜单标题均为“空”，没有虚假 Wi-Fi 名称 |
| C03 清空类型回退 | 关闭数字、开启环内类型，切无 Wi-Fi；中部选择“空”，再遍历六种预览 | 中部可选内容保持为空；切换预览状态不写配置；环线保留 |
| C04 大图长按数字 | F0，长按大图上部数字 | 直接进入数字尺寸/颜色弹窗，标题正确；点击同一点仍打开组件列表 |
| C05 大图长按其他部件 | 对实际可见的 Wi-Fi、信号、闪电、网络类型、双卡分别长按 | 每次进入对应部件设置，编辑对象与当前画面一致 |
| C06 大图长按空白 | C02 状态，长按中部 | 打开中部组件选择菜单 |
| C07 零变更闪电选择 | F0，正常预览，上部选充电闪电；F0 已有 showBolt=true | 即使配置零变更也有“切到充电/快充查看”的提示；切充电后显示闪电，提示消失 |
| C08 充电覆盖 | F0 改数字居中，切充电；上部选择 Wi-Fi | 充电时仍显示闪电，提示充电优先；切正常后上部 Wi-Fi 出现，提示消失；切换状态本身不改设置 |
| C09 无 Wi-Fi 提示 | F0 改数字居中，切无 Wi-Fi；上部选择 Wi-Fi | 显示当前预览无 Wi-Fi 的提示；切正常后上部 Wi-Fi 出现、提示消失 |
| C10 旧提示清理 | C07/C09 出现提示后，再选择其他能显示的组件或清空 | 页面显示最新选择的结果，旧提示消失或更新，无重复提示堆积 |
| C11 双卡可达 | 双卡开关关闭，正常预览，上部选择双卡两排点 | 该项可选，开启后两排点与区域标签一致；单卡真机也使用两卡合成预览 |
| C12 环外模式保留 | 设置网络类型为环外；绘制页下部选择原有移动信号，再清空下部 | 原有选择不改配置；清空仅关闭移动信号；环外模式始终保留 |
| C13 矩形 | 切矩形；测顶部选择、中部数字设置、底部清空 | 两侧列命中下部；中部数字命中中部；清空底部后信号列消失、基础电量条保留 |
| C14 最大数字与边缘 | 矩形，数字字号最大；让预览显示三位数 100（测试入口/专门探针），检查数字左右边缘 | 数字笔画仍归中部；触点不会误开信号设置。常规六状态没有 100 入口时，真机此项标为未覆盖并引用 A 的像素证据 |
| C15 滚动与取消 | 从大图内做纵向拖动，从状态条横向滑动；按住后拖动取消 | 页面/状态条正常滚动；拖动不误改设置，不残留区域高亮，不误开弹窗 |
| C16 回调新鲜度 | 连续更换部件、状态和样式，立即长按同一位置 | 总是编辑当前帧组件，无上一轮组件的过期弹窗 |
| C17 保存与重进 | 执行一组选择/尺寸调整；切到常规页核对，退出应用再打开 | 组件与设置一致，重新进入后保持；预览状态可回默认，已保存配置保持 |
| C18 配色与文字 | 至少核对中文、英文和深浅主题；较大字体检查弹窗与提示 | 无占位符泄漏，提示完整可读，按钮/滑杆可触达 |
| C19 总开关关闭 | 关闭总开关进入绘制页，编辑后再启用 | 有明确关闭说明；预览可编辑，保存行为一致，启用后采用已保存设置 |

C05/C13 同时覆盖大图、下方区域行、组件菜单三种长按入口。灰项点击/长按均应保持设置不变。

多设置组合写入时，记录最终快照及短暂异常；持久化结论以应用读回与重开后的值为依据。

### 边界与证据要求

- 核心验收为圆环/矩形、六种预览、三分区、点击/长按/滚动、状态提示与保存。
- 设备条件允许时，补小屏/大字体、横屏、RTL；每个额外环境单列结果。
- SystemUI 是否加载本次模块代码需要另行确认；仅安装新设置 APK 不证明作用域已重载。本轮 UI 预览结论与状态栏运行结论分别报告。
- 没有真实 Android 事件证据的用例标为 BLOCKED 或未覆盖，保留离线结果，避免把源码接线检查写成真机通过。

交付：`work/verify-drawing/C/report.md`、每条关键用例的截图/短录屏、设置前后差分、APK 身份与恢复结果。

## 6. 统一报告与验收

每个 agent 使用以下模板：

```markdown
# 绘制验证 / Agent A、B 或 C
结论：PASS / FAIL / BLOCKED（仅针对本 agent 范围）

## 环境与版本
- 源清单 SHA-256 / 起止校验：
- APK SHA-256、签名、安装核验（适用时）：
- 工具与设备信息：

## 已执行用例
| ID | 实际结果 | 预期 | 判定 | 证据路径 |
| --- | --- | --- | --- | --- |

## 问题
- 严重度与标题：
- 完整初始设置和运行态：
- 最短复现步骤：
- 预期 / 实际：
- 源码 file:line 与原文：
- 设置差分、截图或失败断言：

## 覆盖缺口
- 未执行的项目、原因、所需条件：

## 收尾
- 源码哈希复核：
- 用户配置恢复结果（适用时）：
```

最终验收条件：A 的核心行为与两条 mutation 对照成立；B 的测试、Debug/Release 构建和身份核验通过；C01–C13、C15–C19 有真实事件证据并通过。C14 可以由 A 的 `100` 像素边界用例覆盖，同时保留真机未覆盖记录。所有环境限制明确列出。

P1：误写其他持久化键、清空失败、崩溃/卡死、配置丢失。P2：错误编辑对象、错误当前组件标签、提示与实际不符、手势被吞。纯外观间距问题单列为 P3。P1/P2 保持开放，最终结论列出剩余问题。

## 7. 可直接交给其他 agent 的开工提示

### 给 A

> 请阅读 `C:\code\HyperDuo\docs\validation\drawing-2026-10-16\PLAN.md`，只执行 Agent A 的独立逻辑/像素验证与两条失败对照。待测对象是当前工作区和 source-manifest.json 固定的源码，包含未提交文件。探针与证据放 work/verify-drawing/A，源码保持原样。交付带复现、file:line、实际运行证据与覆盖缺口的 report.md；现有自测 PASS 作为补充。

### 给 B

> 请阅读 `C:\code\HyperDuo\docs\validation\drawing-2026-10-16\PLAN.md`，只执行 Agent B 的版本校验、顺序运行现有回归、Debug/Release 构建与 APK 身份记录。你独占仓库固定测试/Gradle 输出目录。证据放 work/verify-drawing/B，提供 report.md 和供 C 使用的 build-result.json；源码保持原样。此任务的 PASS 限于构建与回归范围。

### 给 C

> 请阅读 `C:\code\HyperDuo\docs\validation\drawing-2026-10-16\PLAN.md`，只执行 Agent C 的真实 Android 交互与保存验证。先读取 B 的 build-result.json 并核验安装 APK 身份，记录和恢复用户设置。使用真实点击、长按和拖动检验 C01–C19；条件缺失项明确标注。源码保持原样，证据放 work/verify-drawing/C，报告包含截图/录屏、设置差分、安装身份和恢复结果。
