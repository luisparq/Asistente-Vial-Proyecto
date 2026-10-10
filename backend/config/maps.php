<?php

return [
    'style_url' => env('MAP_STYLE_URL', 'https://tiles.openfreemap.org/styles/liberty'),
    'allowed_origins' => array_values(array_filter(explode(',', env('MAP_ALLOWED_ORIGINS', 'https://tiles.openfreemap.org')),
        fn ($origin) => preg_match('#^https://[a-zA-Z0-9.-]+(?::[0-9]+)?$#D', $origin))),
];
