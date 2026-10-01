<?php

return [
    'stateful' => [],
    // API remains Bearer-only; dashboard sessions use the separate web guard.
    'guard' => [],
    'expiration' => 43200,
    'token_prefix' => '',
    'middleware' => [],
];
