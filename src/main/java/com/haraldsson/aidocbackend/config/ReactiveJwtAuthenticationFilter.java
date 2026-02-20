package com.haraldsson.aidocbackend.config;

import com.haraldsson.aidocbackend.user.model.CustomUser;
import com.haraldsson.aidocbackend.user.service.CustomUserService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ReactiveJwtAuthenticationFilter implements WebFilter {

    private final Logger log = LoggerFactory.getLogger(ReactiveJwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserService customUserService;

    public ReactiveJwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, CustomUserService customUserService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.customUserService = customUserService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        log.debug("Reactive JWT Filter: {} {}", exchange.getRequest().getMethod(), path);

        // Skip for auth endpoints
        if (path.startsWith("/api/auth/")) {
            log.debug("Skipping JWT filter for auth endpoint");
            return chain.filter(exchange);
        }

        String token = getJwtFromRequest(exchange.getRequest());

        log.debug("Token present: {}", token != null);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            try {
                Claims claims = jwtTokenProvider.getAllClaimsFromToken(token);

                String username = claims.getSubject();
                UUID userId = UUID.fromString(claims.get("userId", String.class));
                List<String> roles = claims.get("roles", List.class);

                List<GrantedAuthority> authorities = roles.stream()
                        .map(role -> new SimpleGrantedAuthority(role))
                        .collect(Collectors.toList());

                CustomUser userFromToken = new CustomUser(
                        userId,
                        username,
                        "",
                        authorities
                );

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userFromToken, null, authorities);

                SecurityContext context = new SecurityContextImpl(authentication);

                return chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(context)));

            } catch (Exception e) {
                log.warn("Could not create auth from token: {}", e.getMessage());
                return chain.filter(exchange);
            }
        } else {
            return chain.filter(exchange);
        }
    }

    private String getJwtFromRequest(ServerHttpRequest request) {

        if (request.getCookies().containsKey("jwt")) {
            String jwtCookie = request.getCookies().getFirst("jwt").getValue();

            if (StringUtils.hasText(jwtCookie)) {
                return jwtCookie;
            }
        }
        String bearerToken = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}