<#
  构建 → 装机 → 截图。用于把实现与 docs/assets 下的设计稿做像素级比对。

  用法：
    pwsh -File tools/screenshot.ps1                    复用已启动的模拟器
    pwsh -File tools/screenshot.ps1 -StartEmulator     没启动就先启动
    pwsh -File tools/screenshot.ps1 -Clear -Seed       清空数据后用调试种子造数
    pwsh -File tools/screenshot.ps1 -StopEmulator      截完关掉模拟器
    pwsh -File tools/screenshot.ps1 -Out foo.png       指定输出文件名

  注意：本机无法直连 services.gradle.org，脚本内置代理参数。
#>
param(
    [string]$Out = 'home.png',
    [switch]$Clear,
    [switch]$Seed,
    [double]$SeedWeight = 0,
    [switch]$StartEmulator,
    [switch]$StopEmulator,
    [switch]$SkipBuild
)

# 刻意用 Continue：adb 会把「Activity not started」这类提示写到 stderr，
# 而 PowerShell 5.1 在 ErrorActionPreference=Stop 下会把原生 stderr 当成终止错误。
$ErrorActionPreference = 'Continue'

$repo  = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

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
$emu   = Join-Path $sdk 'emulator\emulator.exe'
$avd   = if ($env:AVD_NAME) { $env:AVD_NAME } else { 'Medium_Phone_API_37.0' }
$pkg   = 'com.weightdiary.app'
$shotDir = Join-Path $repo 'build\screenshots'
$shotPath = Join-Path $shotDir $Out

# 代理是可选的：需要时先设 $env:GRADLE_PROXY，能直连 Gradle 仓库的环境不用设
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
if ($env:GRADLE_PROXY) { $env:GRADLE_OPTS = $env:GRADLE_PROXY }
$env:Path = "$sdk\platform-tools;$sdk\emulator;$env:Path"

function Get-Device {
    $lines = & $adb devices 2>$null
    ($lines | Where-Object { $_ -match '^emulator-\d+\s+device' } | Select-Object -First 1) -replace '\s+device.*$', ''
}

# ── 构建 ──
if (-not $SkipBuild) {
    Write-Host '── 构建 ──'
    Push-Location $repo
    & cmd.exe /c "gradlew.bat assembleDebug --no-daemon --console=plain $env:GRADLE_PROXY" 2>&1 |
        Select-String -Pattern 'BUILD |^e: |FAILURE' | Select-Object -First 20
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -ne 0) { throw "构建失败 (exit=$code)" }
}

# ── 确保有设备 ──
$device = Get-Device
if (-not $device -and $StartEmulator) {
    Write-Host '── 启动模拟器 ──'
    $proc = Start-Process -FilePath $emu -ArgumentList '-avd', $avd, '-no-boot-anim' -PassThru
    & $adb wait-for-device 2>$null
    $deadline = (Get-Date).AddMinutes(8)
    while ((Get-Date) -lt $deadline) {
        if ($proc.HasExited) { throw '模拟器提前退出' }
        if ((((& $adb shell getprop sys.boot_completed 2>$null) -join '').Trim()) -eq '1') { break }
        Start-Sleep -Seconds 5
    }
    $device = Get-Device
}
if (-not $device) { throw '没有可用设备。加 -StartEmulator，或先手动启动模拟器。' }
Write-Host "设备: $device"

# ── 安装 ──
Write-Host '── 安装 ──'
$apk = Join-Path $repo 'app\build\outputs\apk\debug\app-debug.apk'
& $adb -s $device install -r -t $apk 2>&1 | Select-String -Pattern 'Success|Failure' | Select-Object -First 3

# ── 可选：清空 / 造数 ──
if ($Clear) {
    Write-Host '── 清空应用数据 ──'
    & $adb -s $device shell pm clear $pkg 2>&1 | Out-Null
}
if ($Seed) {
    Write-Host '── 用调试种子造数 ──'
    # 必须先 force-stop：否则 intent 会被投递给已在运行的实例，
    # onCreate 不会重走，--ez debugSeed 就白传了
    $extra = @()
    if ($SeedWeight -gt 0) {
        $extra = @('--ef', 'debugWeight', "$SeedWeight")
        Write-Host "   覆盖最新体重为 $SeedWeight"
    }
    & $adb -s $device shell am force-stop $pkg 2>&1 | Out-Null
    Start-Sleep -Seconds 1
    & $adb -s $device shell am start -n "$pkg/.MainActivity" --ez debugSeed true @extra 2>&1 | Out-Null
    Start-Sleep -Seconds 3
}

# ── 启动并截图 ──
Write-Host '── 启动并截图 ──'
& $adb -s $device shell am force-stop $pkg 2>&1 | Out-Null
Start-Sleep -Seconds 1
& $adb -s $device shell am start -n "$pkg/.MainActivity" 2>&1 | Out-Null
Start-Sleep -Seconds 3
New-Item -ItemType Directory -Path $shotDir -Force | Out-Null
& $adb -s $device shell screencap -p /sdcard/_shot.png 2>&1 | Out-Null
& $adb -s $device pull /sdcard/_shot.png $shotPath 2>&1 | Select-String -Pattern 'pulled' | Select-Object -First 1

Write-Host "截图: $shotPath"

if ($StopEmulator) {
    Write-Host '── 关闭模拟器 ──'
    & $adb -s $device emu kill 2>&1 | Out-Null
}
