package com.eduhub.edu_hub.aiassistant.infrastructure.adapter;

import static org.assertj.core.api.Assertions.*;

import com.eduhub.edu_hub.aiassistant.application.exception.AiAssistantException;
import com.eduhub.edu_hub.aiassistant.infrastructure.config.AiRagProperties;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentTextExtractorTest {
    KnowledgeDocumentTextExtractor extractor = new KnowledgeDocumentTextExtractor(new AiRagProperties());

    @Test
    void readsVietnameseUtf8TextAndRejectsMalformedBytes() {
        var pages = extractor.extractPages(
                "quy-che.txt", "\uFEFFĐiều 1.\r\nQuy định học lại.".getBytes(StandardCharsets.UTF_8));
        assertThat(pages.getFirst().content()).isEqualTo("Điều 1.\nQuy định học lại.");
        assertThatThrownBy(() -> extractor.extractPages("quy-che.txt", new byte[] {(byte) 0xc3, 0x28}))
                .isInstanceOf(AiAssistantException.class);
    }

    @Test
    void readsDocxWithoutExternalServices() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("Điều 2. Quy định đào tạo.");
            document.write(bytes);
        }
        assertThat(extractor
                        .extractPages("quy-che.docx", bytes.toByteArray())
                        .getFirst()
                        .content())
                .contains("Điều 2.", "Quy định đào tạo.");
    }

    @Test
    void preservesPdfPageNumbersAndRejectsPdfWithoutText() throws Exception {
        var textPdf = new ByteArrayOutputStream();
        try (var document = new PDDocument()) {
            var page = new PDPage();
            document.addPage(page);
            try (var stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(30, 700);
                stream.showText("Academic regulations.");
                stream.endText();
            }
            document.save(textPdf);
        }
        var pages = extractor.extractPages("quy-che.pdf", textPdf.toByteArray());
        assertThat(pages.getFirst().pageNumber()).isEqualTo(1);
        assertThat(pages.getFirst().content()).contains("Academic regulations.");
        var imageOnlyPdf = new ByteArrayOutputStream();
        try (var document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(imageOnlyPdf);
        }
        assertThatThrownBy(() -> extractor.extractPages("scan.pdf", imageOnlyPdf.toByteArray()))
                .isInstanceOf(AiAssistantException.class);
    }
}
