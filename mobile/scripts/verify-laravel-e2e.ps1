param(
    [string]$Serial = 'emulator-5554',
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$Php = 'C:\xampp\php\php.exe'
)
$ErrorActionPreference = 'Stop'
if ($Serial -notlike 'emulator-*') { throw 'Use a test emulator only.' }
$mobile = Split-Path -Parent $PSScriptRoot
$backend = Join-Path (Split-Path -Parent $mobile) 'backend'
$report = Join-Path $mobile 'app/build/reports/laravel-e2e'
New-Item -ItemType Directory -Force $report | Out-Null
& $Adb -s $Serial install -r (Join-Path $mobile 'app/build/outputs/apk/debug/app-debug.apk')
if ($LASTEXITCODE -ne 0) { throw 'App installation failed' }
& $Adb -s $Serial install -r (Join-Path $mobile 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')
if ($LASTEXITCODE -ne 0) { throw 'Test installation failed' }
$database = Join-Path $backend ('storage/framework/testing/android-e2e-' + [guid]::NewGuid().ToString() + '.sqlite')
$fixture = Join-Path $report 'fixture.json'
$helper = Join-Path $PSScriptRoot 'laravel-e2e-fixture.php'
& $Php $helper setup $database $fixture
if ($LASTEXITCODE -ne 0) { throw 'Fixture setup failed' }
$credentials = Get-Content $fixture -Raw | ConvertFrom-Json
$savedEnvironment = @{}
foreach ($key in @('DB_CONNECTION','DB_DATABASE','APP_ENV','CACHE_STORE','SESSION_DRIVER')) { $savedEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
$server = $null
try {
    $env:DB_CONNECTION = 'sqlite'
    $env:DB_DATABASE = $database
    $env:APP_ENV = 'testing'
    $env:CACHE_STORE = 'file'
    $env:SESSION_DRIVER = 'array'
    $server = Start-Process -FilePath $Php -ArgumentList @('-S','127.0.0.1:18888','-t','public','public/index.php') -WorkingDirectory $backend -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $report 'server.stdout.log') -RedirectStandardError (Join-Path $report 'server.stderr.log')
    $ready = $false
    for ($i = 0; $i -lt 25; $i++) {
        try { $null = Invoke-WebRequest 'http://127.0.0.1:18888/' -TimeoutSec 2; $ready = $true; break } catch { Start-Sleep -Milliseconds 200 }
    }
    if (-not $ready -or $server.HasExited) { throw 'Isolated server did not start' }
    & $Adb -s $Serial reverse tcp:18888 tcp:18888
    if ($LASTEXITCODE -ne 0) { throw 'ADB reverse failed' }
    $result = & $Adb -s $Serial shell am instrument -w -r -e class com.tecsup.asistente.conduccion.LaravelEndToEndTest -e laravelE2E true -e baseUrl http://127.0.0.1:18888/ -e testEmail $credentials.email -e testPassword $credentials.password com.tecsup.asistente.conduccion.test/androidx.test.runner.AndroidJUnitRunner
    $result | Set-Content (Join-Path $report 'instrumentation.txt')
    if (($result -join "`n") -notmatch 'OK \(1 test\)' -or ($result -join "`n") -match 'FAILURES!!!|INSTRUMENTATION_FAILED') { throw 'Real Android HTTP test failed; see instrumentation.txt' }
    $verification = & $Php $helper verify $database
    if ($LASTEXITCODE -ne 0) { throw 'SQLite verification failed' }
    $verification | Set-Content (Join-Path $report 'sqlite-verification.json')
    $verification
    Write-Output "Real Android to Laravel HTTP test passed. Reports: $report"
} finally {
    if ($null -ne $server -and -not $server.HasExited) { Stop-Process -Id $server.Id }
    & $Adb -s $Serial reverse --remove tcp:18888 2>$null
    foreach ($key in $savedEnvironment.Keys) { [Environment]::SetEnvironmentVariable($key, $savedEnvironment[$key], 'Process') }
    if (Test-Path -LiteralPath $fixture) { Remove-Item -LiteralPath $fixture }
    # Retain only the isolated TEST SQLite file for inspection; no local development data touched.
}
