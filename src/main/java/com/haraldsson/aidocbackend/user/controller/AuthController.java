package com.haraldsson.aidocbackend.user.controller;


import com.haraldsson.aidocbackend.config.JwtTokenProvider;
import com.haraldsson.aidocbackend.user.dto.AuthResponse;
import com.haraldsson.aidocbackend.user.dto.LoginRequest;
import com.haraldsson.aidocbackend.user.service.CustomUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final CustomUserService customUserService;
    private final JwtTokenProvider jwtTokenProvider;

    @Autowired
    public AuthController(CustomUserService customUserService, JwtTokenProvider jwtTokenProvider) {
        this.customUserService = customUserService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<AuthResponse>> register(@RequestBody LoginRequest request) {
        return customUserService.registerUser(request.getUsername(), request.getPassword())
                .flatMap(user -> {
                    String token = jwtTokenProvider.generateToken(user.getUsername());
                    AuthResponse response = new AuthResponse(token, user.getUsername());
                    return Mono.just(ResponseEntity.ok(response));
                })
                .onErrorResume(e -> {
                    AuthResponse response = new AuthResponse(e.getMessage()); // Check whats wrong with constructor
                    return Mono.just(ResponseEntity.badRequest().body(response));
                });
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<AuthResponse>> login (@RequestBody LoginRequest request) {

        System.out.println("=== LOGIN ATTEMPT ===");
        System.out.println("Username: " + request.getUsername());

        return customUserService.loginUser(request.getUsername(), request.getPassword())
                .map(token -> {
                    System.out.println("=== LOGIN SUCCESSFUL ===");
                    return ResponseEntity.ok(new AuthResponse(token, request.getUsername()));
                })
                .onErrorResume(e -> {
                    System.out.println("=== LOGIN FAILED: " + e.getMessage() + " ===");
                    return Mono.just(ResponseEntity.status(401)
                            .body(new AuthResponse(e.getMessage())));
                })
                .doOnSubscribe(sub -> System.out.println("Starting login process..."))
                .doOnTerminate(() -> System.out.println("Login process completed"));
    }
}
