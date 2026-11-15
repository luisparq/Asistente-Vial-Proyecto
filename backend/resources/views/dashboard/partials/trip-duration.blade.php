@if($trip->started_at && $trip->ended_at && $trip->ended_at->gte($trip->started_at))
    @php($duration = $trip->started_at->diff($trip->ended_at))
    @if($duration->days > 0){{ $duration->days }} d @endif{{ $duration->format('%H:%I:%S') }}
@endif
