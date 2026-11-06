package com.avigem.diagnostics.infrastructure.web.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Valida el Bearer existente de Laravel. Falla cerrado si no está configurado. */
@Component
public class LaravelBearerAuthenticationFilter extends OncePerRequestFilter {
    private final String validationUrl;
    private final HttpClient client;

    public LaravelBearerAuthenticationFilter(
            @Value("${diagnostics.auth.validation-url:}") String validationUrl) {
        this.validationUrl = validationUrl.trim();
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/diagnostics/real/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (validationUrl.isEmpty()) {
            reject(response, 503, "REAL_BLE_AUTH_NOT_CONFIGURED");
            return;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            reject(response, 401, "BEARER_REQUIRED");
            return;
        }
        try {
            HttpRequest authRequest = HttpRequest.newBuilder(URI.create(validationUrl))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .header("Authorization", authorization)
                .GET().build();
            HttpResponse<Void> authResponse = client.send(authRequest, HttpResponse.BodyHandlers.discarding());
            if (authResponse.statusCode() != 200) {
                reject(response, 401, "INVALID_LARAVEL_SESSION");
                return;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            reject(response, 503, "AUTH_SERVICE_UNAVAILABLE");
            return;
        } catch (IllegalArgumentException | IOException ex) {
            reject(response, 503, "AUTH_SERVICE_UNAVAILABLE");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"Acceso REAL_BLE no autorizado\"}");
    }
}
