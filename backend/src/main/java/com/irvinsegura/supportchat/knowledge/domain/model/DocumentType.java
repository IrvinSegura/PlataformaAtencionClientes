package com.irvinsegura.supportchat.knowledge.domain.model;

import java.util.Arrays;
import java.util.Locale;

/**
 * Supported formats. The type is decided from the file's content and name, never from the Content-Type the client
 * sent, which costs nothing to fake: binary formats must also start with their signature, so a renamed file is
 * rejected before it reaches a parser.
 */
public enum DocumentType {

    PDF("application/pdf"),
    MARKDOWN("text/markdown"),
    PLAIN_TEXT("text/plain"),
    HTML("text/html"),
    WORD("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    LEGACY_WORD("application/msword"),
    SPREADSHEET("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    LEGACY_SPREADSHEET("application/vnd.ms-excel");

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    // Office Open XML files are ZIP archives
    private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};
    // .doc and .xls are OLE2 compound files
    private static final byte[] OLE2_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1,
            0x1A, (byte) 0xE1};

    private final String mediaType;

    DocumentType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String mediaType() {
        return mediaType;
    }

    /**
     * Whether the format is a spreadsheet, whose rows are read as records rather than as prose.
     */
    public boolean isSpreadsheet() {
        return this == SPREADSHEET || this == LEGACY_SPREADSHEET;
    }

    public static DocumentType detect(String fileName, byte[] content) {
        if (startsWith(content, PDF_MAGIC)) {
            return PDF;
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (startsWith(content, ZIP_MAGIC)) {
            if (name.endsWith(".docx")) {
                return WORD;
            }
            if (name.endsWith(".xlsx")) {
                return SPREADSHEET;
            }
            throw new UnsupportedDocumentException();
        }
        if (startsWith(content, OLE2_MAGIC)) {
            if (name.endsWith(".doc")) {
                return LEGACY_WORD;
            }
            if (name.endsWith(".xls")) {
                return LEGACY_SPREADSHEET;
            }
            throw new UnsupportedDocumentException();
        }
        if (name.endsWith(".md") || name.endsWith(".markdown")) {
            return MARKDOWN;
        }
        if (name.endsWith(".txt")) {
            return PLAIN_TEXT;
        }
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            return HTML;
        }
        throw new UnsupportedDocumentException();
    }

    public static DocumentType fromMediaType(String mediaType) {
        for (DocumentType type : values()) {
            if (type.mediaType.equals(mediaType)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown media type " + mediaType);
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        return content.length >= magic.length && Arrays.equals(content, 0, magic.length, magic, 0, magic.length);
    }
}
