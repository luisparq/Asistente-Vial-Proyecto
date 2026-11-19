<?php

namespace App\Http\Controllers;

use Illuminate\Http\Response;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

class TrafficFlowTileController extends Controller
{
    /**
     * Proxies TomTom Flow raster tiles so the provider key is never sent to a browser.
     */
    public function __invoke(int $z, int $x, int $y): Response
    {
        abort_unless($z >= 0 && $z <= 22 && $x >= 0 && $x < 2 ** $z && $y >= 0 && $y < 2 ** $z, 404);

        $key = config('services.tomtom.key');
        abort_unless(is_string($key) && trim($key) !== '', 503, 'Tráfico no configurado.');

        $cacheKey = "tomtom:flow:relative0:$z:$x:$y";
        $tile = Cache::get($cacheKey);
        if (!is_string($tile)) {
            try {
                $upstream = Http::connectTimeout(3)->timeout(8)->get(
                    "https://api.tomtom.com/traffic/map/4/tile/flow/relative0/$z/$x/$y.png",
                    ['key' => $key],
                );
            } catch (\Illuminate\Http\Client\ConnectionException) {
                Log::warning('TomTom tile connection failure');
                abort(503, 'Tráfico temporalmente indisponible.');
            }

            if (!$upstream->successful()) {
                Log::warning('TomTom tile HTTP failure', ['status' => $upstream->status()]);
            }
            abort_unless($upstream->successful(), 503, 'Tráfico temporalmente indisponible.');
            $tile = $upstream->body();
            Cache::put($cacheKey, $tile, now()->addMinutes(1));
        }

        return response($tile, 200, [
            'Content-Type' => 'image/png',
            'Cache-Control' => 'private, max-age=60',
        ]);
    }
}
