<#
.SYNOPSIS
  Hides (disables) the apps in a debloat list on a Google TV over ADB. Nothing is deleted.

.EXAMPLE
  .\debloat.ps1 -Device 192.168.1.50:5555 -DryRun
  .\debloat.ps1 -Device 192.168.1.50:5555
#>
param(
    [string]$Device,
    [string]$List = (Join-Path $PSScriptRoot 'lists\debloat-list.txt'),
    [switch]$DryRun
)

. (Join-Path $PSScriptRoot 'common.ps1')

Connect-Tv $Device
$packages = @(Read-PackageList $List)
Write-Host "List: $List ($($packages.Count) packages)$(if ($DryRun) { ' [dry run]' })"

if (-not $DryRun) { Save-State 'before-debloat' }

$all = Get-PackageSet @('-u')
$installed = Get-PackageSet @()
$disabled = Get-PackageSet @('-d')
$summary = @{ hidden = 0; skipped = 0; failed = 0 }

foreach ($pkg in $packages) {
    if (-not $all[$pkg]) { Write-Host "  skip    $pkg (not on this device)"; $summary.skipped++; continue }
    if (-not $installed[$pkg] -or $disabled[$pkg]) { Write-Host "  skip    $pkg (already hidden)"; $summary.skipped++; continue }
    if ($DryRun) { Write-Host "  would hide $pkg"; continue }

    # Prefer disable-user: the app and its data stay on the device, it just stops running and disappears.
    $out = (Invoke-Adb shell pm disable-user --user 0 $pkg) -join ' '
    if ($out -match 'new state: disabled') {
        Write-Host "  hidden  $pkg (disabled)"
        Write-ChangeLog 'hide' $pkg 'disabled'
        $summary.hidden++
        continue
    }

    # Some protected packages refuse disable; removing for user 0 keeps the APK on the system partition.
    $out2 = (Invoke-Adb shell pm uninstall -k --user 0 $pkg) -join ' '
    if ($out2 -match 'Success') {
        Write-Host "  hidden  $pkg (removed for user, APK kept)"
        Write-ChangeLog 'hide' $pkg 'removed-for-user'
        $summary.hidden++
    } else {
        $reason = ($out -split "\sat com\.android")[0].Trim()
        Write-Host "  FAILED  $pkg : $reason | $out2" -ForegroundColor Yellow
        $summary.failed++
    }
}

Write-Host ""
Write-Host "Done. hidden=$($summary.hidden) skipped=$($summary.skipped) failed=$($summary.failed)"
if (-not $DryRun -and $summary.hidden -gt 0) { Write-Host "Undo any time with: .\restore.ps1 -Device $script:Serial" }
