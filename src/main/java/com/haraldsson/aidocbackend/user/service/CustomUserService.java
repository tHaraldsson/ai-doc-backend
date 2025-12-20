package com.haraldsson.aidocbackend.user.service;

import com.haraldsson.aidocbackend.config.JwtTokenProvider;
import com.haraldsson.aidocbackend.user.dto.AuthResultDTO;
import com.haraldsson.aidocbackend.user.model.CustomUser;
import com.haraldsson.aidocbackend.user.repository.CustomUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;


@Service
public class CustomUserService {
    private final Logger log = LoggerFactory.getLogger(CustomUserService.class);

    private final CustomUserRepository customUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;


    public CustomUserService(CustomUserRepository customUserRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.customUserRepository = customUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public Mono<AuthResultDTO> loginUser(String username, String password) {
        log.info("Login attempt for user: {}", maskUsername(username));

        return customUserRepository.findByUsername(username)
                .doOnNext(user -> log.debug("User found: {}", maskUsername(user.getUsername())))
                .flatMap(user -> {
                    boolean passwordMatches = passwordEncoder.matches(password, user.getPassword());
                    log.debug("Password verification result for {}: {}",
                            maskUsername(username), passwordMatches ? "MATCH" : "MISMATCH");

                    if (!passwordEncoder.matches(password, user.getPassword())) {
                        log.warn("Invalid password attempt for user: {}", maskUsername(username));
                        return Mono.error(new RuntimeException("Invalid Password"));
                    }

                    String token = jwtTokenProvider.generateToken(user);
                    log.info("Login successful for user: {}, token generated", maskUsername(username));
                    return Mono.just(new AuthResultDTO(token, username));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("User not found: {}", maskUsername(username));
                    return Mono.error(new RuntimeException("Invalid credentials"));
                }))
                .doOnError(error -> log.error("Login error for {}: {}",
                        maskUsername(username), error.getMessage()));
    }

    public Mono<CustomUser> registerUser(String username, String password) {

        log.info("Registration attempt for user: {}", maskUsername(username));

        return customUserRepository.findByUsername(username)
                .flatMap(existingUser -> {
                    log.warn("Registration failed - user already exists: {}",
                            maskUsername(username));
                    return Mono.error(new RuntimeException("Username already taken"));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    CustomUser newUser = new CustomUser();
                    newUser.setUsername(username);
                    newUser.setPassword(passwordEncoder.encode(password));
                    log.info("Creating new user: {}", maskUsername(username));

                    return customUserRepository.save(newUser)
                            .doOnSuccess(savedUser ->
                                    log.info("User registered successfully: {}",
                                            maskUsername(savedUser.getUsername())));
                }))
                .cast(CustomUser.class)
                .doOnError(error -> log.error("Registration error for {}: {}",
                        maskUsername(username), error.getMessage()));
    }

    public Mono<CustomUser> findByUsername(String username) {
        log.debug("Looking up user by username: {}", maskUsername(username));
        return customUserRepository.findByUsername(username);
    }

    // hjälpmetod för att skydda username info vid loggning
    private String maskUsername(String username) {
        if (username == null || username.length() <= 2) {
            return "***";
        }

        return username.substring(0, 2) + "***";
    }
}
