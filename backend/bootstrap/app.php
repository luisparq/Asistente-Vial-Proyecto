<?php

use App\Support\ApiProblem;
use Illuminate\Auth\AuthenticationException;
use Illuminate\Foundation\Application;
use Illuminate\Foundation\Configuration\Exceptions;
use Illuminate\Foundation\Configuration\Middleware;
use Illuminate\Http\Request;
use Illuminate\Validation\ValidationException;
use Symfony\Component\HttpKernel\Exception\HttpExceptionInterface;

return Application::configure(basePath: dirname(__DIR__))
    ->withRouting(
        web: __DIR__.'/../routes/web.php',
        api: __DIR__.'/../routes/api.php',
        commands: __DIR__.'/../routes/console.php',
        health: '/up',
    )
    ->withMiddleware(function (Middleware $middleware) {
        $middleware->redirectGuestsTo(fn (Request $request) => $request->is('api/*') ? null : route($request->is('platform*') ? 'platform.login' : 'login'));
        $middleware->trimStrings(except: [fn ($request) => $request->is('api/*')]);
        $middleware->convertEmptyStringsToNull(except: [fn ($request) => $request->is('api/*')]);
    })
    ->withExceptions(function (Exceptions $exceptions) {
        $exceptions->dontFlash(['activation_code']);
        $exceptions->shouldRenderJsonWhen(fn ($request) => $request->is('api/*') || $request->expectsJson());
        $exceptions->render(function (Throwable $error, Request $request) {
            if (! $request->is('api/*')) {
                return null;
            }
            $status = 500;
            $code = 'server_error';
            $message = 'No se pudo procesar la petición.';
            $fields = null;
            $headers = [];
            if ($error instanceof ApiProblem) {
                $status = $error->status;
                $code = $error->errorCode;
                $message = $error->getMessage();
            } elseif ($error instanceof ValidationException) {
                $status = 422;
                $code = 'validation_failed';
                $message = 'Datos no válidos.';
                $fields = $error->errors();
            } elseif ($error instanceof AuthenticationException) {
                $status = 401;
                $code = 'unauthenticated';
                $message = 'Autenticación requerida.';
            } elseif ($error instanceof HttpExceptionInterface) {
                $status = $error->getStatusCode();
                $headers = $error->getHeaders();
                $code = match ($status) {
                    404 => 'not_found', 403 => 'forbidden', 429 => 'rate_limited', 405 => 'method_not_allowed', default => 'http_error'
                };
                $message = 'Petición no disponible o no autorizada.';
            }
            $body = ['code' => $code, 'message' => $message];
            if ($fields !== null) {
                $body['fields'] = $fields;
            }

            return response()->json(['error' => $body], $status, $headers);
        });
    })->create();
