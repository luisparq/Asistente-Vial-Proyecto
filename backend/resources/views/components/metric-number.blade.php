@props(['value'])
<strong class="metric-number">
    <span class="metric-value">{{ number_format($value, 0, ',', '.') }}</span>
    @if($value > 0 && $value < 1000)
        <span class="metric-count" aria-hidden="true" data-count="{{ $value }}"></span>
    @endif
</strong>
