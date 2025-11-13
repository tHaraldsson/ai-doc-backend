package com.haraldsson.aidocbackend.user.controller;


import com.haraldsson.aidocbackend.config.JwtTokenProvider;
import com.haraldsson.aidocbackend.user.dto.AuthResponseDTO;
import com.haraldsson.aidocbackend.user.dto.LoginRequestDTO;
import com.haraldsson.aidocbackend.user.model.CustomUser;
import com.haraldsson.aidocbackend.user.service.CustomUserService;
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

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final CustomUserService customUserService;
    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    public AuthController(CustomUserService customUserService, JwtTokenProvider jwtTokenProvider) {
        this.customUserService = customUserService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<AuthResponseDTO>> register(@RequestBody LoginRequestDTO request) {

        return customUserService.registerUser(request.getUsername(), request.getPassword())
                .map(user -> {
                    AuthResponseDTO response = new AuthResponseDTO("Registration successful. Please login", user.getUsername());
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    AuthResponseDTO response = new AuthResponseDTO(e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(response));
                });
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponseDTO>> login (@RequestBody LoginRequestDTO request) {

        System.out.println("=== LOGIN ATTEMPT ===");
        System.out.println("Username: " + request.getUsername());

        return customUserService.loginUser(request.getUsername(), request.getPassword())
                .flatMap(authResult -> {
                    System.out.println("=== LOGIN SUCCESSFUL ===");

                    ResponseCookie cookie = ResponseCookie.from("jwt", authResult.getToken())
                            .httpOnly(true)
                            .secure(false)
                            .path("/")
                            .maxAge(Duration.ofHours(12))
                            .sameSite("Strict")
                            .build();

                    AuthResponseDTO response = new AuthResponseDTO("Login successful", authResult.getUsername());

                    return Mono.just(ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, cookie.toString())
                            .body(response));
                })

                .onErrorResume(e -> {
                    System.out.println("=== LOGIN FAILED: " + e.getMessage() + " ===");
                    return Mono.just(ResponseEntity.status(401)
                            .body(new AuthResponseDTO(e.getMessage())));
                })
                .doOnSubscribe(sub -> System.out.println("Starting login process..."))
                .doOnTerminate(() -> System.out.println("Login process completed"));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<AuthResponseDTO>> logout(
            @CookieValue(value = "jwt", required = false) String jwtToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader
            ) {

        String username = "unknown";
        if (jwtToken != null && jwtTokenProvider.validateToken(jwtToken)) {
            username = jwtTokenProvider.getUsernameFromToken(jwtToken);
        }

        logger.info("Logout request from user: {}", username);

        ResponseCookie cookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(true)  // Put true in production
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        AuthResponseDTO response = new AuthResponseDTO("Logout successful", null);

        logger.info("User {} logged out successfully", username);
        return Mono.just(ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response));
    }

    @GetMapping("/user")
    public Mono<ResponseEntity<CustomUser>> getCurrentUser(
            @CookieValue(value = "jwt", required = false) String jwtToken) {

        if (jwtToken == null || !jwtTokenProvider.validateToken(jwtToken)) {
            return Mono.just(ResponseEntity.status(401).build());
        }

        String username = jwtTokenProvider.getUsernameFromToken(jwtToken);
        logger.debug("Current user request for: {}", username);

        return customUserService.findByUsername(username)
                .map(user -> ResponseEntity.ok().body(user))
                .defaultIfEmpty(ResponseEntity.status(401).build());
    }
}









