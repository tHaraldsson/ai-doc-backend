package com.haraldsson.aidocbackend.config;

import com.haraldsson.aidocbackend.user.service.CustomUserService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
public class ReactiveJwtAuthenticationFilter implements WebFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserService customUserService;

    public ReactiveJwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, CustomUserService customUserService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.customUserService = customUserService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        System.out.println("=== REACTIVE JWT FILTER: " + exchange.getRequest().getMethod() + " " + path);

        // Skip for auth endpoints
        if (path.startsWith("/api/auth/")) {
            return chain.filter(exchange);
        }

        String token = getJwtFromRequest(exchange.getRequest());

        System.out.println("Token present: " + (token != null ? "YES" : "NO"));

        if (token != null && jwtTokenProvider.validateToken(token)) {
            String username = jwtTokenProvider.getUsernameFromToken(token);
            System.out.println("Valid token for user: " + username);

            return customUserService.findByUsername(username)
                    .flatMap(user -> {
                        System.out.println("Reactive Filter - User found: " + user.getUsername());
                        System.out.println("Reactive Filter - Authorities: " + user.getAuthorities());

                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());

                        SecurityContext context = new SecurityContextImpl(authentication);

                        return chain.filter(exchange)
                                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));
                    })
                    .switchIfEmpty(chain.filter(exchange));
        } else {
            System.out.println("JWT Filter - Token is INVALID or missing");
            return chain.filter(exchange);
        }
    }

    private String getJwtFromRequest(org.springframework.http.server.reactive.ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}