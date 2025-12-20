package com.haraldsson.aidocbackend.user.controller;


import com.haraldsson.aidocbackend.config.JwtTokenProvider;
import com.haraldsson.aidocbackend.user.dto.AuthResponseDTO;
import com.haraldsson.aidocbackend.user.dto.LoginRequestDTO;
import com.haraldsson.aidocbackend.user.dto.RegisterRequestDTO;
import com.haraldsson.aidocbackend.user.model.CustomUser;
import com.haraldsson.aidocbackend.user.service.CustomUserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final CustomUserService customUserService;
    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    public AuthController(CustomUserService customUserService, JwtTokenProvider jwtTokenProvider) {
        this.customUserService = customUserService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<AuthResponseDTO>> register(
            @Valid @RequestBody RegisterRequestDTO request) {

        String maskedUsername = maskUsername(request.getUsername());
        log.info("Registration request received for user: {}", maskedUsername);

        return customUserService.registerUser(request.getUsername(), request.getPassword())
                .map(user -> {
                    AuthResponseDTO response = new AuthResponseDTO("Registration successful. Please login", user.getUsername());
                    log.info("Registration successful for user: {}", maskedUsername);
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    log.error("Registration failed for {}: {}", maskedUsername, e.getMessage());
                    AuthResponseDTO response = new AuthResponseDTO(e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(response));
                })
                .doOnSubscribe(s -> log.debug("Starting registration process for: {}", maskedUsername))
                .doOnTerminate(() -> log.debug("Registration process completed for: {}", maskedUsername));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponseDTO>> login (
            @Valid @RequestBody LoginRequestDTO request) {

        String maskedUsername = maskUsername(request.getUsername());
        log.info("Login request received for user: {}", maskedUsername);

        return customUserService.loginUser(request.getUsername(), request.getPassword())
                .flatMap(authResult -> {
                    log.info("Login successful for user: {}", maskedUsername);

                    ResponseCookie cookie = ResponseCookie.from("jwt", authResult.getToken())
                            .httpOnly(true)
                            .secure(true) // sätt true vid prod
                            .path("/")
                            .maxAge(Duration.ofHours(12))
                            .sameSite("None")
                            .build();

                    AuthResponseDTO response = new AuthResponseDTO("Login successful", authResult.getUsername());

                    return Mono.just(ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, cookie.toString())
                            .body(response));
                })

                .onErrorResume(e -> {
                    log.warn("Login failed for {}: {}", maskedUsername, e.getMessage());
                    return Mono.just(ResponseEntity.status(401)
                            .body(new AuthResponseDTO(e.getMessage())));
                })
                .doOnSubscribe(s -> log.debug("Starting login process for: {}", maskedUsername))
                .doOnTerminate(() -> log.debug("Login process completed for: {}", maskedUsername));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<AuthResponseDTO>> logout(
            @CookieValue(value = "jwt", required = false) String jwtToken
            ) {

        String username = "unknown";
        if (jwtToken != null && jwtTokenProvider.validateToken(jwtToken)) {
            username = jwtTokenProvider.getUsernameFromToken(jwtToken);
        }

        String maskedUsername = maskUsername(username);
        log.info("Logout request from user: {}", maskedUsername);

        ResponseCookie cookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("None")
                .build();

        AuthResponseDTO response = new AuthResponseDTO("Logout successful", null);

        log.info("User {} logged out successfully", maskedUsername);
        return Mono.just(ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response));
    }

    @GetMapping("/user")
    public Mono<ResponseEntity<CustomUser>> getCurrentUser(
            @CookieValue(value = "jwt", required = false) String jwtToken) {

        if (jwtToken == null || !jwtTokenProvider.validateToken(jwtToken)) {
            log.warn("Unauthorized user request - invalid or missing token");
            return Mono.just(ResponseEntity.status(401).build());
        }

        String username = jwtTokenProvider.getUsernameFromToken(jwtToken);
        String maskedUsername = maskUsername(username);
        log.debug("Current user request for: {}", maskedUsername);


        return customUserService.findByUsername(username)
                .map(user -> {
                    log.debug("User found for request: {}", maskedUsername);
                    return ResponseEntity.ok().body(user);
                })
                .defaultIfEmpty(ResponseEntity.status(401).build())
                .doOnError(e -> log.error("Error fetching user {}: {}",
                        maskedUsername, e.getMessage()));
    }

    // hjälpmetod för att dölja username
    private String maskUsername(String username) {
        if (username == null || username.length() <= 2) {
            return "***";
        }
        return username.substring(0, 2) + "***";
    }
}
