# 体重日记 (WeightDiary)

给个人用户使用的**纯本地**体重 / 体脂趋势记录 App。

| | |
|---|---|
| 包名 | `com.weightdiary.app` |
| 最低版本 | Android 8.0（minSdk 26） |
| compileSdk / targetSdk | 36 |
| 技术栈 | Kotlin 2.3.20 · Jetpack Compose · Room · DataStore · 图表自绘 Canvas |

## 当前进度

| 阶段 | 状态 |
|---|---|
| M0 项目骨架 + 数据层 | ✅ 完成 |
| M1 上半屏视觉 | ✅ 完成 |
| M2 录入流程 | ✅ 完成 |
| M3 图表（自绘 Canvas） | ✅ 完成 |
| M4 列表与记录管理 | ✅ 完成 |
| M5 退化场景与打磨 | ✅ 完成 |


> M0 的「能存能读、杀进程不丢」已在模拟器上实测通过：清空数据 → 冷启动显示 0 条 → 写入 1 条 → `am force-stop` → 重启后仍为 1 条。
>
> M1 已与设计稿并排比对通过，并用 `tools/verify-m1.py` 量化校验了 token 色彩、无 elevation、等宽数字。
>
> M2 已用真实点按走完整流程验证（`tools/verify-m2.ps1`）：首次引导 → 跳过 → 录入 → 变化量 → 设身高后 BMI 联动。
>
> M3 图表为自绘 Canvas：区间解析 / 聚合（当日最低值锁定同一条记录）/ Y 轴整数刻度 / monotone cubic 平滑 / 点击气泡。横轴刻度按视图分类（日 `00:00–24:00`、周 `一–日`、月 `1/10/20/月末`、年 `1–12`），不再是一刀切的 5 个点。
>
> M4 列表与记录管理：首页历史区 + 「全部记录」弹窗 + 点击编辑 + 左滑显示红色删除按钮（左滑本身不删除，必须再点一下）。
> 首页历史区**不做**左滑，避免与页面纵向滚动打架；一次只允许一行处于划开状态。1000 条列表实测 Missed Vsync 0。
>
> M5 退化场景与打磨：空状态引导 + 插画、骨架屏、图表淡入淡出、目标达成脉冲、无障碍、字体 1.5× 适配。冷启动实测 1169–1212ms。
>
> M5 之后追加（产品逐轮反馈）：右下角悬浮「添加数据」按钮、日期栏箭头带底色表示可用性、目标卡片与概览卡片互换、新增整屏设置页（BMI 中国/WHO 标准、CSV 导入导出、清空数据）、自适应 App 图标。
> release 包开启 R8，12.85 MB → **1.55 MB**；Room 的反射初始化已在真机可用的构建上实测通过。

## 开发工具（tools/）

```powershell
# 构建 → 装机 → 造数 → 截图
pwsh -File tools/screenshot.ps1 -StartEmulator -Clear -Seed

# 用不同体重再截一张，用于「等宽数字」的 A/B 验证
pwsh -File tools/screenshot.ps1 -SkipBuild -Clear -Seed -SeedWeight 18.5 -Out home-w1.png

# 与设计稿并排比对
python tools/compare-design.py

# 量化校验 M1 出口标准
python tools/verify-m1.py build/screenshots/home-m1.png build/screenshots/home-m1-w1.png

# M2 交互验证：真实点按走一遍录入流程，逐步截图
pwsh -File tools/verify-m2.ps1
```

> ⚠️ `tools/*.ps1` 含中文，必须带 UTF-8 BOM —— PowerShell 5.1 会把无 BOM 的 UTF-8 当 GBK 读，
> 中文字节会吞掉后面的字符导致解析报错。**用编辑器改完这些脚本记得补回 BOM。**

## 文档

全部设计文档在 [`docs/`](docs/)，**改任何设定之前先翻 [`04-决策记录.md`](docs/04-决策记录.md)** —— 大部分「为什么不那样做」都有答案和否掉的原因。

| 文档 | 内容 |
|---|---|
| [README](docs/README.md) | 文档地图与环境基线 |
| [01 需求文档](docs/01-需求文档.md) | 产品定位、功能清单、非目标、验收标准 |
| [02 设计规范](docs/02-设计规范.md) | 设计 token、界面规格、状态、动效 |
| [03 技术设计](docs/03-技术设计.md) | 架构、数据模型、核心算法、风险 |
| [04 决策记录](docs/04-决策记录.md) | 全部决策与**被否原因** |
| [05 交付计划](docs/05-交付计划.md) | 里程碑与出口标准 |

### 设计稿

设计稿是**脚本渲染**的高保真图，不是手绘 —— 改完 token 重跑 `docs/assets/render/` 下的脚本即可同步，设计稿不会与文档脱节。

| 稿 | 文件 |
|---|---|
| 首页 | [`docs/assets/home.png`](docs/assets/home.png) |
| 弹窗与空状态 | [`docs/assets/sheets.png`](docs/assets/sheets.png) |
| 图表刻度对比 | [`docs/assets/chart-ticks.png`](docs/assets/chart-ticks.png) |
| 折线平滑对比 | [`docs/assets/chart-smooth.png`](docs/assets/chart-smooth.png) |

## 构建

```bash
# 本机 Java 直连 services.gradle.org 会超时，必须走代理
export GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 \
                    -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897"

./gradlew assembleDebug          # 构建
./gradlew testDebugUnitTest      # 单测（123 个）
./gradlew distRelease            # 出正式签名包 → dist/WeightDiary-<版本>.apk
```

Android Studio 若同步失败，检查 `Settings → HTTP Proxy`。详见 [03 技术设计 · R2](docs/03-技术设计.md)。

### 关于正式包

release 开启了 R8（混淆 + 资源压缩），**12.85 MB → 1.55 MB**。

> Room 的生成类是按类名反射查找的，开 R8 最容易在这一环翻车 ——
> 每次改完混淆规则都应在真机或模拟器上实跑一遍「写入一条记录并读回」。

`distRelease` 需要项目根的 `keystore.properties`（**不在版本库里**）：

```properties
storeFile=keystore/weightdiary-release.jks
storePassword=…
keyAlias=weightdiary
keyPassword=…
```

文件不存在时构建不会失败，只是不做签名、出不了可安装的正式包 —— 这样别人 clone 下来照样能跑 `assembleDebug`。

> **密钥库请单独备份。**丢了就再也发不了「同一个 App」的更新，只能换包名重来。

## 包结构

```
com.weightdiary.app
├─ WeightDiaryApp / MainActivity      入口 + 整屏导航（首页 ⇄ 设置）+ 弹窗路由
├─ di/AppContainer                    手写依赖容器（刻意不引入 Hilt）
├─ data/
│   ├─ local/       Room：Entity / Dao / Database / Mappers
│   ├─ prefs/       DataStore：ProfileStore
│   ├─ backup/      RecordBackup：CSV 的 SAF 读写
│   └─ repository/  WeightRepository
├─ domain/
│   ├─ model/       WeightRecord · UserProfile · BmiLevel · BmiStandard · Metric · UnitSystem
│   ├─ bmi/         BmiCalculator · BmiClassifier
│   ├─ chart/       RangeResolver · Aggregator · ChartScaffolder · MonotoneCubic
│   ├─ record/      RecordRows · RecordCsv
│   └─ validation/  RecordValidator
└─ ui/
    ├─ theme/       设计 token（颜色 / 字号 / 间距，全走 CompositionLocal）
    ├─ home/        首页：卡片、图表、历史区
    ├─ settings/    设置（整屏）
    ├─ sheet/       添加/编辑、个人资料、全部记录、首次引导
    └─ common/      Formatters · NumberInput

app/src/test/   123 个单测（BMI 12 · 图表 51 · 记录行 7 · 校验 13 · CSV 12 · 数字输入 8 …）
```

## 关键约定

- **BMI 是派生数据，不落库**，由体重 + 身高实时计算；改身高则历史 BMI 全部重算
- **Domain 层不依赖 Android**，所有算法是纯函数，可直接 JVM 单测
- **表结构变更必须写 Migration**，刻意不提供 `fallbackToDestructiveMigration`
- **数据一律以公制存储**，单位切换只是显示层的事
- **颜色、字号、间距不写死**，全部走 `ui/theme/` 的 token

## 本机环境注记

- **构建必须走代理**：Java 直连 `services.gradle.org` 超时，走 `127.0.0.1:7897` 才通
- **`android` CLI 的 `create` 子命令在本机不可用**：它会卡在自己的 `.sdk/lock` 上报错，即使放宽权限也一样；本项目是手工搭建的。遇到同类报错别再折腾权限，直接绕开或用 Android Studio
