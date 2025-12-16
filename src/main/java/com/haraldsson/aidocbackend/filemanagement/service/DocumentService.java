package com.haraldsson.aidocbackend.filemanagement.service;

import com.haraldsson.aidocbackend.advice.exceptions.FileProcessingException;
import com.haraldsson.aidocbackend.advice.exceptions.ResourceNotFoundException;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
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

    public DocumentService(DocumentRepository documentRepository,
                           DocumentChunkRepository documentChunkRepository,
                           EmbeddingService embeddingService,
                           ExcelProcessorService excelProcessorService,
                           PowerPointProcessorService powerPointProcessorService,
                           DocumentChunkHelper documentChunkHelper) {
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingService = embeddingService;
        this.excelProcessorService = excelProcessorService;
        this.powerPointProcessorService = powerPointProcessorService;
        this.documentChunkHelper = documentChunkHelper;
    }

    public Mono<Document> processAndSaveFile(FilePart filePart, CustomUser user) {
        String filename = filePart.filename().toLowerCase();

        log.info("Processing file: {} for user: {}", filename, user.getId());

        return convertFilePartToBytes(filePart)
                .flatMap(fileBytes -> {
                    return documentRepository.findByUserIdAndFileName(user.getId(), filename)
                            .collectList()
                            .flatMap(existingDocs -> {
                                if (!existingDocs.isEmpty()) {
                                    log.info("Deleting {} existing document(s) before processing new file: {}",
                                            existingDocs.size(), filename);
                                    return Flux.fromIterable(existingDocs)
                                            .flatMap(oldDoc -> deleteDocumentAndChunks(oldDoc.getId()))
                                            .then(processNewFileWithBytes(fileBytes, filePart, user, filename));
                                } else {
                                    return processNewFileWithBytes(fileBytes, filePart, user, filename);
                                }
                            });
                })
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(throwable -> !(throwable instanceof FileProcessingException)));
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

    private Mono<byte[]> convertFilePartToBytes(FilePart filePart) {
        log.info("Converting file part: {}", filePart.filename());

        return filePart.content()
                .collectList()
                .map(dataBuffers -> {
                    int totalSize = dataBuffers.stream()
                            .mapToInt(db -> {
                                int count = db.readableByteCount();
                                log.debug("Buffer size: {}", count);
                                return count;
                            })
                            .sum();

                    log.info("Total file size from buffers: {} bytes", totalSize);

                    if (totalSize == 0) {
                        log.error("FILE IS 0 BYTES! Something wrong with upload!");
                        throw new FileProcessingException(
                                "Uploaded file is empty (0 bytes): " + filePart.filename());
                    }

                    byte[] bytes = new byte[totalSize];
                    int offset = 0;

                    for (var buffer : dataBuffers) {
                        int count = buffer.readableByteCount();
                        log.debug("Reading buffer: {} bytes, offset: {}", count, offset);

                        buffer.read(bytes, offset, count);
                        offset += count;

                        log.debug("After read - offset: {}, buffer readable: {}",
                                offset, buffer.readableByteCount());

                        if (buffer instanceof io.netty.buffer.ByteBufHolder) {
                            ((io.netty.buffer.ByteBufHolder) buffer).release();
                            log.debug("Buffer released");
                        }
                    }

                    log.info("File converted successfully: {} bytes total", bytes.length);

                    if (bytes.length >= 10) {
                        StringBuilder hex = new StringBuilder();
                        for (int i = 0; i < 10; i++) {
                            hex.append(String.format("%02X ", bytes[i]));
                        }
                        log.info("First 10 bytes (hex): {}", hex.toString());
                    }

                    return bytes;
                })
                .doOnError(e -> log.error("Error converting file to bytes: {}", e.getMessage()));
    }

    private Mono<String> extractTextFromPdf(byte[] pdfBytes) {
        return Mono.fromCallable(() -> {
            try (PDDocument document = PDDocument.load(pdfBytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            } catch (IOException e) {
                throw new FileProcessingException("PDF processing error", e);
            }
        });
    }

    private Mono<Void> createPdfChunks(UUID documentId, UUID userId,
                                       String filename, String fullText) {

        String cleanedText = documentChunkHelper.cleanTextForDatabase(fullText);

        log.info("=== START PDF CHUNKING ===");
        log.info("Document ID: {}", documentId);
        log.info("Text length: {}", cleanedText.length());

        int chunkSize = 1000;
        int overlap = 200;
        int numChunks = (int) Math.ceil((double) cleanedText.length() / (chunkSize - overlap));
        log.info("Will create {} chunks", numChunks);

        return Flux.range(0, numChunks)
                .delayElements(Duration.ofMillis(50))
                .flatMap(i -> {
                    try {
                        int start = i * (chunkSize - overlap);
                        int end = Math.min(start + chunkSize, cleanedText.length());

                        if (start >= cleanedText.length()) {
                            return Mono.empty();
                        }

                        String chunkText = cleanedText.substring(start, end);
                        log.debug("Creating chunk {}: [{}-{}]", (i + 1), start, end);

                        DocumentChunk chunk = new DocumentChunk();
                        chunk.setDocumentId(documentId);
                        chunk.setUserId(userId);
                        chunk.setFilename(filename);
                        chunk.setContent(chunkText);
                        chunk.setChunkNumber(i + 1);
                        chunk.setStartIndex(start);
                        chunk.setEndIndex(end);

                        return documentChunkRepository.save(chunk)
                                .flatMap(savedChunk -> {
                                    log.debug("Saved chunk {} (#{})", savedChunk.getId(), savedChunk.getChunkNumber());
                                    log.debug("Creating embedding for chunk {}", savedChunk.getChunkNumber());

                                    return embeddingService.createEmbedding(chunkText)
                                            .flatMap(embedding -> {
                                                if (embedding != null && embedding.length > 0) {
                                                    savedChunk.setEmbedding(embedding);
                                                    log.debug("Got embedding: {} dimensions", embedding.length);
                                                    return documentChunkRepository.save(savedChunk);
                                                } else {
                                                    log.warn("No embedding created for chunk {}", savedChunk.getChunkNumber());
                                                    return Mono.just(savedChunk);
                                                }
                                            })
                                            .onErrorResume(e -> {
                                                log.error("Embedding failed for chunk {}: {}",
                                                        savedChunk.getChunkNumber(), e.getMessage());
                                                return Mono.just(savedChunk);
                                            });
                                });
                    } catch (Exception e) {
                        log.error("Exception creating chunk {}: {}", i, e.getMessage());
                        return Mono.empty();
                    }
                })
                .then()
                .doOnSuccess(v -> log.info("ALL PDF CHUNKS SAVED SUCCESSFULLY"))
                .doOnError(e -> log.error("PDF CHUNKING FAILED: {}", e.getMessage()));
    }

    private Mono<Void> createExcelChunks(UUID documentId, UUID userId,
                                         String filename, String excelText) {
        log.info("=== CREATING EXCEL-CHUNKS ===");
        String[] worksheets = excelText.split("--- WORKSHEET: ");

        return Flux.range(1, worksheets.length - 1)
                .flatMap(i -> {
                    String worksheetText = worksheets[i].trim();
                    if (worksheetText.isEmpty()) return Mono.empty();

                    String fullWorksheetText = "--- WORKSHEET: " + worksheetText;
                    log.debug("Processing Excel-page {} ({} signs)", i, fullWorksheetText.length());

                    DocumentChunk chunk = new DocumentChunk();
                    chunk.setDocumentId(documentId);
                    chunk.setUserId(userId);
                    chunk.setFilename(filename);
                    chunk.setContent(fullWorksheetText);
                    chunk.setChunkNumber(i);
                    chunk.setStartIndex(0);
                    chunk.setEndIndex(fullWorksheetText.length());

                    return documentChunkRepository.save(chunk)
                            .flatMap(savedChunk -> {
                                log.debug("Saved Excel-chunk for page {}", i);
                                return embeddingService.createEmbedding(fullWorksheetText)
                                        .flatMap(embedding -> {
                                            if (embedding != null && embedding.length > 0) {
                                                savedChunk.setEmbedding(embedding);
                                                return documentChunkRepository.save(savedChunk);
                                            }
                                            return Mono.just(savedChunk);
                                        })
                                        .onErrorResume(e -> {
                                            log.error("Embedding failed: {}", e.getMessage());
                                            return Mono.just(savedChunk);
                                        });
                            });
                })
                .then()
                .doOnSuccess(v -> log.info("ALL EXCEL-CHUNKS SAVED"))
                .doOnError(e -> log.error("EXCEL-CHUNKING FAILED: {}", e.getMessage()));
    }

    private Mono<Void> createPowerPointChunks(UUID documentId, UUID userId,
                                              String filename, String powerpointText) {
        log.info("=== CREATING POWERPOINT-CHUNKS ===");
        String[] slides = powerpointText.split("--- SLIDE ");

        return Flux.range(1, slides.length - 1)
                .flatMap(i -> {
                    String slideText = slides[i].trim();
                    if (slideText.isEmpty()) return Mono.empty();

                    String fullSlideText = "--- SLIDE " + slideText;
                    log.debug("Processing PowerPoint-slide {} ({} signs)", i, fullSlideText.length());

                    DocumentChunk chunk = new DocumentChunk();
                    chunk.setDocumentId(documentId);
                    chunk.setUserId(userId);
                    chunk.setFilename(filename);
                    chunk.setContent(fullSlideText);
                    chunk.setChunkNumber(i);
                    chunk.setStartIndex(0);
                    chunk.setEndIndex(fullSlideText.length());

                    return documentChunkRepository.save(chunk)
                            .flatMap(savedChunk -> {
                                log.debug("Saved PowerPoint-chunk for slide {}", i);
                                return embeddingService.createEmbedding(fullSlideText)
                                        .flatMap(embedding -> {
                                            if (embedding != null && embedding.length > 0) {
                                                savedChunk.setEmbedding(embedding);
                                                return documentChunkRepository.save(savedChunk);
                                            }
                                            return Mono.just(savedChunk);
                                        })
                                        .onErrorResume(e -> {
                                            log.error("Embedding failed: {}", e.getMessage());
                                            return Mono.just(savedChunk);
                                        });
                            });
                })
                .then()
                .doOnSuccess(v -> log.info("ALL POWER-POINT CHUNKS SAVED"))
                .doOnError(e -> log.error("POWERPOINT-CHUNKING FAILED: {}", e.getMessage()));
    }

    public Mono<String> getTextByUserId(UUID userId) {
        return documentRepository.findByUserId(userId)
                .map(Document::getContent)
                .reduce(new StringBuilder(), (sb, content) -> sb.append(content).append("\n"))
                .map(StringBuilder::toString)
                .defaultIfEmpty("No documents found for this user");
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