<span class="fleet-avatar" aria-hidden="true">
    @if($driver->photo_path)
        <img src="{{ route('dashboard.drivers.photo', $driver->id) }}" alt="" loading="lazy" width="56" height="56">
    @else
        {{ mb_strtoupper(mb_substr($driver->full_name, 0, 1)) }}
    @endif
</span>
