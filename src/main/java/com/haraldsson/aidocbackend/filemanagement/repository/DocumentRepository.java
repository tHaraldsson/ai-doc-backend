package com.haraldsson.aidocbackend.filemanagement.repository;

import com.haraldsson.aidocbackend.filemanagement.model.Document;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends ReactiveCrudRepository<Document, Long> {
    // TODO lägg till querys
}
