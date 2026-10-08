<#
  M2 交互验证：用 adb 真实点按走一遍录入流程，逐步截图 **并逐步断言**。

  用法：
    pwsh -File tools/verify-m2.ps1
    pwsh -File tools/verify-m2.ps1 -SkipBuild
    pwsh -File tools/verify-m2.ps1 -Uninstall      # 先卸载再装（签名冲突时用，**会丢数据**）

  覆盖：
    首次引导 → 跳过 → 录入第一条 → 录入第二条（验变化量）→ 撤销 → 设身高（验 BMI 联动）

  ─────────────────────────────────────────────────────────────
  2026-10 重写。旧版为什么坏、以及这个版本怎么防：

  1. **不再写死像素坐标**，一律按 `text` / `content-desc` / `class`+序号现查现点。
     旧版把坐标硬编码在脚本里，M5 那次「添加数据」从顶栏搬到右下角悬浮按钮之后
     整体失效（`开发手册` §3 记着这次改动）：`ADD_BUTTON` 的 (995,134) 正好落在
     顶栏新来的设置齿轮上，于是整个流程变成「打开设置页 → 往空气里打字 → 点空白」。
     实测失效的还有 `GOAL_PENCIL`（差 254px）、`SNACKBAR_UNDO`（差 214px，且落进悬浮按钮）、
     `SHEET_SAVE`（差 17px，打在按钮上边缘之外）。

  2. **每步都断言，不只截图**。旧版只 `Shot`，于是它以 0 退出、打印「完成」，
     而那张名叫 `m2-04-saved-snackbar.png` 的截图实际是设置页、库里一条记录都没有。
     现在：找不到控件、条数不对、输入没读回，都会 `throw` → 退出码非 0。

  3. **install 失败要中止**。签名冲突时继续跑，等于对着旧二进制点按 ——
     而紧随其后的 `pm clear` 会把那个包的数据清掉。`07-真机测试清单` §1 早就写了
     「release 包与 debug 包签名不同，先卸载旧的再装」。

  注意：`tools/*.ps1` 必须带 UTF-8 BOM（AGENTS 红线 #1）。
#>
param(
    [switch]$SkipBuild,
    [switch]$Uninstall
)

# adb 会把提示写到 stderr，PowerShell 5.1 在 Stop 下会把它当成终止错误
$ErrorActionPreference = 'Continue'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

# SDK 一律自动探测，**不写死本机路径** ——
# 写死会让脚本换台机器就跑不起来，也会把本机用户名带进版本库。
# 优先级：环境变量 → local.properties 的 sdk.dir → 常见默认位置
function Resolve-AndroidSdk {
    foreach ($p in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)) {
        if ($p -and (Test-Path $p)) { return $p }
    }
    $lp = Join-Path $repo 'local.properties'
    if (Test-Path $lp) {
        $line = Select-String -Path $lp -Pattern '^\s*sdk\.dir\s*=\s*(.+?)\s*$' | Select-Object -First 1
        if ($line) {
            $dir = $line.Matches[0].Groups[1].Value -replace '\\\\', '\'
            if (Test-Path $dir) { return $dir }
        }
    }
    $guess = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path $guess) { return $guess }
    throw '找不到 Android SDK：请设置 ANDROID_SDK_ROOT，或在 local.properties 里写 sdk.dir'
}

$sdk   = Resolve-AndroidSdk
$adb   = Join-Path $sdk 'platform-tools\adb.exe'
$pkg   = 'com.weightdiary.app'
$shots = Join-Path $repo 'build\screenshots'

$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
if ($env:GRADLE_PROXY) { $env:GRADLE_OPTS = $env:GRADLE_PROXY }
$env:Path = "$sdk\platform-tools;$env:Path"

$device = ((& $adb devices 2>$null) | Where-Object { $_ -match '^emulator-\d+\s+device' } |
    Select-Object -First 1) -replace '\s+device.*$', ''
if (-not $device) { throw '没有可用设备，先启动模拟器' }
Write-Host "设备: $device"

# ─────────────────────────── UI 查询与点按 ───────────────────────────

$script:X = @{ Adb = $adb; Device = $device }

function Get-UiXml {
    & $script:X.Adb -s $script:X.Device shell uiautomator dump /sdcard/_verify.xml 2>&1 | Out-Null
    return ((& $script:X.Adb -s $script:X.Device shell cat /sdcard/_verify.xml 2>&1) -join "`n")
}

# 按 text / content-desc / class+序号 找控件，返回中心点。找不到返回 $null。
function Get-NodeCenter {
    param(
        [string]$Xml,
        [string]$Text,
        [string]$Desc,
        [string]$Class,
        [int]$ClassIndex = 0
    )
    $seen = -1
    foreach ($node in ($Xml -split '<node')) {
        $t  = [regex]::Match($node, 'text="([^"]*)"').Groups[1].Value
        $cd = [regex]::Match($node, 'content-desc="([^"]*)"').Groups[1].Value
        $cl = [regex]::Match($node, 'class="([^"]*)"').Groups[1].Value

        $hit = $false
        if ($Text)     { $hit = ($t -eq $Text) }
        elseif ($Desc) { $hit = ($cd -eq $Desc) }
        elseif ($Class) {
            if ($cl -eq $Class) {
                $seen++
                $hit = ($seen -eq $ClassIndex)
            }
        }
        if (-not $hit) { continue }

        $m = [regex]::Match($node, 'bounds="\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]"')
        if (-not $m.Success) { continue }
        return [pscustomobject]@{
            X = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
            Y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
        }
    }
    return $null
}

function Get-SelectorName {
    param([string]$Text, [string]$Desc, [string]$Class, [int]$ClassIndex)
    if ($Text)  { return "text=「$Text」" }
    if ($Desc)  { return "content-desc=「$Desc」" }
    return "class=$Class 第 $ClassIndex 个"
}

# 找不到控件就抛 —— 这正是旧版缺的东西：它点空了也照样往下走
function Tap {
    param(
        [string]$Text,
        [string]$Desc,
        [string]$Class,
        [int]$ClassIndex = 0,
        [int]$WaitMs = 900,
        [string]$Label
    )
    $name = Get-SelectorName -Text $Text -Desc $Desc -Class $Class -ClassIndex $ClassIndex
    $xml = Get-UiXml
    $p = Get-NodeCenter -Xml $xml -Text $Text -Desc $Desc -Class $Class -ClassIndex $ClassIndex
    if (-not $p) { throw "点不到控件：$name$(if ($Label) { "（$Label）" })" }

    Write-Host ("    点 {0} → ({1},{2})" -f $(if ($Label) { $Label } else { $name }), $p.X, $p.Y)
    & $script:X.Adb -s $script:X.Device shell input tap $p.X $p.Y 2>&1 | Out-Null
    Start-Sleep -Milliseconds $WaitMs
    return $p
}

function Shot {
    param([string]$Name)
    & $script:X.Adb -s $script:X.Device shell screencap -p /sdcard/_verify.png 2>&1 | Out-Null
    & $script:X.Adb -s $script:X.Device pull /sdcard/_verify.png (Join-Path $shots $Name) 2>&1 |
        Select-String 'pulled' | Out-Null
    Write-Host "    截图 $Name"
}

function Assert-Ui {
    param([string]$Pattern, [string]$Message, [string]$Xml)
    if (-not $Xml) { $Xml = Get-UiXml }
    if ($Xml -notmatch $Pattern) {
        throw "断言失败：$Message  —— 界面上找不到 /$Pattern/"
    }
    Write-Host "    ✓ $Message"
}

function Get-RecordCount {
    $xml = Get-UiXml
    $m = [regex]::Match($xml, '共 (\d+) 条')
    if ($m.Success) { return [int]$m.Groups[1].Value }
    if ($xml -match '还没有任何记录') { return 0 }
    throw '认不出记录条数：首页既没有「共 N 条」也没有空状态文案'
}

function Assert-RecordCount {
    param([int]$Expected, [string]$Message)
    $n = Get-RecordCount
    if ($n -ne $Expected) {
        throw "断言失败：$Message —— 期望 $Expected 条，实际 $n 条"
    }
    Write-Host "    ✓ $Message（$n 条）"
}

# 所有 EditText 的当前文本，按屏幕顺序
function Get-EditTextValues {
    $xml = Get-UiXml
    $vals = @()
    foreach ($node in ($xml -split '<node')) {
        if ([regex]::Match($node, 'class="([^"]*)"').Groups[1].Value -ne 'android.widget.EditText') { continue }
        $vals += [regex]::Match($node, 'text="([^"]*)"').Groups[1].Value
    }
    return , $vals
}

# 往第 N 个输入框打字，并**读回确认**。收键盘用 BACK（不收的话保存按钮会被键盘盖住）。
function Fill-EditText {
    param([int]$Index, [string]$Value, [string]$Label)
    $null = Tap -Class 'android.widget.EditText' -ClassIndex $Index -Label "$Label 输入框"
    & $script:X.Adb -s $script:X.Device shell input text $Value 2>&1 | Out-Null
    Start-Sleep -Milliseconds 600

    $vals = Get-EditTextValues
    if ($vals.Count -le $Index -or $vals[$Index] -ne $Value) {
        $got = if ($vals.Count -gt $Index) { $vals[$Index] } else { '(没有这个输入框)' }
        throw "断言失败：$Label 输入「$Value」后读回是「$got」"
    }
    Write-Host "    ✓ $Label = $Value（已读回）"

    # 收键盘。若这一下把弹窗也关了，紧随其后的断言会立刻暴露出来
    & $script:X.Adb -s $script:X.Device shell input keyevent KEYCODE_BACK 2>&1 | Out-Null
    Start-Sleep -Milliseconds 800
}

# ─────────────────────────── 构建与安装 ───────────────────────────

if (-not $SkipBuild) {
    Write-Host '── 构建 ──'
    Push-Location $repo
    & cmd.exe /c "gradlew.bat assembleDebug --console=plain $env:GRADLE_PROXY" 2>&1 |
        Select-String -Pattern 'BUILD |^e: ' | Select-Object -First 10
    Pop-Location
}

Write-Host '── 安装 ──'
$apk = Join-Path $repo 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $apk)) { throw "找不到 $apk，先跑一次 assembleDebug" }

if ($Uninstall) {
    Write-Host '    先卸载（与 release 包签名不同时必须；会丢掉该 App 的全部数据）'
    $uninstallOut = (& $adb -s $device uninstall $pkg 2>&1) -join "`n"
    Write-Host "    $uninstallOut"
}

# **必须把 adb 的输出原样打出来，并且看退出码。** 两件事都不能省：
#   - adb 的报错走 stderr，用 `2>&1 | ForEach-Object` 转发会被 PowerShell 的流语义吃掉，
#     所以先收进变量再 Write-Host（这个坑实际踩过一次：守卫触发了，但看不到原因）
#   - 签名冲突时若继续往下跑，就是对着旧二进制点按，而下面的 pm clear 还会把那个包的数据清掉
#
# 用 Out-String 而不是 -join：adb 的 stderr 会变成 ErrorRecord，
# 直接字符串拼接会多打一行没意义的 "System.Management.Automation.RemoteException"
$installOut = (& $adb -s $device install -r -t $apk 2>&1 | Out-String).Trim()
Write-Host "    $installOut"
$installCode = $LASTEXITCODE
if ($installCode -ne 0) {
    throw @'
安装失败，已中止（原因见上面 adb 那一行）。
若那是 INSTALL_FAILED_UPDATE_INCOMPATIBLE，说明设备上装的是 release 包（签名不同）。
两条路，自己选：
  - 加 -Uninstall 重跑（先卸载；该 App 的数据会被清掉）
  - 或者保留 release 包，别用这个脚本去测 debug 包
'@
}

Write-Host '── 清数据并启动 ──'
& $adb -s $device shell pm clear $pkg 2>&1 | Out-Null
& $adb -s $device shell am start -n "$pkg/.MainActivity" 2>&1 | Out-Null
Start-Sleep -Seconds 4

# ─────────────────────────── 1. 首次引导 ───────────────────────────

Write-Host '── 1. 首次引导，跳过 ──'
Assert-Ui -Pattern '先填一下身高' -Message '引导页出现'
Shot 'm2-01-onboarding.png'
$null = Tap -Text '跳过' -WaitMs 1500
Assert-Ui -Pattern '还没有任何记录' -Message '跳过引导后是空状态'
Assert-RecordCount -Expected 0 -Message '初始没有记录'
Shot 'm2-02-home-empty.png'

# ─────────────────────────── 2. 录入第一条 ───────────────────────────

Write-Host '── 2. 录入第一条 68.5（+ → 输入 → 保存，共 3 步）──'
$null = Tap -Desc '添加数据' -WaitMs 1500 -Label '右下角悬浮按钮'
Assert-Ui -Pattern '日期时间' -Message '添加数据弹窗打开'
$null = Fill-EditText -Index 0 -Value '68.5' -Label '体重'
Shot 'm2-03-filled.png'
$null = Tap -Text '保存' -WaitMs 2500
Assert-RecordCount -Expected 1 -Message '保存后新增一条'
Assert-Ui -Pattern '已记录 68\.5 kg' -Message 'Snackbar 报出刚记的体重'
Assert-Ui -Pattern '撤销' -Message 'Snackbar 带撤销入口'
Shot 'm2-04-saved-snackbar.png'

# ─────────────────────────── 3. 录入第二条，验变化量 ───────────────────────────

Write-Host '── 3. 录入第二条 67.5，验变化量 ──'
$null = Tap -Desc '添加数据' -WaitMs 1500 -Label '右下角悬浮按钮'
Assert-Ui -Pattern '日期时间' -Message '弹窗再次打开'
$null = Fill-EditText -Index 0 -Value '67.5' -Label '体重'
$null = Tap -Text '保存' -WaitMs 2500
Assert-RecordCount -Expected 2 -Message '两条记录'
Assert-Ui -Pattern '↓ 1\.0' -Message '变化量显示 ↓ 1.0'
Shot 'm2-05-two-records.png'

# ─────────────────────────── 4. 撤销上一条 ───────────────────────────

Write-Host '── 4. 撤销上一条 ──'
$null = Tap -Text '撤销' -WaitMs 2500 -Label 'Snackbar 上的撤销'
Assert-RecordCount -Expected 1 -Message '撤销后回到一条'
Shot 'm2-06-after-undo.png'

# ─────────────────────────── 5. 设身高，验 BMI 联动 ───────────────────────────

Write-Host '── 5. 设身高 175，验 BMI 联动 ──'
Assert-Ui -Pattern 'BMI --' -Message '设身高前 BMI 是 --'
$null = Tap -Desc '编辑个人资料' -WaitMs 1500
Assert-Ui -Pattern '身高（厘米）' -Message '编辑个人资料弹窗打开'
Shot 'm2-07-edit-profile.png'
$null = Fill-EditText -Index 0 -Value '175' -Label '身高'
$null = Tap -Text '保存' -WaitMs 2500
Assert-Ui -Pattern 'BMI \d' -Message '设身高后 BMI 出现数字'
Shot 'm2-08-bmi-shown.png'

Write-Host ''
Write-Host "全部断言通过。截图在 $shots" -ForegroundColor Green
