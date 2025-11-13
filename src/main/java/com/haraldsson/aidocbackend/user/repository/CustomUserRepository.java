package com.haraldsson.aidocbackend.user.repository;

import com.haraldsson.aidocbackend.user.model.CustomUser;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface CustomUserRepository extends ReactiveCrudRepository<CustomUser, UUID> {
    Mono<CustomUser> findByUsername(String username);
    Mono<Boolean> existsByUsername(String username);

}
