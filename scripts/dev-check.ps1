param(
    [ValidateSet("fast", "apk", "release-gate")]
    [string]$Mode = "fast",
    [string[]]$AppTests = @(
        "br.com.projetoia.app.data.ai.ModelProbeTest",
        "br.com.projetoia.app.data.ai.claude.OpenRouterClaudeGatewayTest",
        "br.com.projetoia.app.data.ai.tools.WorkspaceToolsTest"
    ),
    [int]$TimeoutSeconds = 600,
    [switch]$AllowDirty,
    [switch]$KeepDaemon
)

$ErrorActionPreference = "Stop"
$stopwatch = [System.Diagnostics.Stopwatch]::StartNew()

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    if (-not $env:JAVA_HOME -and (Test-Path "D:\java")) { $env:JAVA_HOME = "D:\java" }
    if (-not $env:ANDROID_HOME -and (Test-Path "D:\Android\Sdk")) { $env:ANDROID_HOME = "D:\Android\Sdk" }
    if (-not $env:ANDROID_SDK_ROOT -and $env:ANDROID_HOME) { $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME }
    if (-not $env:GRADLE_USER_HOME -and (Test-Path "D:\Android\.gradle")) { $env:GRADLE_USER_HOME = "D:\Android\.gradle" }

    function Invoke-TimedProcess {
        param(
            [string]$Label,
            [string]$FilePath,
            [string[]]$ArgumentList,
            [int]$Seconds
        )
        Write-Host "`n==> $Label (timeout: ${Seconds}s)" -ForegroundColor Cyan
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $FilePath
        $psi.Arguments = ($ArgumentList | ForEach-Object {
            if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ }
        }) -join ' '
        $psi.WorkingDirectory = $repoRoot
        $psi.UseShellExecute = $false
        $proc = [System.Diagnostics.Process]::Start($psi)
        if (-not $proc.WaitForExit($Seconds * 1000)) {
            try { $proc.Kill($true) } catch {}
            throw "$Label excedeu o timeout de ${Seconds}s."
        }
        if ($proc.ExitCode -ne 0) {
            throw "$Label falhou com exit code $($proc.ExitCode)."
        }
    }

    function Run-BootstrapValidation {
        $python = Get-Command python -ErrorAction SilentlyContinue
        if (-not $python) { throw "Python nao encontrado no PATH para validar bootstrap.py." }
        $script = 'import pathlib; code = pathlib.Path("app/src/main/resources/projetoia-claude/bootstrap.py").read_text(encoding="utf-8"); compile(code, "bootstrap.py", "exec"); assert "--session-id" in code and "--resume" in code; print("BOOTSTRAP_SYNTAX_OK")'
        Invoke-TimedProcess -Label "Validacao rapida bootstrap.py" -FilePath $python.Source -ArgumentList @("-I", "-S", "-c", $script) -Seconds 60
    }

    $gradleBaseArgs = @(
        "-x", ":web:buildWebUi",
        "--build-cache",
        "--max-workers=2",
        "-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8",
        "--console=plain"
    )
    if (-not $KeepDaemon) {
        $gradleBaseArgs += "--no-daemon"
    }

    if ($Mode -eq "fast") {
        Run-BootstrapValidation
        $testArgs = @(":ai:testDebugUnitTest", ":common:testDebugUnitTest", ":app:compileGithubDebugKotlin", ":app:compileGithubDebugUnitTestKotlin", ":app:testGithubDebugUnitTest")
        foreach ($pattern in $AppTests) {
            $testArgs += @("--tests", $pattern)
        }
        Invoke-TimedProcess -Label "Testes rapidos JVM no Windows (:ai, :common, :app filtrado)" -FilePath ".\gradlew.bat" -ArgumentList ($testArgs + $gradleBaseArgs) -Seconds $TimeoutSeconds
    } elseif ($Mode -eq "apk") {
        Invoke-TimedProcess -Label "Compilacao local APK Debug" -FilePath ".\gradlew.bat" -ArgumentList (@("assembleGithubDebug") + $gradleBaseArgs) -Seconds $TimeoutSeconds
    } elseif ($Mode -eq "release-gate") {
        if (-not $AllowDirty) {
            $dirty = git status --short --untracked-files=no
            if ($dirty) {
                throw "Existem alteracoes rastreadas nao commitadas antes do release-gate. Commit antes de validar o APK final ou use -AllowDirty para teste local."
            }
        }
        $headSha = (git rev-parse HEAD).Trim()
        Write-Host "Commit alvo para validacao e APK: $headSha" -ForegroundColor Yellow

        Run-BootstrapValidation
        $testArgs = @(":ai:testDebugUnitTest", ":common:testDebugUnitTest", ":app:testGithubDebugUnitTest")
        foreach ($pattern in $AppTests) {
            $testArgs += @("--tests", $pattern)
        }
        Invoke-TimedProcess -Label "Suite Windows pré-distribuicao (:ai, :common, :app filtrado)" -FilePath ".\gradlew.bat" -ArgumentList ($testArgs + $gradleBaseArgs) -Seconds $TimeoutSeconds
        Invoke-TimedProcess -Label "Build local APK Debug (parity check)" -FilePath ".\gradlew.bat" -ArgumentList (@("assembleGithubDebug") + $gradleBaseArgs) -Seconds $TimeoutSeconds

        Write-Host "`nRelease-gate local concluido para commit $headSha." -ForegroundColor Green
        Write-Host "Proximo passo para distribuicao oficial (assinatura CI + testes POSIX): git push / workflow_dispatch no GitHub Actions." -ForegroundColor Green
    }
    Write-Host ("`n[OK] dev-check (-Mode {0}) concluido em {1:N1}s." -f $Mode, $stopwatch.Elapsed.TotalSeconds) -ForegroundColor Green
} finally {
    Pop-Location
}
