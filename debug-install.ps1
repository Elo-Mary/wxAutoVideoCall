#Requires -Version 5.1
<#
.SYNOPSIS
    编译 debug 包、装到已连接的手机上，并把应用的关键日志抓出来。

.DESCRIPTION
    这个脚本只为「开发阶段反复试」而存在。做的事：
      1) 用 gradlew 编译 debug 包；
      2) adb install -r 覆盖安装；
      3) 清一次 logcat 缓冲，然后只抓本应用与本应用相关标签的日志。

    步骤 3 是关键：本应用的问题现场大多在奶奶的手机上，开发时能复现的部分
    必须一次抓全，否则来回插拔数据线非常费时间。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File .\debug-install.ps1
.EXAMPLE
    # 只看日志，不重新编译
    powershell -ExecutionPolicy Bypass -File .\debug-install.ps1 -SkipBuild
#>
[CmdletBinding()]
param(
    # 跳过编译，直接把已有 APK 装上去看日志。
    [switch]$SkipBuild,
    # 抓日志的持续秒数；0 表示一直抓到按 Ctrl+C。
    [int]$WatchSeconds = 0,
    # 允许指定 SDK 路径，默认从 local.properties 读。
    [string]$SdkRoot,
    # 指定 JDK，默认用环境变量 JAVA_HOME。
    [string]$JavaHome
)

$ErrorActionPreference = 'Stop'
try {
    & chcp.com 65001 | Out-Null
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
}

$root = $PSScriptRoot
$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'

function Write-Step([string]$message) {
    Write-Host ""
    Write-Host "==> $message" -ForegroundColor Cyan
}

function Write-Ok([string]$message) {
    Write-Host "    $message" -ForegroundColor Green
}

# ---------------------------------------------------------------- 定位 SDK 与 adb
if (-not $SdkRoot) {
    $localProperties = Join-Path $root 'local.properties'
    if (Test-Path -LiteralPath $localProperties) {
        foreach ($line in (Get-Content -LiteralPath $localProperties)) {
            if ($line -match '^\s*sdk\.dir\s*=\s*(.+)$') {
                # local.properties 里的 Windows 路径是转义过的（E\:\\Android\\Sdk），要去转义。
                $SdkRoot = $Matches[1].Trim() -replace '\\\\', '\' -replace '\\:', ':'
            }
        }
    }
}
if (-not $SdkRoot) {
    if ($env:ANDROID_HOME) { $SdkRoot = $env:ANDROID_HOME }
    elseif ($env:ANDROID_SDK_ROOT) { $SdkRoot = $env:ANDROID_SDK_ROOT }
}
if (-not $SdkRoot -or -not (Test-Path -LiteralPath (Join-Path $SdkRoot 'platform-tools'))) {
    throw "没找到 Android SDK 或其中的 platform-tools。请用 -SdkRoot 指定，例如 -SdkRoot 'E:\Android\Sdk'"
}
$adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
Write-Ok "SDK : $SdkRoot"
Write-Ok "adb : $adb"

# ---------------------------------------------------------------- 编译
if (-not $SkipBuild) {
    Write-Step "编译 debug 包"
    if ($JavaHome) {
        $env:JAVA_HOME = $JavaHome
    }
    $gradlew = Join-Path $root 'gradlew.bat'
    & $gradlew assembleDebug
    if ($LASTEXITCODE -ne 0) {
        throw "编译失败（退出码 $LASTEXITCODE）"
    }
    Write-Ok "编译完成：$apk"
} else {
    Write-Step "按 -SkipBuild 要求跳过编译"
    if (-not (Test-Path -LiteralPath $apk)) {
        throw "没有找到已有的 APK：$apk（去掉 -SkipBuild 重新编译）"
    }
}

# ---------------------------------------------------------------- 设备
Write-Step "检查设备"
$devices = & $adb devices
$online = @($devices | Select-String -Pattern '^\S+\s+device$')
if ($online.Count -eq 0) {
    Write-Host "    没有检测到已授权的设备。请确认：" -ForegroundColor Yellow
    Write-Host "      * 手机已插上数据线，并且 USB 调试已打开；" -ForegroundColor Yellow
    Write-Host "      * 手机上弹出的「允许 USB 调试」已点同意（勾选一律允许）。" -ForegroundColor Yellow
    Write-Host "    当前设备列表：" -ForegroundColor Yellow
    $devices | ForEach-Object { Write-Host "      $_" }
    exit 1
}
$serial = ($online[0] -split '\s+')[0]
Write-Ok "设备：$serial"

# ---------------------------------------------------------------- 安装
Write-Step "安装（-r 覆盖安装，保留配置）"
& $adb -s $serial install -r $apk
if ($LASTEXITCODE -ne 0) {
    throw "安装失败（退出码 $LASTEXITCODE）。签名不一致时需要先卸载，见 -Help 说明。"
}
Write-Ok "安装完成"

# ---------------------------------------------------------------- 日志
Write-Step "抓日志"
Write-Host "    需要先手动点一次桌面图标拨号，才能看到自动化流程的日志。" -ForegroundColor Yellow
Write-Host "    按 Ctrl+C 结束。" -ForegroundColor Yellow

# 清一次缓冲，避免把上一次的现场混进来。
& $adb -s $serial logcat -c

# 标签白名单：只抓本应用自己写的日志，以及 Android 无障碍框架的报错。
$filter = @(
    'CallService:V', 'CallFlow:V', 'NodeUi:V', 'WeChatFinders:V',
    'WeChatLauncher:V', 'ForegroundApps:V', 'CallScreenInfo:V',
    'MainActivity:V', 'SettingsActivity:V', 'PermissionGuide:V',
    'Permissions:V', 'Prefs:V', 'FlowNotifier:V',
    'AndroidRuntime:E', 'AccessibilityManagerService:W',
    '*:S'
)
$logArgs = @('-s', $serial, 'logcat') + $filter

if ($WatchSeconds -gt 0) {
    $job = Start-Job -ScriptBlock {
        param($adbPath, $args2)
        & $adbPath @args2
    } -ArgumentList $adb, $logArgs
    Start-Sleep -Seconds $WatchSeconds
    Stop-Job $job
    Receive-Job $job
    Remove-Job $job -Force
} else {
    & $adb @logArgs
}
