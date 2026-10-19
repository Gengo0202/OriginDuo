# HyperOS 4：代码修复与交叉验证

基线：`db03d5a`。本轮修改以 HyperOS 4 为目标，无连接设备；验证结论限于源码契约、模拟 View 的执行结果和 APK 构建。

这是绘制页扩展前的修复快照。当前构建还包含后续 UI 改动，请使用 [绘制页承接记录](drawing-general-parity-2026-10-17.md)及其新 manifest 核对产物；旧哈希不代表当前输出目录中的 APK。

## 本轮修复

- 外置信号、功率/温度、网络类型作为一条带统一排布。原实现对各视图独立 clamp，窄容器中会将它们压在同一边界；现在在实际图标行范围内统一缩放，按缩放后的宽度预留，空间完全被遮挡时暂停绘制并交还空间，恢复后重新预留。布局导致可用宽高变化时，通过去重的 posted 更新同步预留，避免在 onLayout 内修改 padding。
- 预留与排布共用性能读数的稳定槽宽，数字变化不再移动其外侧的网络类型。定位扣除系统原始 padding，且不会再次扣除模块已预留的 padding。
- 从图标行而非电池父容器读取岛 monitor。`getIslandWidth()` 用作逻辑起始侧遮挡边界，删除将其当整行平移距离的处理。RTL 在同一逻辑坐标上镜像。
- 空文字更新只重新汇总公共预留带，不先释放再预留其他读数仍在使用的空间。
- 电池 tint 依据电池自身位置及 tint areas 解析；复制分发参数，防止 ROM 后续复用列表/Rect 修改缓存。宿主位置变化时重新计算区域归属；同一行的邻接图标不再覆盖电池来源的颜色。
- 关闭模块或岛态停画时，在原生 onDraw 前卸载旧字形；按参考 SystemUI 的 `clearTint()` → `onDarkChangeInternal()` 顺序恢复原生颜色。
- legacy 绘制分支、缺槽位和槽位写入失败均走每帧直接绘制；正常槽位后续帧保持单次绘制。

## 证据与参考

本地 `work/jadx-out` 的 manifest 为 SystemUI `17.03.260226.r` / Android 17 / API 37。该样本未保存与某个完整 OS4 ROM build 绑定的采集哈希，因此仅作为实现契约样本，不能代表所有 OS4 构建。

- `MiuiStatusBatteryContainer.onLayout` 为电池与图标行分配不同 frame；隐藏电池时改变图标行端点。
- `MiuiStatusIconContainer.onLayout` 将 island width 与每个图标的 `layoutTranslationX` 比较，处理被遮挡的图标；它不是统一 translation。
- `DarkIconDispatcher.isInArea` 采用横向超过半宽且 area.top ≤ 0 的条件；空 area/list 表示全区域。
- `MiuiBatteryMeterIconView` 的多个刷新路径成对调用 clearTint 与 onDarkChangeInternal；legacy 分支绕过 tint drawable 槽。

公开源码对照：

- [HyperChanger：Drawable 的 tint 与状态处理](https://github.com/ColdP/HyperChanger/blob/55c5549791d5892c4f8d86064d45bd856992bdff/app/src/main/java/btm/m/os4/systemuihook/HyperSystemUiModule.kt#L167)，以及[多个 tint 刷新入口](https://github.com/ColdP/HyperChanger/blob/55c5549791d5892c4f8d86064d45bd856992bdff/app/src/main/java/btm/m/os4/systemuihook/HyperSystemUiModule.kt#L6750)。其状态图标替换不能直接证明电池/充电岛兼容性。
- [HyperChanger：图标可见性与测量宽度的区别](https://github.com/ColdP/HyperChanger/blob/55c5549791d5892c4f8d86064d45bd856992bdff/app/src/main/java/btm/m/os4/systemuihook/HyperSystemUiModule.kt#L6727)。本轮未移植其移动信号隐藏逻辑。
- [HyperCeiler：电池更新入口](https://github.com/ReChronoRain/HyperCeiler/blob/861be0473847d5c020102191f2c2891acda5d628/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/systemui/statusbar/icon/b/HideBatteryIconB.kt#L39)。用于交叉核对生命周期，不将该项目视为 OS4 全量兼容证明。

## 离线验证

顺序运行 `tests/*.ps1`，再执行：

```powershell
. .\scripts\env.ps1
gradle :app:assembleDebug :app:assembleRelease --offline --no-daemon --console=plain
```

新增/增强的关键入口：

| 入口 | 实际执行范围 |
| --- | --- |
| `tests/os4-layout.ps1` | 生产排布、预留、岛 monitor 读取方法与真正的 OutRingLayout/Refl；模拟 View 的几何、padding、缩放、透明度。覆盖窄空间、RTL、岛边界、完全遮挡后恢复、初始 padding、稳定性能槽、缺图标行和高度限制。 |
| `tests/issue-regressions.ps1 -Suite tint` | 生产 meter/icon tint hook、rowInk、TrioTint；覆盖区域内外、半宽边界、位置变化、参数复制、两类回调顺序与跨行独立性。 |
| `tests/glyph-drawable.ps1` | 真正的 GlyphDrawable/Refl 与生产 onDraw hook；五种宿主形状连续三帧，关闭/岛态首帧、恢复首帧、原生 tint 恢复。 |

执行脚本中的 `-SourceDirectory` 可指向复制的源码，进行故障注入；无需回滚当前工作树。

本轮结果：十个脚本通过；OS4 布局 3,716 条执行断言，五种字形宿主共 156 条，tint 26 条。
隔离源码副本分别注入「取消空间压缩」「忽略 tint area」「停用提前卸载」「仍按原尺寸预留」四项故障，
均在对应行为断言失败。Debug/Release 最终构建成功（1m 8s）。产物及源码哈希见同目录
`hyperos4-code-2026-10-17-manifest.json`；修改保留在工作区，尚未生成本轮提交。

## 后续设备验收

每次记录：机型、完整 ROM build、SystemUI 版本及 APK SHA-256、模块 APK SHA-256、是否重载 SystemUI、模块配置、其他状态栏模块。

1. 圆环/矩形/胶囊切换；检查原生时钟隐藏恢复、整体宽度恢复和时间更新。
2. 信号、性能读数、网络类型的八种开关组合；窄空间和大字号下检查互不覆盖，读数变化时原生行和外侧标签稳定。
3. 桌面、锁屏、控制中心分别测试深浅背景、局部亮暗区域和过渡动画。原生图标、字形与外置读数按各自所在行取色。
4. 物理充电触发岛出现、展开、收回；确认边界变化时没有额外整行偏移，遮挡结束后内容恢复。
5. 模块关闭、重新启用、SystemUI 重建；检查原生图标/电池/时钟及 tint 无残留。

尚待设备确认：原生未管理图标在空间耗尽时的 underflow/动画行为、祖先变换和 GPU 合成、不同 OS4 构建的私有字段差异。历史全局岛态 fallback 及其与多行/胶囊时钟的关系未在本轮重构。整带压缩可能降低极窄空间下的可读性，这是避免相互覆盖的显式退让行为。
