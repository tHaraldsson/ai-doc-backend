package com.haraldsson.aidocbackend.filemanagement.service;

import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.util.List;

@Service
public class PowerPointProcessorService {

    public Mono<String> extractTextFromPowerPoint(byte[] pptBytes) {
        return Mono.fromCallable(() -> {
            try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(pptBytes))) {
                StringBuilder result = new StringBuilder();

                result.append("=== POWERPOINT PRESENTATION ===\n\n");
                result.append("Total slides: ").append(ppt.getSlides().size()).append("\n\n");

                List<XSLFSlide> slides = ppt.getSlides();

                for (int i = 0; i < slides.size(); i++) {
                    XSLFSlide slide = slides.get(i);

                    result.append("--- SLIDE ").append(i + 1).append(" ---\n\n");

                    String title = getSlideTitle(slide);
                    if (!title.isEmpty()) {
                        result.append("TITLE: ").append(title).append("\n\n");
                    }

                    String slideText = extractTextFromSlide(slide);
                    result.append(slideText).append("\n\n");

                    if (!slide.getShapes().stream()
                            .filter(shape -> shape instanceof XSLFPictureShape)
                            .toList().isEmpty()) {
                        result.append("(This slide contains images)\n");
                    }

                    result.append("\n");
                }

                return result.toString();
            } catch (Exception e) {
                throw new RuntimeException("PowerPoint processing error: " + e.getMessage(), e);
            }
        });
    }

    private String getSlideTitle(XSLFSlide slide) {
        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                String text = textShape.getText();
                if (text != null && !text.trim().isEmpty()) {
                    return text.trim();
                }
            }
        }
        return "";
    }

    private String extractTextFromSlide(XSLFSlide slide) {
        StringBuilder slideText = new StringBuilder();

        for (XSLFShape shape : slide.getShapes()) {
            if (shape instanceof XSLFTextShape) {
                XSLFTextShape textShape = (XSLFTextShape) shape;
                String text = textShape.getText();
                if (text != null && !text.trim().isEmpty()) {
                    slideText.append(text.trim()).append("\n");
                }
            }
        }

        return slideText.toString();
    }
}