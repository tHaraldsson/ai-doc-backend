package com.haraldsson.aidocbackend.user.repository;

import com.haraldsson.aidocbackend.user.model.CustomUser;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface CustomUserRepository extends ReactiveCrudRepository<CustomUser, UUID> {

    @Retryable(
            value = { DataAccessResourceFailureException.class },
            maxAttempts = 3
    )
    Mono<CustomUser> findByUsername(String username);

    @Retryable(
            value = { DataAccessResourceFailureException.class },
            maxAttempts = 3
    )
    Mono<Boolean> existsByUsername(String username);

}
