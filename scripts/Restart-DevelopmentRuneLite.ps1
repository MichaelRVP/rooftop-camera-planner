param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path -LiteralPath $RepoRoot).Path
$escapedRoot = [regex]::Escape($RepoRoot)

$owned = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -in @('java.exe', 'javaw.exe') -and
    $_.CommandLine -match $escapedRoot
})

foreach ($process in ($owned | Sort-Object ProcessId -Descending)) {
    Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
}

$deadline = (Get-Date).AddSeconds(10)
do {
    Start-Sleep -Milliseconds 200
    $remaining = @(Get-CimInstance Win32_Process | Where-Object {
        $_.Name -in @('java.exe', 'javaw.exe') -and
        $_.CommandLine -match $escapedRoot
    })
} while ($remaining.Count -gt 0 -and (Get-Date) -lt $deadline)

if ($remaining.Count -gt 0) {
    throw "Could not stop all development RuneLite processes for $RepoRoot"
}

$logPath = Join-Path $RepoRoot 'build\runelite-run.log'
$errorLogPath = Join-Path $RepoRoot 'build\runelite-run-error.log'
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $logPath) | Out-Null

$launcher = Start-Process `
    -FilePath (Join-Path $RepoRoot 'gradlew.bat') `
    -ArgumentList @('run') `
    -WorkingDirectory $RepoRoot `
    -WindowStyle Hidden `
    -RedirectStandardOutput $logPath `
    -RedirectStandardError $errorLogPath `
    -PassThru

$started = $false
$deadline = (Get-Date).AddSeconds(30)
do {
    Start-Sleep -Milliseconds 500
    $client = @(Get-CimInstance Win32_Process | Where-Object {
        $_.Name -in @('java.exe', 'javaw.exe') -and
        $_.CommandLine -match $escapedRoot -and
        $_.CommandLine -match 'RooftopCameraPluginTest'
    } | Select-Object -First 1)
    $started = $client.Count -gt 0
} while (-not $started -and (Get-Date) -lt $deadline -and -not $launcher.HasExited)

if (-not $started) {
    $details = @()
    if (Test-Path -LiteralPath $logPath) { $details += Get-Content -LiteralPath $logPath -Tail 30 }
    if (Test-Path -LiteralPath $errorLogPath) { $details += Get-Content -LiteralPath $errorLogPath -Tail 30 }
    throw "Development RuneLite did not start. $($details -join ' ')"
}

[ordered]@{
    ok = $true
    stoppedProcessIds = @($owned.ProcessId)
    launcherProcessId = $launcher.Id
    clientProcessId = $client[0].ProcessId
    logPath = $logPath
    errorLogPath = $errorLogPath
} | ConvertTo-Json -Depth 4
