package com.haraldsson.aidocbackend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import javax.naming.ServiceUnavailableException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DatabaseCircuitBreaker {

    Logger log = LoggerFactory.getLogger(DatabaseCircuitBreaker.class);

    private final AtomicInteger failureCount = new AtomicInteger(0);
    private volatile boolean circuitOpen = false;
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    public <T> Mono<T> execute(Mono<T> operation) {
        if (circuitOpen) {
            if (System.currentTimeMillis() - lastFailureTime.get() > 30000) {
                circuitOpen = false;
                failureCount.set(0);
            } else {
                return Mono.error(new ServiceUnavailableException(
                        "Database is recovering, please try again in 30 seconds"));
            }
        }

        return operation
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .filter(e -> e instanceof DataAccessResourceFailureException)
                        .doBeforeRetry(signal -> log.warn("Circuit breaker retrying DB operation, attempt {}", signal.totalRetries() + 1)))
                .doOnError(e -> {
                    if (e instanceof DataAccessResourceFailureException) {
                        lastFailureTime.set(System.currentTimeMillis());
                        int failures = failureCount.incrementAndGet();
                        log.warn("Database error #{}, circuit will open at 3", failures);
                        if (failures >= 3) {
                            circuitOpen = true;
                            log.error("CIRCUIT OPENED - blocking database calls for 30s");
                            Mono.delay(Duration.ofSeconds(30))
                                    .subscribe(v -> {
                                        circuitOpen = false;
                                        failureCount.set(0);
                                        log.info("Circuit reset");
                                    });
                        }
                    }
                })
                .doOnSuccess(v -> failureCount.set(0));
    }
}