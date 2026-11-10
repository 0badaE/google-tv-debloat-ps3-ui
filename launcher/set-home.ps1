<#
.SYNOPSIS
  Installs Crossbar and makes sure Google TV is intact underneath. Then you turn on
  "Crossbar Home button" under Settings > Accessibility, and Home opens Crossbar.

  Nothing is disabled: Google TV stays the system Home, so its quick-settings panel (hold Home),
  YouTube button, recommendations and setup wizard keep working. If an earlier version of this
  script disabled Google TV or the setup wizard, this turns them back on.

.EXAMPLE
  .\set-home.ps1 -Device 192.168.1.50:5555
#>
param(
    [string]$Device,
    [string]$Apk = (Join-Path $PSScriptRoot 'build\Crossbar.apk')
)

. (Join-Path (Split-Path $PSScriptRoot -Parent) 'common.ps1')

$Launcher = 'com.google.android.apps.tv.launcherx'

Connect-Tv $Device
if (-not (Test-Path $Apk)) { throw "$Apk doesn't exist. Run .\build.ps1 first." }
Write-Host "Installing $Apk"
Invoke-Adb install -r $Apk | Select-Object -Last 1 | Write-Host

$disabled = Get-PackageSet @('-d')
foreach ($pkg in @($Launcher, 'com.google.android.tungsten.setupwraith')) {
    if ($disabled[$pkg]) {
        Write-Host "  $((Invoke-Adb shell pm enable --user 0 $pkg) -join ' ')"
        Write-ChangeLog 'restore' $pkg 'enabled'
        Start-Sleep -Seconds 2
    }
}
Invoke-Adb shell cmd role add-role-holder android.app.role.HOME $Launcher 0 | Out-Null

$enabled = (Invoke-Adb shell settings get secure enabled_accessibility_services) -join ''
if ($enabled -match 'com.gtvdebloat.crossbar/') {
    Write-Host 'Done. The Home button helper is already on.'
} else {
    Invoke-Adb shell am start -a android.settings.ACCESSIBILITY_SETTINGS | Out-Null
    Write-Host 'Last step, on the TV: Accessibility > "Crossbar Home button" > On.'
}
