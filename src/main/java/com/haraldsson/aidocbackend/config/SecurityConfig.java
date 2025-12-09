package com.haraldsson.aidocbackend.config;



import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;

import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {


    private final ReactiveJwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    public SecurityConfig(ReactiveJwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }


    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http, ReactiveJwtAuthenticationFilter jwtFilter) {
        return http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .pathMatchers("/api/auth/**").permitAll()
                        .pathMatchers("/api/upload").hasRole("USER")
                        .pathMatchers("/api/documents").hasRole("USER")
                        .pathMatchers("/api/textindb").hasRole("USER")
                        .pathMatchers("/api/ask").hasRole("USER")
                        .pathMatchers("/api/debug-all-chunks").permitAll()
                        .pathMatchers("/").permitAll()
                        .anyExchange().authenticated()
                )
                .addFilterAt(jwtAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint((exchange, ex) -> {
                            if (exchange.getResponse().isCommitted()) {
                                return Mono.empty();
                            }
                            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                            return exchange.getResponse().setComplete();
                        })
                        .accessDeniedHandler((exchange, ex) -> {
                            if (exchange.getResponse().isCommitted()) {
                                return Mono.empty();
                            }
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        })
                )
                .formLogin(form -> form.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .build();
    }
}
