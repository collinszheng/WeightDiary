# 体重日记 (WeightDiary)

纯本地的体重 / 体脂趋势记录 App。**没有账号、没有网络权限、没有广告** —— 所有数据只存在你自己的设备上。

**下载**：[最新版 APK](https://github.com/collinszheng/WeightDiary/releases/latest) · 1.71 MB · 要求 Android 8.0（minSdk 26）及以上（体脂秤同步需 Android 9+）

<p align="center">
  <img src="docs/screenshots/home.png" width="200" alt="首页" />
  <img src="docs/screenshots/add.png" width="200" alt="添加数据" />
  <img src="docs/screenshots/records.png" width="200" alt="全部记录" />
  <img src="docs/screenshots/settings.png" width="200" alt="设置" />
</p>

## 功能

- **概览** —— 「当前 / 目标体重 + BMI 水平」卡片，附最近一次体脂
- **折线图** —— 自绘 Canvas，无第三方图表库。日 / 周 / 月 / 年 / 总五种区间，整数刻度、等宽数字、monotone cubic 平滑
- **BMI 预警** —— 超重 / 肥胖的分级阈值直接画进体重图。**只在它落进当前坐标范围时出现** —— 体重贴近临界值时那条线自然浮现，离得远就不画，也绝不为了画它把折线压平
- **记录管理** —— 首页历史区、全部记录、点击编辑、左滑露出删除按钮（左滑本身不删除，必须再点一下）
- **BMI 标准** —— 中国 / WHO 两套阈值可切换
- **体脂秤同步** —— 通过 Health Connect 读体重与体脂率。走**设备内 IPC**，仍然不需要网络权限；
  只拉最近 30 天，重复的称重会被自动去重。要求 Android 9（API 28）及以上
- **数据自主** —— CSV 导出与导入、一键清空；全程不联网
- **轻** —— release 包 **1.71 MB**

## 技术栈

| | |
|---|---|
| 语言 / UI | Kotlin 2.3.20 · Jetpack Compose · Material 3 |
| 存储 | Room（记录）· DataStore（档案） |
| 架构 | 单向数据流（ViewModel + StateFlow）· 手写依赖容器 |
| 图表 | 自绘 Canvas |
| 版本 | minSdk 26（Android 8.0）· targetSdk 36 |

## 快速开始

```bash
./gradlew assembleDebug        # 构建
./gradlew testDebugUnitTest    # 单测（163 个）
./gradlew connectedDebugAndroidTest   # 设备上的 Room 迁移测试（要模拟器/真机）
./gradlew distRelease          # 出正式签名包 → dist/WeightDiary-<版本>.apk
```

「没有网络权限」这条约束是**脚本验证**的，不是靠自觉：`tools/verify-no-internet.ps1`
解析**合并后**的清单并断言没有 `INTERNET`（依赖的清单会合并进来，那是唯一的破口）。
本机执行策略若禁止运行未签名脚本，用 `-ExecutionPolicy Bypass` 跑。

需要 **JDK 17+** 与 **Android SDK（compileSdk 36）**。`local.properties` 里的 `sdk.dir` 指向你的
SDK 位置，该文件不在版本库里，各人自己配。

## 文档

| 文档 | 内容 |
|---|---|
| [开发手册](docs/开发手册.md) | 项目结构、里程碑、关键约定、出包流程 —— **想动手改代码先看这份** |
| [01 需求文档](docs/01-需求文档.md) | 产品定位、功能清单、非目标、验收标准 |
| [02 设计规范](docs/02-设计规范.md) | 设计 token、逐区块界面规格、状态、动效 |
| [03 技术设计](docs/03-技术设计.md) | 架构分层、数据模型、核心算法、风险清单 |
| [04 决策记录](docs/04-决策记录.md) | 全部决策与**被否原因** —— 想改设定之前先翻这里 |
| [05 交付计划](docs/05-交付计划.md) | 里程碑划分与出口标准 |
| [06 体脂秤接入调研](docs/06-体脂秤接入调研.md) | 为什么是 Health Connect（选型与被推翻的说法） |
| [08 Health Connect 同步设计](docs/08-Health Connect 同步设计.md) | 怎么接：数据模型、去重、过滤、已知限制 |

设计稿是**脚本渲染**的高保真图，不是手绘：改完设计 token 重跑 `docs/assets/render/` 下的脚本即可同步，
设计稿不会与文档脱节。

## License

[MIT](LICENSE) © 2026 collinszheng
