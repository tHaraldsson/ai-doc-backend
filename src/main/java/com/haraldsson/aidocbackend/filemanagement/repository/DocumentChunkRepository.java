package com.haraldsson.aidocbackend.filemanagement.repository;

import com.haraldsson.aidocbackend.filemanagement.model.DocumentChunk;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface DocumentChunkRepository extends ReactiveCrudRepository<DocumentChunk, UUID> {

    @Query("SELECT * FROM document_chunks WHERE user_id = :userId ORDER BY filename, chunk_number")
    Flux<DocumentChunk> findByUserId(UUID userId);

    @Query("SELECT * FROM document_chunks WHERE document_id = :documentId ORDER BY chunk_number")
    Flux<DocumentChunk> findByDocumentId(UUID documentId);

    @Query("SELECT * FROM document_chunks WHERE filename = :filename AND user_id = :userId ORDER BY chunk_number")
    Flux<DocumentChunk> findByFilenameAndUserId(String filename, UUID userId);

    @Query("DELETE FROM document_chunks WHERE document_id = :documentId")
    Mono<Void> deleteByDocumentId(UUID documentId);

    @Query("SELECT DISTINCT filename FROM document_chunks WHERE user_id = :userId")
    Flux<String> findDistinctFilenamesByUserId(UUID userId);
}