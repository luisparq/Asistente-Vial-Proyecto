@extends('dashboard.layout')
@section('title', 'Configuración')
@section('description', 'Personaliza tu cuenta y la forma en que Fleet muestra las notificaciones.')
@section('data-notice', '')
@section('content')
<div class="fleet-settings-grid">
    <form class="panel fleet-form fleet-settings-panel" action="{{ route('dashboard.settings.update') }}" method="post">
        @csrf
        @method('PUT')
        <section aria-labelledby="settings-profile">
            <h2 id="settings-profile">Perfil</h2>
            <label for="account-name">Nombre visible</label>
            <input id="account-name" type="text" name="name" maxlength="255" required autocomplete="name" value="{{ old('name', $account->name) }}">
            <small>Se muestra en Fleet y en el seguimiento de incidencias.</small>
        </section>
        <section aria-labelledby="settings-alerts">
            <h2 id="settings-alerts">Notificaciones del Resumen</h2>
            <label for="notification-interval">Actualización automática</label>
            <select id="notification-interval" name="fleet_notification_interval">
                @foreach([15 => 'Cada 15 segundos', 30 => 'Cada 30 segundos', 60 => 'Cada minuto', 0 => 'Al recargar la página'] as $seconds => $label)
                    <option value="{{ $seconds }}" @selected((string) old('fleet_notification_interval', $account->fleet_notification_interval ?? 15) === (string) $seconds)>{{ $label }}</option>
                @endforeach
            </select>
            <small>Se aplica a la lista de notificaciones recientes del Resumen.</small>
        </section>
        <div><button class="button primary">Guardar cambios</button></div>
    </form>
    <div>
        <section class="panel fleet-settings-panel">
            <h2>Apariencia</h2>
            <p>El tema se guarda en este navegador.</p>
            <div class="fleet-settings-theme" role="group" aria-label="Tema de Fleet" hidden>
                <button type="button" class="button quiet" data-fleet-theme="light" aria-pressed="false">Claro</button>
                <button type="button" class="button quiet" data-fleet-theme="dark" aria-pressed="false">Oscuro</button>
            </div>
        </section>
        <section class="panel fleet-settings-panel">
            <h2>Espacio de trabajo</h2>
            <dl class="fleet-settings-details">
                <div><dt>Empresa</dt><dd>{{ $account->company->name }}</dd></div>
                <div><dt>Correo de acceso</dt><dd>{{ $account->email }}</dd></div>
                <div><dt>Rol</dt><dd>Administrador de flota</dd></div>
                <div><dt>Zona horaria de Fleet</dt><dd>Lima · UTC−05:00</dd></div>
            </dl>
        </section>
    </div>
</div>
@endsection
