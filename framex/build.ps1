<#
.SYNOPSIS
  Builds frameXTV.apk with plain javac + d8 + aapt2 (no Gradle).

  Uses the same toolchain as the launcher, under ..\tools (see launcher/README.md):
    jdk-17*\                          Eclipse Temurin JDK 17
    android-sdk\build-tools\34.0.0\   Android build-tools 34
    android-sdk\platforms\android-34\ Android 14 platform (android.jar)

.EXAMPLE
  .\build.ps1                 # builds build\frameXTV.apk
  .\build.ps1 -VersionCode 2
#>
param([int]$VersionCode = 1, [string]$VersionName = '1.0')

$ErrorActionPreference = 'Stop'
$here = $PSScriptRoot
$tools = Join-Path (Split-Path $here -Parent) 'tools'
$jdk = Get-ChildItem $tools -Directory -Filter 'jdk-17*' | Select-Object -First 1
if (-not $jdk) { throw "JDK 17 not found in $tools" }
$bt = Join-Path $tools 'android-sdk\build-tools\34.0.0'
$androidJar = Join-Path $tools 'android-sdk\platforms\android-34\android.jar'
foreach ($p in @($bt, $androidJar)) { if (-not (Test-Path $p)) { throw "Missing $p" } }

$env:JAVA_HOME = $jdk.FullName
$env:PATH = "$($jdk.FullName)\bin;$bt;$env:PATH"

$out = Join-Path $here 'build'
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Force "$out\gen", "$out\classes", "$out\dex" | Out-Null

function Run([string]$exe) {
    # Tools like keytool report progress on stderr; only the exit code means failure.
    $ErrorActionPreference = 'Continue'
    & $exe @args 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "$exe failed ($LASTEXITCODE)" }
}

Write-Host '1/6 Compiling resources'
Run "$bt\aapt2.exe" compile --dir "$here\res" -o "$out\res.zip"
Run "$bt\aapt2.exe" link -I $androidJar --manifest "$here\AndroidManifest.xml" -o "$out\unsigned.apk" `
    -A "$here\assets" --java "$out\gen" --min-sdk-version 28 --target-sdk-version 34 `
    --version-code $VersionCode --version-name $VersionName "$out\res.zip"

Write-Host '2/6 Compiling Java'
$sources = @(Get-ChildItem -Recurse "$here\src", "$out\gen" -Filter *.java | ForEach-Object { $_.FullName })
Run javac -encoding UTF-8 -source 8 -target 8 '-Xlint:-options' -bootclasspath "$androidJar;$bt\core-lambda-stubs.jar" -d "$out\classes" @sources

Write-Host '3/6 Dexing'
$classes = @(Get-ChildItem -Recurse "$out\classes" -Filter *.class | ForEach-Object { $_.FullName })
Run "$bt\d8.bat" --release --min-api 28 --lib $androidJar --output "$out\dex" @classes

Write-Host '4/6 Packaging'
Push-Location "$out\dex"
try { Run "$bt\aapt.exe" add "$out\unsigned.apk" classes.dex | Out-Null } finally { Pop-Location }
Run "$bt\zipalign.exe" -f -p 4 "$out\unsigned.apk" "$out\aligned.apk"

Write-Host '5/6 Signing'
# A stable local key so updates install over the previous version. Keep it out of git.
$ks = Join-Path $here 'signing\framex.jks'
if (-not (Test-Path $ks)) {
    New-Item -ItemType Directory -Force (Split-Path $ks) | Out-Null
    Run keytool -genkeypair -keystore $ks -storepass framextv -keypass framextv -alias framex `
        -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=frameXTV" | Out-Null
}
Run "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:framextv --key-pass pass:framextv `
    --out "$out\frameXTV.apk" "$out\aligned.apk"

Write-Host '6/6 Verifying'
Run "$bt\apksigner.bat" verify "$out\frameXTV.apk"
$size = (Get-Item "$out\frameXTV.apk").Length / 1KB
Write-Host ("Built {0} ({1:N0} KB)" -f "$out\frameXTV.apk", $size)
