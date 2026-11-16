@if($alert->test_only)
    <span class="badge simulated">Alerta de prueba</span>
@elseif($alert->reviewed_at)
    <span class="badge">Revisada</span>
@else
    <span class="badge">Pendiente de revisión</span>
@endif
