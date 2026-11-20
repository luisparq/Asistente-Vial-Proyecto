<nav class="quick-actions" aria-label="Vista de dispositivos">
    <a class="button {{ $diagnosticsTab ? 'quiet' : 'primary' }}" href="{{ route(($global ?? false) ? 'platform.devices' : 'dashboard.devices') }}" @if(!$diagnosticsTab) aria-current="page" @endif>Estado de dispositivos</a>
    <a class="button {{ $diagnosticsTab ? 'primary' : 'quiet' }}" href="{{ route(($global ?? false) ? 'platform.devices.diagnostics' : 'dashboard.devices.diagnostics') }}" @if($diagnosticsTab) aria-current="page" @endif>Diagnóstico</a>
</nav>
