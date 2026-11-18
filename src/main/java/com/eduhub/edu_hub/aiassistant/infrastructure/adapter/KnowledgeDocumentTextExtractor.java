package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import com.eduhub.edu_hub.shared.exception.ErrorCode;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

/** Chỉ đọc văn bản PDF/DOCX/TXT; PDF scan hoặc tệp hỏng bị từ chối, chưa có OCR. */
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentTextExtractor {
    private static final int MAXIMUM_PDF_PAGES = 1000;
    private final AiRagProperties properties;

    public List<DocumentPage> extractPages(String filename, byte[] originalFile) {
        try {
            String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            List<DocumentPage> pages;
            if ("pdf".equals(extension)) {
                if (originalFile.length < 5
                        || !new String(originalFile, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                    throw invalidDocument();
                }
                pages = extractPdfPages(originalFile);
            } else if ("docx".equals(extension)) {
                try (var document = new XWPFDocument(new ByteArrayInputStream(originalFile));
                        var extractor = new XWPFWordExtractor(document)) {
                    pages = List.of(new DocumentPage(null, cleanText(extractor.getText())));
                }
            } else if ("txt".equals(extension)) {
                String text = StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(originalFile))
                        .toString();
                if (text.indexOf('\0') >= 0) throw invalidDocument();
                pages = List.of(new DocumentPage(null, cleanText(text)));
            } else {
                throw invalidDocument();
            }
            long totalCharacters =
                    pages.stream().mapToLong(page -> page.content().length()).sum();
            if (totalCharacters == 0 || totalCharacters > properties.getMaximumExtractedCharacters())
                throw invalidDocument();
            return pages;
        } catch (AiAssistantException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalidDocument();
        }
    }

    private List<DocumentPage> extractPdfPages(byte[] originalFile) throws IOException {
        try (var document = Loader.loadPDF(originalFile)) {
            if (!document.getCurrentAccessPermission().canExtractContent()
                    || document.getNumberOfPages() > MAXIMUM_PDF_PAGES) throw invalidDocument();
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<DocumentPage> pages = new ArrayList<>();
            long totalCharacters = 0;
            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                stripper.setStartPage(pageNumber);
                stripper.setEndPage(pageNumber);
                String text = cleanText(stripper.getText(document));
                totalCharacters += text.length();
                if (totalCharacters > properties.getMaximumExtractedCharacters()) throw invalidDocument();
                if (!text.isBlank()) pages.add(new DocumentPage(pageNumber, text));
            }
            return pages;
        }
    }

    private String cleanText(String text) {
        return text.replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "")
                .strip();
    }

    private AiAssistantException invalidDocument() {
        return new AiAssistantException(ErrorCode.INVALID_AI_DOCUMENT);
    }

    public record DocumentPage(Integer pageNumber, String content) {}
}
