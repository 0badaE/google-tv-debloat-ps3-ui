<#
.SYNOPSIS
  Brings back apps hidden by debloat.ps1 (or any package you name).

.EXAMPLE
  .\restore.ps1 -Device 192.168.1.50:5555                                   # everything in the debloat list
  .\restore.ps1 -Device 192.168.1.50:5555 -Package com.google.android.katniss # just one app
#>
param(
    [string]$Device,
    [string]$List = (Join-Path $PSScriptRoot 'lists\debloat-list.txt'),
    [string[]]$Package,
    [switch]$DryRun
)

. (Join-Path $PSScriptRoot 'common.ps1')

Connect-Tv $Device
$packages = if ($Package) { $Package } else { @(Read-PackageList $List) }
Write-Host "Restoring $(@($packages).Count) packages$(if ($DryRun) { ' [dry run]' })"

if (-not $DryRun) { Save-State 'before-restore' }

$all = Get-PackageSet @('-u')
$installed = Get-PackageSet @()
$disabled = Get-PackageSet @('-d')
$summary = @{ restored = 0; skipped = 0; failed = 0 }

foreach ($pkg in $packages) {
    if (-not $all[$pkg]) { Write-Host "  skip      $pkg (not on this device)"; $summary.skipped++; continue }
    if ($installed[$pkg] -and -not $disabled[$pkg]) { Write-Host "  skip      $pkg (already enabled)"; $summary.skipped++; continue }
    if ($DryRun) { Write-Host "  would restore $pkg"; continue }

    $ok = $true
    if (-not $installed[$pkg]) {
        $out = (Invoke-Adb shell cmd package install-existing --user 0 $pkg) -join ' '
        if ($out -notmatch 'installed for user') { $ok = $false; Write-Host "  FAILED    $pkg : $out" -ForegroundColor Yellow }
    }
    if ($ok) {
        $out = (Invoke-Adb shell pm enable --user 0 $pkg) -join ' '
        if ($out -notmatch 'new state: (enabled|default)') { $ok = $false; Write-Host "  FAILED    $pkg : $out" -ForegroundColor Yellow }
    }
    if ($ok) {
        Write-Host "  restored  $pkg"
        Write-ChangeLog 'restore' $pkg 'enabled'
        $summary.restored++
    } else { $summary.failed++ }
}

Write-Host ""
Write-Host "Done. restored=$($summary.restored) skipped=$($summary.skipped) failed=$($summary.failed)"
