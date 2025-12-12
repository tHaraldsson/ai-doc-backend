package com.haraldsson.aidocbackend.user.service;

import com.haraldsson.aidocbackend.config.JwtTokenProvider;
import com.haraldsson.aidocbackend.user.dto.AuthResultDTO;
import com.haraldsson.aidocbackend.user.model.CustomUser;
import com.haraldsson.aidocbackend.user.repository.CustomUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class CustomUserService {

    private final CustomUserRepository customUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;


    public CustomUserService(CustomUserRepository customUserRepository, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
        this.customUserRepository = customUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public Mono<AuthResultDTO> loginUser(String username, String password) {

        System.out.println("--Custom user service login --");
        System.out.println("looking for user: " + username);

        return customUserRepository.findByUsername(username)
                .doOnNext(user -> System.out.println("user found: " + user.getUsername()))
                .flatMap(user -> {
                    System.out.println("Checking password for: " + username);
                    boolean passwordMatches = passwordEncoder.matches(password, user.getPassword());
                    System.out.println("password matches: " + passwordMatches);

                    if (!passwordEncoder.matches(password, user.getPassword())) {
                        System.out.println("---password mismatch --");
                        return Mono.error(new RuntimeException("Invalid Password"));
                    }

                    String token = jwtTokenProvider.generateToken(username);
                    System.out.println("Token generated successfully");
                    return Mono.just(new AuthResultDTO(token, username));
                })
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")))
                .doOnError(error -> System.out.println("login Error: " + error.getMessage()));
    }

    public Mono<CustomUser> registerUser (String username, String password) {

        return customUserRepository.findByUsername(username)
                .flatMap(existingUser -> Mono.error(new RuntimeException("User already exists")))
                .switchIfEmpty(Mono.defer(() -> {
                    CustomUser newUser = new CustomUser();
                    newUser.setUsername(username);
                    newUser.setPassword(passwordEncoder.encode(password));
                    return customUserRepository.save(newUser);
                }))
                .cast(CustomUser.class);
    }

    public Mono<CustomUser> findByUsername(String username) {
        return customUserRepository.findByUsername(username);
    }

    public Mono<UUID> getUserIdByUsername(String username) {
        return customUserRepository.findByUsername(username)
                .map(CustomUser::getId)
                .switchIfEmpty(Mono.error(new RuntimeException("User not found")));
    }

    public Mono<Boolean> userExists(String username) {
        return customUserRepository.existsByUsername(username);
    }


}
