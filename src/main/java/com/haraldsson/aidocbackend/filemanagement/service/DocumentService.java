package com.haraldsson.aidocbackend.filemanagement.service;

import com.haraldsson.aidocbackend.filemanagement.model.Document;
import com.haraldsson.aidocbackend.filemanagement.model.DocumentChunk;
import com.haraldsson.aidocbackend.filemanagement.repository.DocumentChunkRepository;
import com.haraldsson.aidocbackend.filemanagement.repository.DocumentRepository;
import com.haraldsson.aidocbackend.user.model.CustomUser;
import com.haraldsson.aidocbackend.user.service.CustomUserService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final CustomUserService customUserService;
    private final DocumentChunkRepository documentChunkRepository;
    private final EmbeddingService embeddingService;
    private final ExcelProcessorService excelProcessorService;
    private final PowerPointProcessorService powerPointProcessorService;

    @Autowired
    public DocumentService(DocumentRepository documentRepository,
                           CustomUserService customUserService,
                           DocumentChunkRepository documentChunkRepository,
                           EmbeddingService embeddingService, ExcelProcessorService excelProcessorService, PowerPointProcessorService powerPointProcessorService) {
        this.documentRepository = documentRepository;
        this.customUserService = customUserService;
        this.documentChunkRepository = documentChunkRepository;
        this.embeddingService = embeddingService;
        this.excelProcessorService = excelProcessorService;
        this.powerPointProcessorService = powerPointProcessorService;
    }

    private Mono<Document> processAndSavePdf(FilePart filePart, CustomUser user) {
        return convertFilePartToBytes(filePart)
                .flatMap(this::extractTextFromPdf)
                .flatMap(text -> {
                    Document document = new Document(filePart.filename(), text, user.getId());

                    return documentRepository.save(document)
                            .flatMap(savedDoc -> {
                                return createPdfChunks(savedDoc.getId(), user.getId(),
                                        filePart.filename(), text)
                                        .thenReturn(savedDoc);
                            });
                });
    }

    public Mono<Document> processAndSaveFile(FilePart filePart, CustomUser user) {
        String filename = filePart.filename().toLowerCase();

        if (filename.endsWith(".pdf")) {
            return processAndSavePdf(filePart, user);
        } else if (filename.endsWith(".xlsx") || filename.endsWith(".xls")) {
            return processAndSaveExcel(filePart, user);
        } else if (filename.endsWith(".pptx") || filename.endsWith(".ppt")) {
            return processAndSavePowerPoint(filePart, user);
        } else {
            return Mono.error(new RuntimeException("Endast PDF, Excel och PowerPoint-filer stöds."));
        }
    }

    public Mono<Document> processAndSavePowerPoint(FilePart filePart, CustomUser user) {
        return convertFilePartToBytes(filePart)
                .flatMap(bytes -> powerPointProcessorService.extractTextFromPowerPoint(bytes))
                .flatMap(text -> {
                    System.out.println("PowerPoint extraherad text: " + text.length() + " tecken");

                    Document document = new Document(filePart.filename(), text, user.getId());

                    return documentRepository.save(document)
                            .flatMap(savedDoc -> {
                                return createPowerPointChunks(savedDoc.getId(), user.getId(),
                                        filePart.filename(), text)
                                        .thenReturn(savedDoc);
                            });
                });
    }

    private Mono<byte[]> convertFilePartToBytes(FilePart filePart) {

        return filePart.content()
                .collectList()
                .map(dataBuffers -> {
                    int totalSize = dataBuffers.stream().mapToInt(db -> db.readableByteCount()).sum();

                    byte[] bytes = new byte[totalSize];
                    int offset = 0;

                    for (var buffer : dataBuffers) {
                        int count = buffer.readableByteCount();
                        buffer.read(bytes, offset, count);
                        offset += count;
                    }
                    return bytes;
                });
    }

    private Mono<String> extractTextFromPdf(byte[] pdfBytes) {
        return Mono.fromCallable(() -> {
            try (PDDocument document = PDDocument.load(pdfBytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            } catch (IOException e) {
                throw new RuntimeException("PDF processing error: " + e.getMessage(), e);
            }
        });
    }

    public Mono<Document> processAndSaveExcel(FilePart filePart, CustomUser user) {
        return convertFilePartToBytes(filePart)
                .flatMap(bytes -> excelProcessorService.extractTextFromExcel(bytes))
                .flatMap(text -> {
                    System.out.println("Excel extraherad text: " + text.length() + " tecken");

                    Document document = new Document(filePart.filename(), text, user.getId());

                    return documentRepository.save(document)
                            .flatMap(savedDoc -> {
                                return createExcelChunks(savedDoc.getId(), user.getId(),
                                        filePart.filename(), text)
                                        .thenReturn(savedDoc);
                            });
                });
    }

    private Mono<Document> createAndSaveDocument(String filename, String content, UUID id) {
        Document document = new Document(filename, content, id);
        return documentRepository.save(document);
    }

    private Mono<Void> createPowerPointChunks(UUID documentId, UUID userId,
                                              String filename, String powerpointText) {
        System.out.println("=== CREATING POWERPOINT-CHUNKS ===");

        String[] slides = powerpointText.split("--- SLIDE ");

        return Flux.range(1, slides.length - 1)
                .flatMap(i -> {
                    String slideText = slides[i].trim();
                    if (slideText.isEmpty()) return Mono.empty();

                    String fullSlideText = "--- SLIDE " + slideText;

                    System.out.println("Processing PowerPoint-slide " + i + " (" +
                            fullSlideText.length() + " tecken)");

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
                                System.out.println("Saved PowerPoint-chunk for slide " + i);

                                return embeddingService.createEmbedding(fullSlideText)
                                        .flatMap(embedding -> {
                                            if (embedding != null && embedding.length > 0) {
                                                savedChunk.setEmbedding(embedding);
                                                return documentChunkRepository.save(savedChunk);
                                            }
                                            return Mono.just(savedChunk);
                                        })
                                        .onErrorResume(e -> {
                                            System.err.println("Embedding failed: " + e.getMessage());
                                            return Mono.just(savedChunk);
                                        });
                            });
                })
                .then()
                .doOnSuccess(v -> System.out.println("ALL POWER-POINT CHUNKS SAVED"))
                .doOnError(e -> System.err.println("POWERPOINT-CHUNKING FAILED: " + e.getMessage()));
    }

    public Mono<String> getTextByUserId(UUID userId) {

        return documentRepository.findByUserId(userId)
                .map(Document::getContent)
                .reduce(new StringBuilder(), (sb, content) -> sb.append(content).append("\n"))
                .map(StringBuilder::toString)
                .defaultIfEmpty("No documents found for this user");
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
                                    .append(" från ").append(chunk.getFilename())
                                    .append(" ---\n")
                                    .append(chunk.getContent())
                                    .append("\n\n");
                            chunksFound++;
                        }
                    }

                    if (chunksFound == 0 && !allChunks.isEmpty()) {
                        relevantText.append("(Inga specifika matchningar hittades, visar första delarna)\n\n");
                        for (int i = 0; i < Math.min(3, allChunks.size()); i++) {
                            DocumentChunk chunk = allChunks.get(i);
                            relevantText.append("--- Chunk ").append(chunk.getChunkNumber())
                                    .append(" från ").append(chunk.getFilename())
                                    .append(" ---\n")
                                    .append(chunk.getContent())
                                    .append("\n\n");
                        }
                    }

                    return relevantText.toString();
                })
                .defaultIfEmpty("Inga dokument hittades för användaren");
    }

    public Mono<String> findRelevantChunksWithEmbeddings(String question, UUID userId) {
        System.out.println("Searching with embeddings for question: " + question);

        return embeddingService.createEmbedding(question)
                .flatMap(queryEmbedding -> {
                    if (queryEmbedding == null || queryEmbedding.length == 0) {
                        System.out.println("No embedding for question, falling back to keyword search");
                        return findRelevantChunks(question, userId);
                    }

                    System.out.println("Question embedding created: " + queryEmbedding.length + " dimensions");

                    return getAllChunksByUserId(userId)
                            .collectList()
                            .map(allChunks -> {
                                if (allChunks.isEmpty()) {
                                    return "Inga dokument hittades för användaren";
                                }

                                System.out.println("Comparing with " + allChunks.size() + " chunks");

                                List<ChunkWithSimilarity> scoredChunks = new ArrayList<>();

                                for (DocumentChunk chunk : allChunks) {
                                    float[] chunkEmbedding = chunk.getEmbedding();

                                    if (chunkEmbedding != null && chunkEmbedding.length > 0) {
                                        float similarity = cosineSimilarity(queryEmbedding, chunkEmbedding);
                                        scoredChunks.add(new ChunkWithSimilarity(chunk, similarity));
                                    }
                                }

                                scoredChunks.sort((a, b) -> Float.compare(b.similarity, a.similarity));

                                StringBuilder relevantText = new StringBuilder();
                                relevantText.append("Sökte med embeddings - hittade ").append(scoredChunks.size())
                                        .append(" chunks med embeddings\n\n");

                                int chunksToTake = Math.min(5, scoredChunks.size());
                                for (int i = 0; i < chunksToTake; i++) {
                                    ChunkWithSimilarity scored = scoredChunks.get(i);
                                    DocumentChunk chunk = scored.chunk;

                                    relevantText.append("--- Chunk ").append(chunk.getChunkNumber())
                                            .append(" (relevans: ").append(String.format("%.2f", scored.similarity))
                                            .append(") från ").append(chunk.getFilename())
                                            .append(" ---\n")
                                            .append(chunk.getContent())
                                            .append("\n\n");
                                }

                                return relevantText.toString();
                            })
                            .defaultIfEmpty("Inga chunks med embeddings hittades");
                })
                .defaultIfEmpty("Kunde inte skapa embedding för frågan");
    }

    private float cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0f;
        }

        float dotProduct = 0.0f;
        float normA = 0.0f;
        float normB = 0.0f;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return 0.0f;
        }

        return (float) (dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    private static class ChunkWithSimilarity {
        DocumentChunk chunk;
        float similarity;

        ChunkWithSimilarity(DocumentChunk chunk, float similarity) {
            this.chunk = chunk;
            this.similarity = similarity;
        }
    }

    private Mono<Void> createExcelChunks(UUID documentId, UUID userId,
                                         String filename, String excelText) {
        System.out.println("=== SKAPAR EXCEL-CHUNKS ===");

        String[] worksheets = excelText.split("--- WORKSHEET: ");

        return Flux.range(1, worksheets.length - 1)
                .flatMap(i -> {
                    String worksheetText = worksheets[i].trim();
                    if (worksheetText.isEmpty()) return Mono.empty();

                    String fullWorksheetText = "--- WORKSHEET: " + worksheetText;

                    System.out.println("Bearbetar Excel-ark " + i + " (" +
                            fullWorksheetText.length() + " tecken)");

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
                                System.out.println("Sparat Excel-chunk för ark " + i);

                                return embeddingService.createEmbedding(fullWorksheetText)
                                        .flatMap(embedding -> {
                                            if (embedding != null && embedding.length > 0) {
                                                savedChunk.setEmbedding(embedding);
                                                return documentChunkRepository.save(savedChunk);
                                            }
                                            return Mono.just(savedChunk);
                                        })
                                        .onErrorResume(e -> {
                                            System.err.println("Embedding misslyckades: " + e.getMessage());
                                            return Mono.just(savedChunk);
                                        });
                            });
                })
                .then()
                .doOnSuccess(v -> System.out.println("ALLA EXCEL-CHUNKS SPARADE"))
                .doOnError(e -> System.err.println("EXCEL-CHUNKING MISSLYCKADES: " + e.getMessage()));
    }


        private Mono<Void> createPdfChunks(UUID documentId, UUID userId,
                                               String filename, String fullText) {

            if (filename.toLowerCase().endsWith(".xlsx") ||
                    filename.toLowerCase().endsWith(".xls")) {
                return Mono.empty();
            }

            System.out.println("=== START CHUNKING WITH EMBEDDINGS ===");
            System.out.println("Document ID: " + documentId);
            System.out.println("Text length: " + fullText.length());

            int chunkSize = 1000;
            int overlap = 200;

            int numChunks = (int) Math.ceil((double) fullText.length() / (chunkSize - overlap));
            System.out.println("Will create " + numChunks + " chunks");

            return Flux.range(0, numChunks)
                    .flatMap(i -> {
                        try {
                            int start = i * (chunkSize - overlap);
                            int end = Math.min(start + chunkSize, fullText.length());

                            if (start >= fullText.length()) {
                                return Mono.empty();
                            }

                            String chunkText = fullText.substring(start, end);

                            System.out.println("Creating chunk " + (i+1) + ": [" + start + "-" + end + "]");

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
                                        System.out.println("Saved chunk " + savedChunk.getId() + " (#" + savedChunk.getChunkNumber() + ")");

                                        System.out.println("Creating embedding for chunk " + savedChunk.getChunkNumber());
                                        return embeddingService.createEmbedding(chunkText)
                                                .flatMap(embedding -> {
                                                    if (embedding != null && embedding.length > 0) {
                                                        savedChunk.setEmbedding(embedding);
                                                        System.out.println("Got embedding: " + embedding.length + " dimensions");
                                                        return documentChunkRepository.save(savedChunk);
                                                    } else {
                                                        System.out.println("No embedding created for chunk " + savedChunk.getChunkNumber());
                                                        return Mono.just(savedChunk);
                                                    }
                                                })
                                                .onErrorResume(e -> {
                                                    System.err.println("Embedding failed for chunk " + savedChunk.getChunkNumber() + ": " + e.getMessage());
                                                    return Mono.just(savedChunk);
                                                });
                                    });
                        } catch (Exception e) {
                            System.err.println("Exception creating chunk " + i + ": " + e.getMessage());
                            return Mono.empty();
                        }
                    })
                    .then()
                    .doOnSuccess(v -> System.out.println("ALL CHUNKS WITH EMBEDDINGS SAVED SUCCESSFULLY"))
                    .doOnError(e -> System.err.println("CHUNKING FAILED: " + e.getMessage()));
        }
    }

