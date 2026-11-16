$ErrorActionPreference = "Stop"

Write-Host "=== Ren IA Alpha 16 - Validacao Reproduzivel ===" -ForegroundColor Green

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\java.exe"
    if (Test-Path $candidate) {
        $env:PATH = "$(Join-Path $env:JAVA_HOME 'bin');$env:PATH"
        $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    }
}

if (-not $javaCmd) {
    Write-Error "JDK 21 nao encontrado no PATH nem em JAVA_HOME. Instale o JetBrains Runtime 21 ou Temurin 21 e defina JAVA_HOME."
}

$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }
if (-not (Test-Path $sdkRoot)) {
    Write-Error "Android SDK nao encontrado em '$sdkRoot'. Defina ANDROID_HOME apontando para o Android SDK (compileSdk 37.2, NDK 29.0.14206865)."
}
$env:ANDROID_HOME = $sdkRoot

Write-Host "1. Executando testes unitarios JVM..." -ForegroundColor Cyan
& .\gradlew.bat testGithubDebugUnitTest :ai:testDebugUnitTest :workspace:testDebugUnitTest :common:testDebugUnitTest -x :web:buildWebUi

Write-Host "2. Compilando APK instalavel (assembleGithubDebug)..." -ForegroundColor Cyan
& .\gradlew.bat assembleGithubDebug -x :web:buildWebUi

Write-Host "Concluido! APKs gerados em app\build\outputs\apk\github\debug\" -ForegroundColor Green
