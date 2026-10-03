# 体重日记 (WeightDiary)

给个人用户使用的**纯本地**体重 / 体脂趋势记录 App。

| | |
|---|---|
| 包名 | `com.weightdiary.app` |
| 最低版本 | Android 8.0（minSdk 26） |
| compileSdk / targetSdk | 36 |
| 技术栈 | Kotlin 2.3.20 · Jetpack Compose · Room · DataStore · 图表自绘 Canvas |

## 当前进度

**M0 已完成**：项目骨架 + 数据层 + 核心算法与单测。

| 阶段 | 状态 |
|---|---|
| M0 项目骨架 + 数据层 | ✅ 完成 |
| M1 上半屏视觉 | ⏳ 未开始 |
| M2 录入流程 | ⏳ 未开始 |
| M3 图表（自绘 Canvas） | ⏳ 未开始 |
| M4 列表与记录管理 | ⏳ 未开始 |
| M5 退化场景与打磨 | ⏳ 未开始 |

> M0 的「能存能读、杀进程不丢」已在模拟器上实测通过：清空数据 → 冷启动显示 0 条 → 写入 1 条 → `am force-stop` → 重启后仍为 1 条。

## 文档

设计文档在本仓库之外，位于 **`E:\adev\docs\`**：

| 文档 | 内容 |
|---|---|
| [README](E:\adev\docs\README.md) | 文档地图与环境基线 |
| [01 需求文档](E:\adev\docs\01-需求文档.md) | 产品定位、功能清单、非目标、验收标准 |
| [02 设计规范](E:\adev\docs\02-设计规范.md) | 设计 token、界面规格、状态、动效 |
| [03 技术设计](E:\adev\docs\03-技术设计.md) | 架构、数据模型、核心算法、风险 |
| [04 决策记录](E:\adev\docs\04-决策记录.md) | 全部决策与**被否原因** |
| [05 交付计划](E:\adev\docs\05-交付计划.md) | 里程碑与出口标准 |

> ⚠️ **改任何设定之前先翻 04 决策记录。** 大部分「为什么不那样做」都有答案。

## 构建

```bash
# 本机 Java 直连 services.gradle.org 会超时，必须走代理
export GRADLE_OPTS="-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 \
                    -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897"

./gradlew assembleDebug          # 构建
./gradlew testDebugUnitTest      # 单测
```

Android Studio 若同步失败，检查 `Settings → HTTP Proxy`。详见 [03 技术设计 · R2](E:\adev\docs\03-技术设计.md)。

## 包结构

```
com.weightdiary.app
├─ WeightDiaryApp / MainActivity      ⚠️ MainActivity 目前是临时调试台，M1 会替换
├─ di/AppContainer                    手写依赖容器（暂不引入 Hilt）
├─ data/
│   ├─ local/       Room：Entity / Dao / Database / Mappers
│   ├─ prefs/       DataStore：ProfileStore
│   └─ repository/  WeightRepository
├─ domain/
│   ├─ model/       WeightRecord · UserProfile · BmiLevel · BmiStandard · Metric
│   └─ bmi/         BmiCalculator · BmiClassifier
└─ ui/              （M1 开始填充）

app/src/test/.../domain/bmi/   15 个单测
```

## 关键约定

- **BMI 是派生数据，不落库**，由体重 + 身高实时计算；改身高则历史 BMI 全部重算
- **Domain 层不依赖 Android**，所有算法是纯函数，可直接 JVM 单测
- **表结构变更必须写 Migration**，刻意不提供 `fallbackToDestructiveMigration`
- **数据一律以公制存储**，单位切换只是显示层的事
