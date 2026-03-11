package com.nexuspro.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Rate limiter key resolvers and fallback handlers.
 *
 * Two resolvers:
 * - ipKeyResolver:   Rate limit by client IP (for auth endpoints — no user yet)
 * - userKeyResolver: Rate limit by user ID extracted from JWT (post-auth)
 */
@Configuration
public class GatewayConfig {

    /** Rate-limit auth endpoints by IP address */
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> {
            String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (ip != null && !ip.isBlank()) {
                ip = ip.split(",")[0].trim();
            } else {
                ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            }
            return Mono.just(ip);
        };
    }

    /** Rate-limit authenticated endpoints by user ID (injected by JwtAuthFilter) */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
            if (userId != null && !userId.isBlank()) return Mono.just(userId);

            // Fallback to IP if header missing
            String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            return Mono.just(ip != null ? ip.split(",")[0].trim() : "anonymous");
        };
    }

    /** Circuit breaker fallback responses */
    @Bean
    public RouterFunction<ServerResponse> fallbackRoutes() {
        return RouterFunctions.route()
            .GET("/fallback/{service}", request -> ServerResponse
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .bodyValue("{\"success\":false,\"message\":\"Service temporarily unavailable. Please try again shortly.\",\"errorCode\":\"SERVICE_UNAVAILABLE\"}"))
            .POST("/fallback/{service}", request -> ServerResponse
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .bodyValue("{\"success\":false,\"message\":\"Service temporarily unavailable. Please try again shortly.\",\"errorCode\":\"SERVICE_UNAVAILABLE\"}"))
            .build();
    }
}
