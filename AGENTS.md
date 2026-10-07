# AGENTS.md

体重日记 —— 纯本地 Android 体重记录 App。Kotlin + Compose，图表自绘 Canvas，不联网。

这份文件是**给 AI 协作者的红线与索引**，每次会话自动加载。细节不要写在这里，写进 `docs/`。

## 先读什么

| 要做什么 | 先看 |
|---|---|
| **改任何设定之前** | [`docs/04-决策记录.md`](docs/04-决策记录.md) —— 大部分「为什么不那样做」都有答案和否掉的原因 |
| 写 UI | [`docs/02-设计规范.md`](docs/02-设计规范.md) —— 设计 token 与逐区块规格 |
| 写逻辑 | [`docs/03-技术设计.md`](docs/03-技术设计.md) —— 架构分层、数据模型、算法 |
| **做体脂秤 / Health Connect 接入** | [`docs/06-体脂秤接入调研.md`](docs/06-体脂秤接入调研.md) —— 方案已定、外部事实已核实，**别重新查一遍** |
| 上手整件事 | [`docs/开发手册.md`](docs/开发手册.md) —— 结构、约定、出包 |

## 红线

下面每一条都是**已经踩过**的，别重复。

1. **`tools/*.ps1` 必须带 UTF-8 BOM。**
   PowerShell 5.1 会把无 BOM 的 UTF-8 当 GBK 读，中文字节吞掉后面的字符导致语法错误。
   用脚本改完这些文件**一定要补回 BOM**（`UTF8Encoding($true)`），并立刻做语法检查：
   `[System.Management.Automation.Language.Parser]::ParseFile($path, [ref]$t, [ref]$e)`

2. **产物名一律英文。**
   中文文件名在 Windows / adb / 手机文件管理器之间转手会出现编码乱码 ——
   实测过 `体重日记-1.0.apk` 传成 `浣撻噸鏃ヨ-1`。APK、导出的 CSV 都用英文名。

3. **删改代码用整段精确匹配，不要按位置切片。**
   不要用「从某个字符串截到下一个 `}`」这类脚本 —— 它不理解嵌套，这个坑在
   `ChartScaffolder`、`PencilIconButton`、`tools/*.ps1` 上各踩过一次，每次都切掉了不该切的。
   删除代码块时，把首尾一起写进匹配串。

4. **改 R8 / proguard 规则后，必须实跑「写入一条记录并读回」。**
   Room 的生成类是按类名反射查找的，混淆掉就崩，**编译期发现不了**。

5. **表结构变更必须写 Migration。** 刻意不提供 `fallbackToDestructiveMigration`。

6. **`keystore/`、`keystore.properties`、`dist/` 不进版本库。**
   签名口令不能写进任何被跟踪的文件，也不能出现在提交历史里。

## 架构约定

- **Domain 层不依赖 Android** —— 所有算法是纯函数，可直接 JVM 单测
- **颜色、字号、间距不写死** —— 全部走 `ui/theme/` 的 token
- **BMI 是派生数据，不落库** —— 由体重 + 身高实时计算
- **数据一律以公制存储** —— 单位切换只是显示层的事

## 构建与验证

```bash
./gradlew assembleDebug        # 构建
./gradlew testDebugUnitTest    # 单测（130 个，改代码后必须全绿）
./gradlew distRelease          # 正式签名包 → dist/WeightDiary-<版本>.apk
```

需要代理时设 `$env:GRADLE_PROXY`。SDK / JDK 路径由 `tools/` 下的脚本自动探测，**不要写死本机路径**。

**Android 的验证回路**：`gradlew` → `adb install -r` → `am start` → `screencap` → 与设计稿比对。
`tools/screenshot.ps1`、`tools/verify-m2.ps1` 已把这条链路脚本化，优先用它们，别手搓 adb 序列。

两条验证纪律：

- **截图要用 `read_image` 真的看**，不要凭代码推断。点击错位、残留窗口这类问题只有看图才发现
- **不要把命令输出用 `Select-String 'Success'` 之类过滤掉失败信息** —— 曾因此漏掉
  `adb install` 的签名冲突静默失败，白测了一轮。要看完整输出，或至少同时匹配失败模式

## 仓库约定

- 提交信息用中文，写清楚**为什么改**以及**怎么验证的**
- 设计稿是脚本渲染的（`docs/assets/render/`），改完 token 要重跑脚本，不要手绘
- `README.md` 面向外部访客，保持简短；内部细节一律放 `docs/`
