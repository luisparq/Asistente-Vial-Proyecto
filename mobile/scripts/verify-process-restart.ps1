param(
    [string]$Serial = 'emulator-5554',
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)
$ErrorActionPreference = 'Stop'
if ($Serial -notlike 'emulator-*') { throw 'Esta prueba debe ejecutarse en un emulador de pruebas.' }
$projectRoot = Split-Path -Parent $PSScriptRoot
$reportDir = Join-Path $projectRoot 'app/build/reports/process-restart'
New-Item -ItemType Directory -Force $reportDir | Out-Null
$package = 'com.tecsup.asistente.conduccion'
$runner = "$package.test/androidx.test.runner.AndroidJUnitRunner"

function Invoke-AdbChecked {
    param([string[]]$AdbArgs)
    $output = & $Adb -s $Serial @AdbArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($output -join "`n") }
    return $output
}

# Instalar sobre la aplicación conserva su base. No se usa pm clear ni uninstall.
Invoke-AdbChecked -AdbArgs @('install', '-r', (Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk'))
Invoke-AdbChecked -AdbArgs @('install', '-r', (Join-Path $projectRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))

$methods = @('aPrepareActiveTrip', 'bVerifyActiveAndFinish', 'cVerifyCompletedHistory')
foreach ($method in $methods) {
    Invoke-AdbChecked -AdbArgs @('shell', 'am', 'force-stop', $package)
    # pidof devuelve 1 cuando ya no existe el proceso: es el resultado esperado.
    $remaining = & $Adb -s $Serial shell pidof $package
    if ($remaining) { throw "El proceso sigue vivo: $remaining" }
    "Proceso detenido antes de $method" | Set-Content (Join-Path $reportDir "$method-process.txt")
    Invoke-AdbChecked -AdbArgs @('shell', 'am', 'start', '-W', '-n', "$package/.MainActivity")
    $result = Invoke-AdbChecked -AdbArgs @(
        'shell', 'am', 'instrument', '-w', '-r', '-e', 'class',
        "$package.RestartPersistenceTest#$method", $runner
    )
    $result | Set-Content (Join-Path $reportDir "$method.txt")
    $outputText = $result -join "`n"
    if ($outputText -notmatch 'OK \(1 test\)' -or $outputText -match 'FAILURES!!!|INSTRUMENTATION_FAILED') {
        throw "Falló ${method}: $outputText"
    }
    Write-Output "${method}: 1 prueba aprobada."
}
Write-Output "Persistencia verificada tras cierres de proceso sin borrar datos. Informes: $reportDir"
