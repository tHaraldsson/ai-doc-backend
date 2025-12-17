package com.haraldsson.aidocbackend.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class HealthController {

    @GetMapping("/health")
    public Mono<ResponseEntity<String>> apiHealth() {
        return Mono.just(ResponseEntity.ok("OK"));
    }
}
