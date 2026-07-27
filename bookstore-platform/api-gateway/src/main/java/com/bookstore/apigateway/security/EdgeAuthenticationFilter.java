package com.bookstore.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Edge authentication: rejects requests to protected routes that lack a valid JWT
 * (401) before they are routed, and propagates the caller's identity downstream
 * as {@code X-Auth-Username}/{@code X-Auth-Role} headers (the original bearer
 * token is also forwarded, and services re-validate it — defense in depth).
 *
 * <p>Public routes: {@code /api/auth/**}, {@code GET /api/books/**}, and actuator.
 * Fine-grained role checks (ADMIN) stay in the services.
 */
@Component
public class EdgeAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final GatewayJwtValidator jwtValidator;

    public EdgeAuthenticationFilter(GatewayJwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (isPublic(request.getURI().getPath(), request.getMethod())) {
            return chain.filter(exchange);
        }

        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Missing bearer token");
        }
        try {
            Claims claims = jwtValidator.parse(header.substring(BEARER_PREFIX.length()));
            ServerHttpRequest mutated = request.mutate()
                    .header("X-Auth-Username", claims.getSubject())
                    .header("X-Auth-Role", String.valueOf(claims.get("role", String.class)))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (JwtException | IllegalArgumentException ex) {
            return unauthorized(exchange, "Invalid or expired token");
        }
    }

    private boolean isPublic(String path, HttpMethod method) {
        if (path.startsWith("/actuator")) {
            return true;
        }
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        // Catalog reads are public; writes require a token (ADMIN enforced in book-service).
        return path.startsWith("/api/books") && HttpMethod.GET.equals(method);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Run before the routing filter so unauthenticated requests never reach a service.
        return -1;
    }
}
