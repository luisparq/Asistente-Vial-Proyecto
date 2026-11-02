@props(['name' => 'index'])
@php
    $paths = [
        'index' => 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
        'analytics' => 'M4 20V10 M10 20V4 M16 20v-8 M22 20H2',
        'companies' => 'M4 21V5h10v16 M14 10h6v11 M8 9h2 M8 13h2 M8 17h2 M2 21h20',
        'accounts' => 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M22 21v-2a4 4 0 0 0-3-3.87',
        'drivers' => 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M22 21v-2a4 4 0 0 0-3-3.87',
        'profile' => 'M20 21v-2a7 7 0 0 0-14 0v2 M13 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8',
        'vehicles' => 'M5 17H3V8l2-4h14l2 4v9h-2 M5 17h14 M3 9h18 M6 13h2 M16 13h2 M5 17v3h3v-3 M16 17v3h3v-3',
        'devices' => 'M8 3h8v18H8z M11 17h2 M3 8v8 M21 8v8',
        'support' => 'M3 14v-3a9 9 0 0 1 18 0v3 M3 12h4v7H3z M17 12h4v7h-4z M17 19a5 5 0 0 1-5 3',
        'reports' => 'M5 3h10l4 4v14H5z M14 3v5h5 M8 12h8 M8 16h6',
        'audits' => 'M12 3 3 7v5c0 5 9 9 9 9s9-4 9-9V7z M8 12l3 3 5-6',
        'trips' => 'M5 4v12a4 4 0 0 0 8 0V8a4 4 0 0 1 8 0v12 M2 4h6 M18 20h6',
        'events' => 'M12 3 2 21h20z M12 9v5 M12 17h.01',
        'map' => 'M9 3 3 6v15l6-3 6 3 6-3V3l-6 3z M9 3v15 M15 6v15',
        'settings' => 'M4 7h16 M4 17h16 M8 4v6 M16 14v6',
    ];
@endphp
<svg class="nav-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="{{ $paths[$name] ?? $paths['index'] }}"/></svg>
