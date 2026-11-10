# Shared helpers for debloat.ps1 and restore.ps1. Dot-source, don't run directly.

$ErrorActionPreference = 'Continue'
$Root = $PSScriptRoot
$BackupDir = Join-Path $Root 'backups'
$LogFile = Join-Path $BackupDir 'changes.log'

function Find-Adb {
    $local = Join-Path $Root 'tools\platform-tools\adb.exe'
    if (Test-Path $local) { return $local }
    $cmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "adb not found. Put Android platform-tools in .\tools\platform-tools or on your PATH (see README)."
}

$Adb = Find-Adb
$Serial = $null

# Runs adb against the selected device and returns trimmed output lines.
function Invoke-Adb {
    $argList = @()
    if ($script:Serial) { $argList += @('-s', $script:Serial) }
    $argList += $args
    # Windows PowerShell wraps stderr lines in ErrorRecords; unwrap them back to plain text.
    $out = & $script:Adb @argList 2>&1 | ForEach-Object {
        if ($_ -is [System.Management.Automation.ErrorRecord]) { $_.Exception.Message } else { "$_" }
    } | ForEach-Object { $_.TrimEnd("`r") } | Where-Object { $_ -and $_ -ne 'System.Management.Automation.RemoteException' }
    return $out
}

function Connect-Tv([string]$Device) {
    if ($Device) {
        $res = (& $script:Adb connect $Device 2>&1 | Out-String).Trim()
        if ($res -notmatch 'connected to|already connected') {
            throw "Could not connect to ${Device}: $res`nPair first: adb pair <ip>:<pairing-port> <code> (see README)."
        }
        $script:Serial = $Device
    }
    $devices = & $script:Adb devices | Select-Object -Skip 1 | Where-Object { $_ -match "`tdevice$" }
    if (-not $script:Serial) {
        if (@($devices).Count -ne 1) { throw "Expected exactly one connected device, found $(@($devices).Count). Pass -Device <ip:port>." }
        $script:Serial = ($devices -split "`t")[0]
    }
    $model = (Invoke-Adb shell getprop ro.product.model) -join ''
    $build = (Invoke-Adb shell getprop ro.build.id) -join ''
    Write-Host "Connected: $script:Serial ($model, build $build)"
}

function Get-PackageSet([string[]]$Flags) {
    $set = @{}
    foreach ($line in (Invoke-Adb shell pm list packages @Flags)) {
        if ($line -like 'package:*') { $set[$line.Substring(8)] = $true }
    }
    return $set
}

# Snapshot of every package and its state, so any run can be compared or undone by hand.
function Save-State([string]$Label) {
    New-Item -ItemType Directory -Force $BackupDir | Out-Null
    $all = Get-PackageSet @('-u')
    $installed = Get-PackageSet @()
    $disabled = Get-PackageSet @('-d')
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $file = Join-Path $BackupDir "state-$stamp-$Label.txt"
    $all.Keys | Sort-Object | ForEach-Object {
        $state = if (-not $installed[$_]) { 'removed-for-user' } elseif ($disabled[$_]) { 'disabled' } else { 'enabled' }
        "$_`t$state"
    } | Set-Content -Encoding utf8 $file
    Write-Host "Saved package state to $file"
}

function Write-ChangeLog([string]$Action, [string]$Package, [string]$Method) {
    New-Item -ItemType Directory -Force $BackupDir | Out-Null
    "$(Get-Date -Format s)`t$script:Serial`t$Action`t$Method`t$Package" | Add-Content -Encoding utf8 $LogFile
}

function Read-PackageList([string]$Path) {
    if (-not (Test-Path $Path)) { throw "List not found: $Path" }
    Get-Content $Path | ForEach-Object { ($_ -replace '#.*$', '').Trim() } | Where-Object { $_ }
}
