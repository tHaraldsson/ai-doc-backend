package com.haraldsson.aidocbackend.filemanagement.repository;

import com.haraldsson.aidocbackend.filemanagement.model.Document;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;


import java.util.UUID;

@Repository
public interface DocumentRepository extends ReactiveCrudRepository<Document, UUID> {

    Flux<Document> findByUserId(UUID userId);

    @Query("SELECT * FROM documents WHERE user_id = :userId AND file_name = :fileName")
    Flux<Document> findByUserIdAndFileName(UUID userId, String fileName);
}
