package com.haraldsson.aidocbackend.filemanagement.service;

import com.haraldsson.aidocbackend.advice.exceptions.FileProcessingException;
import com.haraldsson.aidocbackend.advice.exceptions.ResourceNotFoundException;
import com.haraldsson.aidocbackend.config.DatabaseCircuitBreaker;
import com.haraldsson.aidocbackend.filemanagement.dto.ChunkWithSimilarityDTO;
import com.haraldsson.aidocbackend.filemanagement.model.Document;
import com.haraldsson.aidocbackend.filemanagement.model.DocumentChunk;
import com.haraldsson.aidocbackend.filemanagement.repository.DocumentChunkRepository;
import com.haraldsson.aidocbackend.filemanagement.repository.DocumentRepository;
import com.haraldsson.aidocbackend.filemanagement.utils.DocumentChunkHelper;
import com.haraldsson.aidocbackend.user.model.CustomUser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingService embeddingService;
    private final ExcelProcessorService excelProcessorService;
    private final PowerPointProcessorService powerPointProcessorService;
    private final DocumentChunkHelper documentChunkHelper;
    private final DatabaseCircuitBreaker circuitBreaker;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentChunkRepository documentChunkRepository,
                           EmbeddingService embeddingService,
                           ExcelProcessorService excelProcessorService,
                           PowerPointProcessorService powerPointProcessorService,
                           DocumentChunkHelper documentChunkHelper, DatabaseCircuitBreaker circuitBreaker) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingService = embeddingService;
        this.excelProcessorService = excelProcessorService;
        this.powerPointProcessorService = powerPointProcessorService;
        this.documentChunkHelper = documentChunkHelper;
        this.circuitBreaker = circuitBreaker;
    }


    public Mono<Document> processAndSaveFile(FilePart filePart, CustomUser user) {
        return circuitBreaker.execute(
                convertFileToText(filePart)
                        .map(text -> {
                            String cleanedText = text.replace("\u0000", "")
                                    .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
                                    .replaceAll("\\s+", " ")
                                    .trim();

                            log.info("Text cleaned: {} -> {} characters", text.length(), cleanedText.length());

                            if (cleanedText.contains("\u0000")) {
                                log.warn("WARNING: Text still contains null bytes after cleaning!");
                            }

                            return cleanedText;
                        })
                        .flatMap(text -> {
                            return documentRepository.findByUserIdAndFileName(user.getId(), filePart.filename())
                                    .collectList()
                                    .flatMap(existingDocs -> {
                                        if (!existingDocs.isEmpty()) {
                                            return Flux.fromIterable(existingDocs)
                                                    .flatMap(oldDoc -> circuitBreaker.execute(
                                                            deleteDocumentAndChunks(oldDoc.getId())
                                                    ))
                                                    .then(circuitBreaker.execute(
                                                            saveDocumentWithChunks(filePart, user, text)
                                                    ));
                                        } else {
                                            return circuitBreaker.execute(
                                                    saveDocumentWithChunks(filePart, user, text)
                                            );
                                        }
                                    });
                        })
        );
    }



    private Mono<Document> processNewFileWithBytes(byte[] fileBytes, FilePart filePart, CustomUser user, String filename) {
        if (filename.endsWith(".pdf")) {
            return processPdfBytes(fileBytes, filePart, user);
        } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            return processExcelBytes(fileBytes, filePart, user);
        } else if (filename.endsWith(".pptx") || filename.endsWith(".ppt")) {
            return processPowerPointBytes(fileBytes, filePart, user);
        } else {
            return Mono.error(new FileProcessingException(
                    "Unsupported file type: " + filename));
        }
    }

    private Mono<Void> deleteDocumentAndChunks(UUID documentId) {
        return documentChunkRepository.deleteByDocumentId(documentId)
                .then(documentRepository.deleteById(documentId))
                .doOnSuccess(v -> log.debug("Deleted document and chunks for ID: {}", documentId))
                .doOnError(e -> log.error("Error deleting document {}: {}", documentId, e.getMessage()));
    }


    private Mono<Document> processPdfBytes(byte[] bytes, FilePart filePart, CustomUser user) {
        return extractTextFromPdf(bytes)
                .map(documentChunkHelper::cleanTextForDatabase)
                .flatMap(text -> saveDocumentWithChunks(filePart, user, text));
    }

    private Mono<Document> processExcelBytes(byte[] bytes, FilePart filePart, CustomUser user) {
        return excelProcessorService.extractTextFromExcel(bytes)
                .map(documentChunkHelper::cleanTextForDatabase)
                .flatMap(text -> {
                    log.info("Excel extracted text: {} characters", text.length());
                    return saveDocumentWithChunks(filePart, user, text);
                });
    }

    private Mono<Document> processPowerPointBytes(byte[] bytes, FilePart filePart, CustomUser user) {
        return powerPointProcessorService.extractTextFromPowerPoint(bytes)
                .map(documentChunkHelper::cleanTextForDatabase)
                .flatMap(text -> {
                    log.info("PowerPoint extracted text: {} characters", text.length());
                    return saveDocumentWithChunks(filePart, user, text);
                });
    }

    private Mono<Document> saveDocumentWithChunks(FilePart filePart, CustomUser user, String text) {
        Document document = new Document(filePart.filename(), text, user.getId());

        return documentRepository.save(document)
                .flatMap(savedDoc -> {
                    String filename = filePart.filename().toLowerCase();
                    if (filename.endsWith(".pdf")) {
                        return createPdfChunks(savedDoc.getId(), user.getId(),
                                filePart.filename(), text)
                                .thenReturn(savedDoc);
                    } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
                        return createExcelChunks(savedDoc.getId(), user.getId(),
                                filePart.filename(), text)
                                .thenReturn(savedDoc);
                    } else if (filename.endsWith(".pptx") || filename.endsWith(".ppt")) {
                        return createPowerPointChunks(savedDoc.getId(), user.getId(),
                                filePart.filename(), text)
                                .thenReturn(savedDoc);
                    }
                    return Mono.just(savedDoc);
                })
                .doOnSuccess(doc -> log.info("Successfully saved document: {}", doc.getFileName()))
                .doOnError(e -> log.error("Failed to save document with chunks: {}", e.getMessage(), e));
    }

    private Mono<String> convertFileToText(FilePart filePart) {
        String filename = filePart.filename().toLowerCase();

        if (filename.endsWith(".pdf")) {
            return streamAndProcessPdf(filePart);
        } else if (filename.endsWith(".pptx") || filename.endsWith(".ppt")) {
            return powerPointProcessorService.streamAndExtractText(filePart);

        } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            return excelProcessorService.streamAndExtractText(filePart);

        } else {
            return Mono.error(new FileProcessingException("Unsupported file type"));
        }
    }

    private Mono<String> streamAndProcessPdf(FilePart filePart) {
        return Mono.usingWhen(
                Mono.fromCallable(() -> Files.createTempFile("upload-", ".pdf")),
                tempFile -> filePart.transferTo(tempFile)
                        .then(Mono.fromCallable(() -> {
                            try (PDDocument doc = PDDocument.load(tempFile.toFile())) {
                                PDFTextStripper stripper = new PDFTextStripper();
                                return stripper.getText(doc);
                            }
                        })),
                tempFile -> Mono.fromRunnable(() -> {
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException e) {
                        log.warn("Failed to delete temp file: {}", tempFile, e);
                    }
                })
        );
    }

    private Mono<String> extractTextFromPdf(byte[] pdfBytes) {
        return Mono.fromCallable(() -> {
            try (PDDocument document = PDDocument.load(pdfBytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                String rawText = stripper.getText(document);

                return rawText.replace('\0', ' ')
                        .replaceAll("\\p{Cntrl}", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
            } catch (IOException e) {
                throw new FileProcessingException("PDF processing error", e);
            }
        });
    }

    private Mono<Void> createPdfChunks(UUID documentId, UUID userId,
                                       String filename, String fullText) {
        String cleanedText = documentChunkHelper.cleanTextForDatabase(fullText);

        log.info("=== START PDF CHUNKING (WITH EMBEDDINGS) ===");
        int chunkSize = 1000;
        int overlap = 200;
        int numChunks = (int) Math.ceil((double) cleanedText.length() / (chunkSize - overlap));

        return Flux.range(0, numChunks)
                .map(i -> {
                    int start = i * (chunkSize - overlap);
                    int end = Math.min(start + chunkSize, cleanedText.length());
                    if (start >= cleanedText.length()) return null;

                    String chunkText = cleanedText.substring(start, end);

                    DocumentChunk chunk = new DocumentChunk(
                            documentId,
                            userId,
                            filename,
                            chunkText,
                            i + 1,
                            start,
                            end
                    );
                    return chunk;
                })
                .filter(Objects::nonNull)
                .buffer(10)
                .delayElements(Duration.ofMillis(300))
                .flatMap(batch -> {
                    return documentChunkRepository.saveAll(batch)
                            .collectList()
                            .flatMap(chunks -> {
                                return Flux.fromIterable(chunks)
                                        .flatMap(chunk ->
                                                        embeddingService.createEmbedding(chunk.getContent())
                                                                .timeout(Duration.ofSeconds(15))
                                                                .doOnNext(embedding -> {
                                                                    chunk.setEmbedding(embedding);
                                                                    log.debug("Embedding created for chunk {}", chunk.getChunkNumber());
                                                                })
                                                                .onErrorResume(e -> {
                                                                    log.warn("Embedding failed for chunk {}: {}",
                                                                            chunk.getChunkNumber(), e.getMessage());
                                                                    return Mono.empty();
                                                                })
                                                                .then(Mono.just(chunk)),
                                                1
                                        )
                                        .collectList()
                                        .flatMap(chunksWithEmbeddings ->
                                                documentChunkRepository.saveAll(chunksWithEmbeddings).then()
                                        );
                            });
                }, 1)
                .then()
                .doOnSuccess(v -> log.info("ALL PDF CHUNKS SAVED WITH EMBEDDINGS"))
                .doOnError(e -> log.error("PDF-CHUNKING FAILED: {}", e.getMessage()));
    }

    private Mono<Void> createExcelChunks(UUID documentId, UUID userId,
                                         String filename, String excelText) {
        log.info("=== CREATING EXCEL-CHUNKS (WITH EMBEDDINGS) ===");
        String[] worksheets = excelText.split("--- WORKSHEET: ");

        return Flux.range(1, worksheets.length - 1)
                .map(i -> {
                    String worksheetText = worksheets[i].trim();
                    if (worksheetText.isEmpty()) return null;

                    DocumentChunk chunk = new DocumentChunk();
                    chunk.setDocumentId(documentId);
                    chunk.setUserId(userId);
                    chunk.setFilename(filename);
                    chunk.setContent("--- WORKSHEET: " + worksheetText);
                    chunk.setChunkNumber(i);
                    chunk.setStartIndex(0);
                    chunk.setEndIndex(worksheetText.length());
                    return chunk;
                })
                .filter(Objects::nonNull)
                .buffer(5)
                .delayElements(Duration.ofMillis(400))
                .flatMap(batch -> {
                    return documentChunkRepository.saveAll(batch)
                            .collectList()
                            .flatMap(chunks -> {
                                return Flux.fromIterable(chunks)
                                        .flatMap(chunk ->
                                                        embeddingService.createEmbedding(chunk.getContent())
                                                                .timeout(Duration.ofSeconds(15))
                                                                .doOnNext(embedding -> {
                                                                    chunk.setEmbedding(embedding);
                                                                    log.debug("Embedding created for chunk {}", chunk.getChunkNumber());
                                                                })
                                                                .onErrorResume(e -> {
                                                                    log.warn("Embedding failed for chunk {}: {}",
                                                                            chunk.getChunkNumber(), e.getMessage());
                                                                    return Mono.empty();
                                                                })
                                                                .then(Mono.just(chunk)),
                                                1
                                        )
                                        .collectList()
                                        .flatMap(chunksWithEmbeddings ->
                                                documentChunkRepository.saveAll(chunksWithEmbeddings).then()
                                        );
                            });
                }, 1)
                .then()
                .doOnSuccess(v -> log.info("ALL EXCEL-CHUNKS SAVED WITH EMBEDDINGS"));
    }

    private Mono<Void> createPowerPointChunks(UUID documentId, UUID userId,
                                              String filename, String powerpointText) {
        log.info("=== CREATING POWERPOINT-CHUNKS (BATCHED) ===");
        String[] slides = powerpointText.split("--- SLIDE ");

        return Flux.range(1, slides.length - 1)
                .map(i -> {
                    String slideText = slides[i].trim();
                    if (slideText.isEmpty()) return null;

                    DocumentChunk chunk = new DocumentChunk();
                    chunk.setDocumentId(documentId);
                    chunk.setUserId(userId);
                    chunk.setFilename(filename);
                    chunk.setContent("--- SLIDE " + slideText);
                    chunk.setChunkNumber(i);
                    chunk.setStartIndex(0);
                    chunk.setEndIndex(slideText.length());
                    return chunk;
                })
                .filter(Objects::nonNull)
                .buffer(5)
                .delayElements(Duration.ofMillis(500))
                .flatMap(batch -> {
                    return documentChunkRepository.saveAll(batch)
                            .collectList()
                            .flatMap(chunks -> {
                                return Flux.fromIterable(chunks)
                                        .flatMap(chunk ->
                                                        embeddingService.createEmbedding(chunk.getContent())
                                                                .timeout(Duration.ofSeconds(15))
                                                                .doOnNext(embedding -> {
                                                                    chunk.setEmbedding(embedding);
                                                                    log.debug("Embedding created for chunk {}", chunk.getChunkNumber());
                                                                })
                                                                .onErrorResume(e -> {
                                                                    log.warn("Embedding failed for chunk {}: {}",
                                                                            chunk.getChunkNumber(), e.getMessage());
                                                                    return Mono.empty();
                                                                })
                                                                .then(Mono.just(chunk)),
                                                1
                                        )
                                        .collectList()
                                        .flatMap(chunksWithEmbeddings ->

                                                documentChunkRepository.saveAll(chunksWithEmbeddings).then()
                                        );
                            });
                }, 1)
                .then()
                .doOnSuccess(v -> log.info("ALL POWERPOINT-CHUNKS SAVED (BATCHED)"))
                .doOnError(e -> log.error("POWERPOINT-CHUNKING FAILED: {}", e.getMessage()));
    }

    public Mono<String> getTextByUserId(UUID userId) {
        return documentRepository.findByUserId(userId)
                .collectList()
                .map(documents -> {
                    if (documents.isEmpty()) {
                        return "No documents found for the user";
                    }

                    StringBuilder result = new StringBuilder();

                    for (int i = 0; i < documents.size(); i++) {
                        Document doc = documents.get(i);
                        result.append("═══════════════════════════════\n");
                        result.append("").append(doc.getFileName()).append("\n");
                        result.append("═══════════════════════════════\n\n");

                        String content = doc.getContent();
                        if (content != null) {
                            String formatted = formatSimply(content);
                            result.append(formatted);
                        }

                        result.append("\n\n");
                    }

                    return result.toString();
                });
    }

    private String formatSimply(String text) {
        String[] words = text.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            result.append(words[i]).append(" ");

            if ((i + 1) % 15 == 0) {
                result.append("\n");
            }

            if (i >= 50) {
                result.append("\n[...]");
                break;
            }
        }

        return result.toString().trim();
    }

    public Mono<String> findRelevantChunks(String question, UUID userId) {
        return getAllChunksByUserId(userId)
                .collectList()
                .map(allChunks -> {
                    String[] keywords = question.toLowerCase().split("\\s+");
                    StringBuilder relevantText = new StringBuilder();
                    int chunksFound = 0;

                    for (DocumentChunk chunk : allChunks) {
                        String chunkContent = chunk.getContent().toLowerCase();
                        boolean hasKeyword = false;

                        for (String keyword : keywords) {
                            if (keyword.length() > 3 && chunkContent.contains(keyword)) {
                                hasKeyword = true;
                                break;
                            }
                        }

                        if (hasKeyword && chunksFound < 5) {
                            relevantText.append("--- Chunk ").append(chunk.getChunkNumber())
                                    .append(" from ").append(chunk.getFilename())
                                    .append(" ---\n")
                                    .append(chunk.getContent())
                                    .append("\n\n");
                            chunksFound++;
                        }
                    }

                    if (chunksFound == 0 && !allChunks.isEmpty()) {
                        relevantText.append("(No specific matches was found, showing the first parts)\n\n");
                        for (int i = 0; i < Math.min(3, allChunks.size()); i++) {
                            DocumentChunk chunk = allChunks.get(i);
                            relevantText.append("--- Chunk ").append(chunk.getChunkNumber())
                                    .append(" from ").append(chunk.getFilename())
                                    .append(" ---\n")
                                    .append(chunk.getContent())
                                    .append("\n\n");
                        }
                    }

                    return relevantText.toString();
                })
                .defaultIfEmpty("No document was found for the user");
    }

    public Mono<String> findRelevantChunksWithEmbeddings(String question, UUID userId) {
        log.info("Searching with embeddings for question: {}", question);

        return embeddingService.createEmbedding(question)
                .flatMap(queryEmbedding -> {
                    if (queryEmbedding == null || queryEmbedding.length == 0) {
                        log.warn("No embedding for question, falling back to keyword search");
                        return findRelevantChunks(question, userId);
                    }

                    log.debug("Question embedding created: {} dimensions", queryEmbedding.length);

                    return getAllChunksByUserId(userId)
                            .collectList()
                            .map(allChunks -> {
                                if (allChunks.isEmpty()) {
                                    log.info("No chunks found for user: {}", userId);
                                    return "No document was found for the user";
                                }

                                log.debug("Comparing with {} chunks", allChunks.size());

                                List<ChunkWithSimilarityDTO> scoredChunks = new ArrayList<>();

                                for (DocumentChunk chunk : allChunks) {
                                    float[] chunkEmbedding = chunk.getEmbedding();
                                    if (chunkEmbedding != null && chunkEmbedding.length > 0) {
                                        float similarity = documentChunkHelper.cosineSimilarity(queryEmbedding, chunkEmbedding);
                                        scoredChunks.add(new ChunkWithSimilarityDTO(chunk, similarity));
                                    }
                                }

                                scoredChunks.sort((a, b) -> Float.compare(b.getSimilarity(), a.getSimilarity()));

                                StringBuilder relevantText = new StringBuilder();
                                relevantText.append("Searched with embeddings - found ")
                                        .append(scoredChunks.size())
                                        .append(" chunks with embeddings\n\n");

                                int chunksToTake = Math.min(5, scoredChunks.size());
                                for (int i = 0; i < chunksToTake; i++) {
                                    ChunkWithSimilarityDTO scored = scoredChunks.get(i);
                                    DocumentChunk chunk = scored.getChunk();

                                    relevantText.append("--- Chunk ").append(chunk.getChunkNumber())
                                            .append(" (relevance: ").append(String.format("%.3f", scored.getSimilarity()))
                                            .append(") from ").append(chunk.getFilename())
                                            .append(" ---\n")
                                            .append(chunk.getContent())
                                            .append("\n\n");
                                }

                                if (!scoredChunks.isEmpty()) {
                                    log.info("Top 5 matches:");
                                    for (int i = 0; i < Math.min(5, scoredChunks.size()); i++) {
                                        ChunkWithSimilarityDTO scored = scoredChunks.get(i);
                                        log.info("#{}: {} - similarity: {}",
                                                i+1,
                                                scored.getChunk().getFilename(),
                                                scored.getSimilarity());
                                    }
                                }

                                return relevantText.toString();
                            })
                            .defaultIfEmpty("No chunks with embeddings was found");
                })
                .onErrorResume(e -> {
                    log.error("Error in embedding search: {}", e.getMessage(), e);
                    return Mono.just("Error searching with embeddings: " + e.getMessage());
                });
    }

    public Flux<Document> getAllDocuments(UUID userID) {
        return documentRepository.findByUserId(userID);
    }

    public Mono<Document> save(Document document) {
        return documentRepository.save(document);
    }

    public Mono<Void> deleteDocument(UUID id) {
        return documentChunkRepository.deleteByDocumentId(id)
                .then(documentRepository.deleteById(id));
    }

    public Flux<DocumentChunk> getChunksByDocumentId(UUID documentId) {
        return documentChunkRepository.findByDocumentId(documentId);
    }

    public Flux<DocumentChunk> getAllChunksByUserId(UUID userId) {
        return documentChunkRepository.findByUserId(userId);
    }

    public Mono<Long> countChunksByDocumentId(UUID documentId) {
        return documentChunkRepository.findByDocumentId(documentId).count();
    }
}