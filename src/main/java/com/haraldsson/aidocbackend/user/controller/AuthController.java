package com.haraldsson.aidocbackend.user.controller;


import com.haraldsson.aidocbackend.config.JwtTokenProvider;
import com.haraldsson.aidocbackend.user.dto.AuthResponse;
import com.haraldsson.aidocbackend.user.dto.LoginRequest;
import com.haraldsson.aidocbackend.user.service.CustomUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CustomUserService customUserService;

    @Autowired
    public AuthController(CustomUserService customUserService) {
        this.customUserService = customUserService;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<AuthResponse>> register(@RequestBody LoginRequest request) {

        return customUserService.registerUser(request.getUsername(), request.getPassword())
                .map(user -> {
                    AuthResponse response = new AuthResponse("Registration successful. Please login", user.getUsername());
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(e -> {
                    AuthResponse response = new AuthResponse(e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(response));
                });
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login (@RequestBody LoginRequest request) {

        System.out.println("=== LOGIN ATTEMPT ===");
        System.out.println("Username: " + request.getUsername());

        return customUserService.loginUser(request.getUsername(), request.getPassword())
                .flatMap(authResult -> {
                    System.out.println("=== LOGIN SUCCESSFUL ===");

                    ResponseCookie cookie = ResponseCookie.from("jwt", authResult.getToken())
                            .httpOnly(true)
                            .secure(false)
                            .path("/")
                            .maxAge(Duration.ofHours(24))
                            .sameSite("Strict")
                            .build();

                    AuthResponse response = new AuthResponse("Login successful", authResult.getUsername());

                    return Mono.just(ResponseEntity.ok()
                            .header(HttpHeaders.SET_COOKIE, cookie.toString())
                            .body(response));
                })

                .onErrorResume(e -> {
                    System.out.println("=== LOGIN FAILED: " + e.getMessage() + " ===");
                    return Mono.just(ResponseEntity.status(401)
                            .body(new AuthResponse(e.getMessage())));
                })
                .doOnSubscribe(sub -> System.out.println("Starting login process..."))
                .doOnTerminate(() -> System.out.println("Login process completed"));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<AuthResponse>> logout() {
        ResponseCookie cookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();

        AuthResponse response = new AuthResponse("Logout successful", null);
        return Mono.just(ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(response));
    }
}









