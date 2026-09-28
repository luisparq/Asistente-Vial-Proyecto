<?php

namespace App\Support;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Http\Request;
use Illuminate\Pagination\Cursor;
use Illuminate\Support\Facades\Crypt;
use Throwable;

class ScopedCursor
{
    public function page(Request $request, Builder $query, callable $transform): array
    {
        $filters = $request->query();
        unset($filters['cursor']);
        ksort($filters);
        $user = $request->user();
        $scope = hash('sha256', json_encode([$request->path(), $user->company_id, $user->id,
            $user->permits('trips:read-company'), $filters], JSON_THROW_ON_ERROR));
        $cursor = null;
        if ($request->query('cursor') !== null) {
            try {
                $decoded = json_decode(Crypt::decryptString($request->query('cursor')), true, 8, JSON_THROW_ON_ERROR);
                if (! hash_equals($scope, $decoded['scope'])) {
                    throw new \RuntimeException;
                }
                $cursor = Cursor::fromEncoded($decoded['cursor']);
                if (! $cursor) {
                    throw new \RuntimeException;
                }
            } catch (Throwable) {
                throw new ApiProblem(422, 'invalid_cursor', 'Cursor inválido para esta consulta o identidad.');
            }
        }
        $page = $query->cursorPaginate((int) $request->query('limit', 25), ['*'], 'cursor', $cursor);
        $next = $page->nextCursor();
        if ($next) {
            // Cursor comparisons must use SQL values, not Carbon JSON serialization.
            // In particular, preserve milliseconds and the same representation as SQLite.
            $last = $page->getCollection()->last();
            $parameters = [];
            foreach ($query->getQuery()->orders as $order) {
                $parameters[$order['column']] = $last->getRawOriginal($order['column']);
            }
            $next = new Cursor($parameters, true);
        }

        return ['data' => $page->getCollection()->map($transform)->values()->all(),
            'next_cursor' => $next ? Crypt::encryptString(json_encode(['scope' => $scope, 'cursor' => $next->encode()], JSON_THROW_ON_ERROR)) : null];
    }
}
