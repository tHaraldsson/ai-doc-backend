package com.haraldsson.aidocbackend.filemanagement.repository;

import com.haraldsson.aidocbackend.filemanagement.model.Document;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;


import java.util.UUID;

@Repository
public interface DocumentRepository extends ReactiveCrudRepository<Document, UUID> {

    Flux<Document> findByUserId(UUID userId);
}
