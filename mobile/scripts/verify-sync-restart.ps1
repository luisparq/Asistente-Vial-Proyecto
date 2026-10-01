param([string]$Serial = 'emulator-5554', [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")
$ErrorActionPreference = 'Stop'
if ($Serial -notlike 'emulator-*') { throw 'Use a test emulator only.' }
$report = Join-Path (Split-Path -Parent $PSScriptRoot) 'app/build/reports/sync-process-restart'
New-Item -ItemType Directory -Force $report | Out-Null
foreach ($method in @('aPrepareQueue','bRecoverQueueAfterProcessRestart')) {
    & $Adb -s $Serial shell am force-stop com.tecsup.asistente.conduccion
    if ($LASTEXITCODE -ne 0) { throw 'Force-stop failed' }
    $result = & $Adb -s $Serial shell am instrument -w -r -e class "com.tecsup.asistente.conduccion.SyncRestartPersistenceTest#$method" com.tecsup.asistente.conduccion.test/androidx.test.runner.AndroidJUnitRunner
    $result | Set-Content (Join-Path $report "$method.txt")
    if (($result -join "`n") -notmatch 'OK \(1 test\)' -or ($result -join "`n") -match 'FAILURES!!!|INSTRUMENTATION_FAILED') { throw "Failed: $method" }
    Write-Output "${method}: passed with process stop before phase."
}
