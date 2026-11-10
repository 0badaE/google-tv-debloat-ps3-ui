<#
.SYNOPSIS
  Goes back to plain Google TV. Turning off "Crossbar Home button" in Settings > Accessibility does
  the same without a computer; this also uninstalls Crossbar with -Uninstall, and re-enables Google TV
  and its setup wizard in case an older version of set-home.ps1 disabled them.

.EXAMPLE
  .\restore-home.ps1 -Device 192.168.1.50:5555 -Uninstall
#>
param(
    [string]$Device,
    [switch]$Uninstall
)

. (Join-Path (Split-Path $PSScriptRoot -Parent) 'common.ps1')

$Launcher = 'com.google.android.apps.tv.launcherx'

Connect-Tv $Device
$disabled = Get-PackageSet @('-d')
foreach ($pkg in @($Launcher, 'com.google.android.tungsten.setupwraith')) {
    if ($disabled[$pkg]) {
        Write-Host "  $((Invoke-Adb shell pm enable --user 0 $pkg) -join ' ')"
        Write-ChangeLog 'restore' $pkg 'enabled'
    }
}
Invoke-Adb shell cmd role add-role-holder android.app.role.HOME $Launcher 0 | Out-Null
if ($Uninstall) {
    Invoke-Adb uninstall com.gtvdebloat.crossbar | Write-Host
} else {
    Write-Host 'Google TV is Home again once you turn off "Crossbar Home button" in Settings > Accessibility.'
}
