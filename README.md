# OriginDuo

把状态栏里的 **Wi-Fi、移动信号、电量** 合并成一个仿 iPhone「Duo」风格的三合一图标的 LSPosed 模块，
**针对 vivo OriginOS 适配**。

![状态栏效果](docs/images/statusbar.png)

> 本项目 fork 自 [yixing233/HyperDuo](https://github.com/yixing233/HyperDuo)（上游在小米 14 / HyperOS 4 上开发测试），
> 感谢原作者 [yixing233](https://github.com/yixing233) 的工作与开源。
> 遵循上游 **GPL-3.0** 许可证发布。

开启后，状态栏右上角原本分开的 Wi-Fi 图标、信号格与电池图标会被替换成一个整体字形：

- 外圈是**电池环**，缺口在下方，按电量点亮；
- 环里是 **Wi-Fi 弧线**，弧数跟随真实信号强度；
- 底部是**移动信号点**，四个点表示移动网络信号强度；
- 环内是**电量数字**，充电时变成闪电（快充为琥珀色闪电）。

全部组件可单独开关、排序与调色，改完立即生效；关掉任何一项，对应的系统原生图标回到原位。
详细玩法见[上游 README](https://github.com/yixing233/HyperDuo)。

## 适配设备

| 项目 | 要求 |
|------|------|
| 品牌 | vivo / iQOO（OriginOS 5 及以上） |
| 系统 | Android 15+（已在 X100s Pro / OriginOS 6 / Android 16 上针对视图结构适配） |
| 框架 | 支持 libxposed **API 102** 的 LSPosed，例如 LSPosed 2.2.0 |
| Root | 需要（LSPosed 本身依赖） |

其他品牌（小米/一加/三星等）请使用[上游原版](https://github.com/yixing233/HyperDuo)。

## 安装

1. 安装 `OriginDuo-x.x.x.apk`；
2. 打开 LSPosed → 模块 → 启用 OriginDuo → 作用域勾选「系统界面（`com.android.systemui`）」；
3. 重启系统界面（或重启手机）；
4. 打开 OriginDuo App 开启模块并调整组件。

版本号规则：`1.x.x` 为功能版本，`x.0.x` 为修复版本，`x.x.1` 为测试版本（最多 3 位数字），从 `1.0.1` 起。

## 相对上游的适配改动（v1.1.4）

- **绘制宿主**：vivo 的电池图标视图（横条/圆形/竖条三种样式均覆盖）,槽位——字形改为完全接管其 `onDraw`（跳过原生绘制，直接在系统给出的干净画布上作画）；
- **字形尺寸**：vivo 电池框仅 12dp 高（42px），字形放大 1.66 倍绘制至与 MIUI 版相同的 20dp；
- **贴合开口（iOS 风格）**：顶部槽位只放电量数字或充电闪电时，开口宽度按当帧内容实测宽度收紧，内容以圆环圈顶为视觉中心；放置其他内容时仍维持开放式宽开口；
- **原生图标折叠**：vivo 的图标容器是 AOSP 的 `StatusIconContainer`，折叠走 `addIgnoredSlots` + 布局期 `GONE` 双通道，槽位识别在 `getSlot()` 之外增加按视图类名兜底；
- **电量/充电状态**：vivo 宿主视图无反射字段可读，改由粘性电池广播采样（电量、充电状态、快充按实时充电功率 ≥10W 判定，只影响闪电颜色）；
- **信号等级**：无 `transformResId` 管线可挂钩——Wi-Fi 等级取自 RSSI 采样，移动信号取自电话业务轮询（与系统图标同源的 `SignalStrength`）；
- **网络类型**：环内/环外标签由数据卡的接入网类型映射（NR→5G、LTE→4G……）；
- **颜色跟随**：OriginOS 深浅色变化不经过任何可挂钩方法，前景色改为每 500ms 读取状态栏时钟文字颜色（屏幕熄灭时暂停）；
- **时钟**：胶囊样式接管时间时直接隐藏状态栏时钟，交还时恢复捕获时的原始可见性；
- **环外读数**（环外信号 / 环外网络类型 / 充电功率 / 电池温度）：挂载进 vivo 的 `system_icons` LinearLayout，由系统布局自然占位（因此间距滑杆在该 ROM 上暂不生效）；
- **独立标识**：包名 `io.github.gengo0202.originduo`，可与上游原版共存；更新渠道指向本仓库。

### 已知限制（OriginOS）

- 充电「超级胶囊 / 原子岛」接管状态栏时字形的避让逻辑未适配（上游针对 MIUI 灵动岛）；
- 「5GA」（5G-A）等角标暂不区分，均显示为 5G；
- 环外读数的间距微调滑杆不生效（由系统 LinearLayout 排布）。

## 构建

```
JDK 21 + Android SDK (Platform 37) + Gradle 9.8
gradle :app:assembleRelease -PoriginduoVersionName=1.0.3 -PoriginduoVersionCode=3 --no-daemon
```

**诊断构建**（可选）：编译时添加 `-PoriginduoDiag=true` 开关，可编入 adb shell 控制开关——root 执行

`su -c setprop persist.sys.originduo_test N` 可在不重启的情况下分别禁用某个子系统

（1=停绘制 / 2=停折叠 / 3=停取色轮询 / 4=着色钩子纯直通 / 6=停擦除 / 7=CLEAR 对照 / 8=停解除裁剪），

用于快速二分定位问题。

**公开发布的普通版不含也不应含有此机制，如有需要请自行构建测试**（R8 常量折叠后相关反射代码全部剥离）。

## 上游同步与防误推

本仓库是** vivo/OriginOS 自用适配 fork**，改动均为 OriginOS 专属，不打算也不应该推送到上游
[yixing233/HyperDuo](https://github.com/yixing233/HyperDuo)。GPL-3.0 对此完全兼容：自用修改无任何公开义务，
本仓库的公开发布已超出"分发时提供源码"的许可证要求，且 GPL 从不要求向上游回馈代码。

本地仓库已配置三重防误推：

1. `upstream` 远程为**只读**（推送地址已置为 `DISABLE`，`git push upstream` 会直接失败）；
2. `scripts/pre-push-guard` 推送守卫钩子：任何目标不是 `Gengo0202/OriginDuo` 的推送一律拦截
   （重新克隆后执行 `cp scripts/pre-push-guard .git/hooks/pre-push` 重新安装）；
3. 网页端唯一无法技术拦截的入口是 GitHub fork 页面的 "Contribute" 按钮——**不要**向上游发 PR。

同步上游更新的正确姿势（只拉不推）：

```
git fetch upstream
git merge upstream/main   # 解决冲突时保留 vivo 适配逻辑
```

## 反馈问题

重启系统界面后几分钟内复现问题，然后在设置里打开「调试日志」，用

```powershell
adb logcat -v time -s LSPosedFramework:* | Select-String OriginDuo
```

观察输出（日志 tag 由框架代打，不是 `OriginDuo`）。启动时会打印 `OriginDuo installed, hooks=N`；
某个功能没生效时会打印 `skip <id>: method not found`，说明该处系统实现与适配预期不一致，
欢迎把日志带到 [issue](https://github.com/Gengo0202/OriginDuo/issues)。

## 许可证

GPL-3.0（继承自上游），全文见 [LICENSE](LICENSE)。
