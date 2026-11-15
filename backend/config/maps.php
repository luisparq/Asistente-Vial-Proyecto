<?php

return [
    'style_url' => env('MAP_STYLE_URL', 'https://tiles.openfreemap.org/styles/liberty'),
    'dark_style_url' => env('MAP_DARK_STYLE_URL') ?: (env('LIVE_MAP_DARK_STYLE_URL') ?: 'https://tiles.openfreemap.org/styles/dark'),
    'live_recent_seconds' => max(1, (int) env('LIVE_MAP_RECENT_SECONDS', 120)),
    'live_poll_seconds' => max(15, (int) env('LIVE_MAP_POLL_SECONDS', 30)),
    'allowed_origins' => array_values(array_filter(explode(',', env('MAP_ALLOWED_ORIGINS', 'https://tiles.openfreemap.org')),
        fn ($origin) => preg_match('#^https://[a-zA-Z0-9.-]+(?::[0-9]+)?$#D', $origin))),
];
