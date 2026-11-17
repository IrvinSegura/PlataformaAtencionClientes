package com.irvinsegura.supportchat.knowledge.infrastructure.extraction;

import com.irvinsegura.supportchat.knowledge.domain.model.DocumentType;
import com.irvinsegura.supportchat.knowledge.domain.model.KnowledgeLimits;
import com.irvinsegura.supportchat.knowledge.domain.model.UnreadableDocumentException;
import com.irvinsegura.supportchat.knowledge.domain.port.TextExtractor;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Picks the reader for each format: PDFBox for PDF, Apache Tika for Word and Excel, jsoup for HTML, and a decoder
 * that recognizes legacy encodings for text files. Every reader marks paragraphs with blank lines, which the chunker
 * keeps whole when it can.
 */
@Component
class DefaultTextExtractor implements TextExtractor {

    @Override
    public String extract(DocumentType type, byte[] content) {
        return switch (type) {
            case PDF -> pdf(content);
            case MARKDOWN, PLAIN_TEXT -> TextDecoding.decode(content);
            case HTML -> HtmlText.extract(content);
            case WORD, LEGACY_WORD, SPREADSHEET, LEGACY_SPREADSHEET -> OfficeText.extract(type, content);
        };
    }

    private static String pdf(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                throw new UnreadableDocumentException("The PDF is password protected");
            }
            if (document.getNumberOfPages() > KnowledgeLimits.MAX_PDF_PAGES) {
                throw new UnreadableDocumentException(
                        "The PDF has more than %d pages".formatted(KnowledgeLimits.MAX_PDF_PAGES));
            }
            PDFTextStripper stripper = new PDFTextStripper();
            // Reading order follows the page layout, so multi-column pages do not interleave their columns
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text.isBlank()) {
                throw new UnreadableDocumentException(
                        "The PDF contains no selectable text; scanned documents need OCR first");
            }
            return text;
        } catch (IOException e) {
            throw new UnreadableDocumentException("The PDF is damaged or not a PDF");
        }
    }
}
