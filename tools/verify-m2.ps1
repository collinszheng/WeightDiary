<#
  M2 交互验证：用 adb 真实点按走一遍录入流程，逐步截图。

  用法：
    pwsh -File tools/verify-m2.ps1              # 复用已启动的模拟器
    pwsh -File tools/verify-m2.ps1 -SkipBuild

  覆盖：
    首次引导 → 跳过 → 录入第一条 → 录入第二条（验变化量）→ 撤销 → 设身高（验 BMI 联动）
#>
param(
    [switch]$SkipBuild
)

# adb 会把提示写到 stderr，PowerShell 5.1 在 Stop 下会当成终止错误
$ErrorActionPreference = 'Continue'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sdk  = 'C:\Users\usr\AppData\Local\Android\Sdk'
$jbr  = 'E:\Android\Android Studio\jbr'
$adb  = Join-Path $sdk 'platform-tools\adb.exe'
$pkg  = 'com.weightdiary.app'
$proxy = '-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7897 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7897'
$shots = Join-Path $repo 'build\screenshots'

$env:JAVA_HOME = $jbr
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:GRADLE_OPTS = $proxy
$env:Path = "$sdk\platform-tools;$jbr\bin;$env:Path"

$device = ((& $adb devices 2>$null) | Where-Object { $_ -match '^emulator-\d+\s+device' } |
    Select-Object -First 1) -replace '\s+device.*$', ''
if (-not $device) { throw '没有可用设备，先启动模拟器' }
Write-Host "设备: $device"

function Tap([int]$x, [int]$y, [int]$waitMs = 900) {
    & $adb -s $device shell input tap $x $y 2>&1 | Out-Null
    Start-Sleep -Milliseconds $waitMs
}

function Shot([string]$name) {
    & $adb -s $device shell screencap -p /sdcard/_m2.png 2>&1 | Out-Null
    & $adb -s $device pull /sdcard/_m2.png (Join-Path $shots $name) 2>&1 |
        Select-String 'pulled' | Out-Null
    Write-Host "    截图 $name"
}

# 在弹窗里填一个数字字段：点字段 → 输入 → 收键盘（否则布局被顶上，保存按钮位置会变）
function FillNumber([int]$fieldX, [int]$fieldY, [string]$text) {
    Tap $fieldX $fieldY
    & $adb -s $device shell input text $text 2>&1 | Out-Null
    Start-Sleep -Milliseconds 600
    & $adb -s $device shell input keyevent KEYCODE_BACK 2>&1 | Out-Null
    Start-Sleep -Milliseconds 800
}

# 屏幕是 1080x2400 @420dpi（2.625 px/dp），以下坐标按实测标定
$ADD_BUTTON      = @(995, 134)
$SHEET_WEIGHT    = @(540, 1166)
$SHEET_SAVE      = @(540, 2187)
$SNACKBAR_UNDO   = @(950, 2266)
$GOAL_PENCIL     = @(486, 635)
$SHEET_HEIGHT    = @(540, 951)
$ONBOARDING_SKIP = @(540, 2249)

if (-not $SkipBuild) {
    Write-Host '── 构建 ──'
    Push-Location $repo
    & cmd.exe /c "gradlew.bat assembleDebug --no-daemon --console=plain $proxy" 2>&1 |
        Select-String -Pattern 'BUILD |^e: ' | Select-Object -First 10
    Pop-Location
}

Write-Host '── 安装并清空数据 ──'
$apk = Join-Path $repo 'app\build\outputs\apk\debug\app-debug.apk'
& $adb -s $device install -r -t $apk 2>&1 | Select-String 'Success|Failure'
& $adb -s $device shell pm clear $pkg 2>&1 | Out-Null
& $adb -s $device shell am start -n "$pkg/.MainActivity" 2>&1 | Out-Null
Start-Sleep -Seconds 4

Write-Host '── 1. 首次引导，跳过 ──'
Shot 'm2-01-onboarding.png'
Tap $ONBOARDING_SKIP[0] $ONBOARDING_SKIP[1] 1500
Shot 'm2-02-home-empty.png'

Write-Host '── 2. 录入第一条 68.5（+ → 输入 → 保存，共 3 步）──'
Tap $ADD_BUTTON[0] $ADD_BUTTON[1] 1500
FillNumber $SHEET_WEIGHT[0] $SHEET_WEIGHT[1] '68.5'
Shot 'm2-03-filled.png'
Tap $SHEET_SAVE[0] $SHEET_SAVE[1] 2000
Shot 'm2-04-saved-snackbar.png'
Start-Sleep -Seconds 10   # 等 Snackbar 超时消失，免得干扰后续

Write-Host '── 3. 录入第二条 67.5，验变化量 ──'
Tap $ADD_BUTTON[0] $ADD_BUTTON[1] 1500
FillNumber $SHEET_WEIGHT[0] $SHEET_WEIGHT[1] '67.5'
Tap $SHEET_SAVE[0] $SHEET_SAVE[1] 2500
Shot 'm2-05-two-records.png'

Write-Host '── 4. 撤销上一条 ──'
Tap $SNACKBAR_UNDO[0] $SNACKBAR_UNDO[1] 2000
Shot 'm2-06-after-undo.png'

Write-Host '── 5. 设身高 175，验 BMI 联动 ──'
Tap $GOAL_PENCIL[0] $GOAL_PENCIL[1] 1500
Shot 'm2-07-edit-profile.png'
FillNumber $SHEET_HEIGHT[0] $SHEET_HEIGHT[1] '175'
Tap $SHEET_SAVE[0] $SHEET_SAVE[1] 2000
Shot 'm2-08-bmi-shown.png'

Write-Host "`n完成，截图在 $shots"
