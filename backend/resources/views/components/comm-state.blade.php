@props(['state'])
{{-- Presenta el estado ya calculado por Device::communicationState() con texto, icono y color. --}}
<span class="badge {{ ['Comunicación reciente' => 'ok', 'Sin comunicación reciente' => 'warn'][$state] ?? 'idle' }}">{{ $state }}</span>
