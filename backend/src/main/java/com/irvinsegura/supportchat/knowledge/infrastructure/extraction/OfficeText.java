package com.irvinsegura.supportchat.knowledge.infrastructure.extraction;

import com.irvinsegura.supportchat.knowledge.domain.model.DocumentType;
import com.irvinsegura.supportchat.knowledge.domain.model.KnowledgeLimits;
import com.irvinsegura.supportchat.knowledge.domain.model.UnreadableDocumentException;
import java.io.IOException;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.microsoft.OfficeParser;
import org.apache.tika.parser.microsoft.OfficeParserConfig;
import org.apache.tika.parser.microsoft.ooxml.OOXMLParser;
import org.xml.sax.SAXException;

/**
 * Word and Excel files through Apache Tika. The parser is chosen from the type already detected, not by Tika's own
 * detection, so a file is only ever read as the format it was accepted as.
 * <p>
 * Tika streams .docx (SAX mode) and .xlsx, so a large file does not have to fit in memory as an object model, and
 * the handlers stop at {@link KnowledgeLimits#MAX_EXTRACTED_CHARS}, which also defuses decompression bombs. Embedded
 * files (images, other documents pasted into a document) are skipped: no parser is offered for them.
 */
final class OfficeText {

    private OfficeText() {
    }

    static String extract(DocumentType type, byte[] content) {
        Parser parser = switch (type) {
            case WORD, SPREADSHEET -> new OOXMLParser();
            case LEGACY_WORD, LEGACY_SPREADSHEET -> new OfficeParser();
            default -> throw new IllegalArgumentException("Not an Office format: " + type);
        };
        OfficeParserConfig config = new OfficeParserConfig();
        config.setUseSAXDocxExtractor(true);
        config.setIncludeHeadersAndFooters(true);
        config.setIncludeDeletedContent(false);
        config.setIncludeMoveFromContent(false);
        config.setExtractMacros(false);
        ParseContext context = new ParseContext();
        context.set(OfficeParserConfig.class, config);

        LimitedTextHandler handler = type.isSpreadsheet()
                ? new SpreadsheetTextHandler(KnowledgeLimits.MAX_EXTRACTED_CHARS)
                : new BlockTextHandler(KnowledgeLimits.MAX_EXTRACTED_CHARS);
        try (TikaInputStream input = TikaInputStream.get(content)) {
            parser.parse(input, handler, new Metadata(), context);
        } catch (EncryptedDocumentException e) {
            throw new UnreadableDocumentException("The file is password protected");
        } catch (SAXException | TikaException | IOException | RuntimeException e) {
            // Includes the handler's own stop at the size limit
            if (handler.limitReached()) {
                throw new UnreadableDocumentException("The file has too much text; split it into smaller files");
            }
            throw damaged(type);
        }
        String text = handler.text().strip();
        if (text.isEmpty()) {
            throw new UnreadableDocumentException(type.isSpreadsheet()
                    ? "The spreadsheet has no cells with text"
                    : "The document has no text");
        }
        return text;
    }

    private static UnreadableDocumentException damaged(DocumentType type) {
        return new UnreadableDocumentException(type.isSpreadsheet()
                ? "The spreadsheet is damaged or not an Excel file"
                : "The document is damaged or not a Word file");
    }
}
