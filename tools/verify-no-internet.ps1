<#
.SYNOPSIS
    断言合并后的 AndroidManifest 里没有 INTERNET 权限。

.DESCRIPTION
    「不联网」是本项目最硬的卖点（docs/06 §2.1）。唯一的破口是**合并后**的清单 ——
    依赖的清单会合并进来。所以这条约束必须机器化验证，不能靠肉眼看源码清单。

    **必须解析 `<uses-permission>` 元素，而不是在整份文件里搜 "INTERNET"。**
    清单的注释里完全可能出现这个词（本仓库的注释里就有，用来说明为什么不加它），
    在整份文件上做子串匹配会直接误报 —— 这个误报已经发生过一次。

.PARAMETER Manifest
    要检查的清单路径。不传就自己去 build/intermediates 下找合并后的清单。

.PARAMETER Variant
    构建变体，默认 debug。出正式包时用 release 再跑一遍。

.EXAMPLE
    ./tools/verify-no-internet.ps1
    ./tools/verify-no-internet.ps1 -Variant release

.NOTES
    本文件必须带 UTF-8 BOM（AGENTS 红线 #1）：PowerShell 5.1 会把无 BOM 的 UTF-8
    当 GBK 读，中文字节吞掉后面的字符导致语法错误。
#>
[CmdletBinding()]
param(
    [string]$Manifest,
    [string]$Variant = 'debug'
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$internetPermission = 'android.permission.INTERNET'

function Find-MergedManifest {
    param([string]$Name)

    $roots = @(
        (Join-Path $repoRoot "app\build\intermediates\merged_manifest\$Name"),
        (Join-Path $repoRoot "app\build\intermediates\merged_manifests\$Name")
    )

    foreach ($root in $roots) {
        if (-not (Test-Path $root)) { continue }
        $hit = Get-ChildItem -Path $root -Recurse -Filter 'AndroidManifest.xml' -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($hit) { return $hit.FullName }
    }
    return $null
}

if (-not $Manifest) {
    $Manifest = Find-MergedManifest -Name $Variant
}

if (-not $Manifest -or -not (Test-Path $Manifest)) {
    Write-Host "找不到合并后的清单（变体：$Variant）。先跑一次构建，例如：" -ForegroundColor Red
    Write-Host "    ./gradlew :app:processDebugMainManifest" -ForegroundColor Yellow
    exit 2
}

Write-Host "检查：$Manifest"

$raw = Get-Content -Raw -Encoding UTF8 $Manifest

# 去掉 XML 注释再判断。注释里出现 "INTERNET" 是合法的（那是在解释为什么没有它）
$withoutComments = [regex]::Replace(
    $raw,
    '<!--.*?-->',
    '',
    [System.Text.RegularExpressions.RegexOptions]::Singleline
)

$permissions = [regex]::Matches(
    $withoutComments,
    '<uses-permission[^>]*android:name\s*=\s*"([^"]+)"'
) | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique

Write-Host ''
Write-Host "清单里的权限（$($permissions.Count) 条）："
if ($permissions.Count -eq 0) {
    Write-Host '    （无）'
} else {
    $permissions | ForEach-Object { Write-Host "    $_" }
}
Write-Host ''

if ($permissions -contains $internetPermission) {
    Write-Host "❌ 合并后的清单里有 $internetPermission —— 这直接毁掉本项目的核心卖点。" -ForegroundColor Red
    Write-Host '   检查是不是新加的依赖把它带进来的（看 app/build/intermediates/merged_manifest 的来源标注）。' -ForegroundColor Red
    exit 1
}

Write-Host "✅ 没有 INTERNET 权限" -ForegroundColor Green
exit 0
