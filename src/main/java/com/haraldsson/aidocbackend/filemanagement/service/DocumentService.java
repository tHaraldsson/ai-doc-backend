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
import java.util.UUID;


@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final CustomUserService customUserService;
    private final DocumentChunkRepository documentChunkRepository;

    @Autowired
    public DocumentService(DocumentRepository documentRepository, CustomUserService customUserService, DocumentChunkRepository documentChunkRepository) {
        this.documentRepository = documentRepository;
        this.customUserService = customUserService;
        this.documentChunkRepository = documentChunkRepository;
    }

    public Mono<Document> processAndSavePdf(FilePart filePart, CustomUser user) {
        return convertFilePartToBytes(filePart)
                .flatMap(this::extractTextFromPdf)
                .flatMap(text -> {
                    Document document = new Document(filePart.filename(), text, user.getId());

                    return documentRepository.save(document)
                            .flatMap(savedDoc -> {
                                return createAndSaveChunks(
                                        savedDoc.getId(),
                                        user.getId(),
                                        filePart.filename(),
                                        text
                                ).thenReturn(savedDoc);
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

    private Mono<Document> createAndSaveDocument(String filename, String content, UUID id) {
        Document document = new Document(filename, content, id);
        return documentRepository.save(document);
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
                    // Extrahera nyckelord från frågan (ord längre än 3 tecken)
                    String[] keywords = question.toLowerCase().split("\\s+");

                    StringBuilder relevantText = new StringBuilder();
                    int chunksFound = 0;

                    // Loopa igenom alla chunks och leta efter nyckelord
                    for (DocumentChunk chunk : allChunks) {
                        String chunkContent = chunk.getContent().toLowerCase();
                        boolean hasKeyword = false;

                        // Kolla om chunken innehåller något nyckelord
                        for (String keyword : keywords) {
                            if (keyword.length() > 3 && chunkContent.contains(keyword)) {
                                hasKeyword = true;
                                break;
                            }
                        }

                        // Om chunken är relevant, lägg till den (max 5 chunks)
                        if (hasKeyword && chunksFound < 5) {
                            relevantText.append("--- Chunk ").append(chunk.getChunkNumber())
                                    .append(" från ").append(chunk.getFilename())
                                    .append(" ---\n")
                                    .append(chunk.getContent())
                                    .append("\n\n");
                            chunksFound++;
                        }
                    }

                    // Om inga chunks hittades med nyckelord, ta första 3 chunks som fallback
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

    public Mono<String> debugChunking(UUID documentId) {
        return getChunksByDocumentId(documentId)
                .collectList()
                .map(chunks -> {
                    StringBuilder debug = new StringBuilder();
                    debug.append("Total chunks: ").append(chunks.size()).append("\n\n");

                    for (DocumentChunk chunk : chunks) {
                        debug.append("Chunk #").append(chunk.getChunkNumber())
                                .append(" (tecken ").append(chunk.getStartIndex())
                                .append("-").append(chunk.getEndIndex()).append("):\n")
                                .append(chunk.getContent().length() > 100 ?
                                        chunk.getContent().substring(0, 100) + "..." :
                                        chunk.getContent())
                                .append("\n\n");
                    }

                    return debug.toString();
                })
                .defaultIfEmpty("Inga chunks hittades för detta dokument");
    }

    private Mono<Void> createAndSaveChunks(UUID documentId, UUID userId,
                                           String filename, String fullText) {
        int chunkSize = 1000;
        int overlap = 200;

        int numChunks = (int) Math.ceil((double) fullText.length() / (chunkSize - overlap));

        System.out.println("=== CHUNKING DEBUG ===");
        System.out.println("Document length: " + fullText.length());
        System.out.println("Calculated chunks: " + numChunks);

        return Flux.range(0, numChunks)
                .flatMap(i -> {
                    int start = i * (chunkSize - overlap);
                    int end = Math.min(start + chunkSize, fullText.length());

                    if (start >= fullText.length()) {
                        return Mono.empty();
                    }

                    String chunkText = fullText.substring(start, end);

                    System.out.println("Creating chunk " + (i+1) + ": " +
                            start + "-" + end + " (" + chunkText.length() + " chars)");

                    DocumentChunk chunk = new DocumentChunk(
                            documentId, userId, filename,
                            chunkText, i + 1, start, end
                    );

                    return documentChunkRepository.save(chunk)
                            .doOnNext(savedChunk -> {
                                System.out.println("Saved chunk ID: " + savedChunk.getId());
                            });
                })
                .then()
                .doOnSuccess(v -> System.out.println("All chunks saved successfully"))
                .doOnError(e -> System.err.println("Error saving chunks: " + e.getMessage()));
    }

}
