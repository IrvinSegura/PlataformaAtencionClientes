package com.irvinsegura.supportchat.knowledge.infrastructure.extraction;

import com.irvinsegura.supportchat.knowledge.domain.model.UnreadableDocumentException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;

/**
 * Decodes a text file whatever editor saved it. UTF-8 (with or without BOM) and UTF-16 with BOM are read as such;
 * anything else is handed to Tika's charset detector, which recognizes the Windows-1252 and ISO-8859-1 files that
 * Spanish-speaking businesses still export from older tools ("Información" saved by Excel or Notepad). A binary file
 * renamed to .txt is still rejected, instead of being stored as mojibake.
 */
final class TextDecoding {

    // Below this the detector is guessing; a wrong guess would store garbled text the assistant then quotes
    private static final int MIN_CONFIDENCE = 30;
    // Share of control characters above which the "text" is really binary data
    private static final double MAX_CONTROL_RATIO = 0.01;

    private TextDecoding() {
    }

    static String decode(byte[] content) {
        if (startsWith(content, 0xEF, 0xBB, 0xBF)) {
            return strict(content, 3, StandardCharsets.UTF_8)
                    .orElseThrow(() -> new UnreadableDocumentException("The file is not valid UTF-8 text"));
        }
        if (startsWith(content, 0xFF, 0xFE)) {
            return checked(strict(content, 2, StandardCharsets.UTF_16LE));
        }
        if (startsWith(content, 0xFE, 0xFF)) {
            return checked(strict(content, 2, StandardCharsets.UTF_16BE));
        }
        Optional<String> utf8 = strict(content, 0, StandardCharsets.UTF_8);
        if (utf8.isPresent()) {
            return checked(utf8);
        }
        return checked(detected(content));
    }

    private static Optional<String> detected(byte[] content) {
        CharsetMatch match = new CharsetDetector().setText(content).detect();
        if (match == null || match.getConfidence() < MIN_CONFIDENCE || !Charset.isSupported(match.getName())) {
            return Optional.empty();
        }
        return strict(content, 0, Charset.forName(match.getName()));
    }

    private static Optional<String> strict(byte[] content, int offset, Charset charset) {
        try {
            return Optional.of(charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content, offset, content.length - offset))
                    .toString());
        } catch (CharacterCodingException e) {
            return Optional.empty();
        }
    }

    private static String checked(Optional<String> text) {
        String decoded = text.orElseThrow(() -> new UnreadableDocumentException(
                "The file is not readable text; save it as UTF-8 and upload it again"));
        long control = decoded.chars()
                .filter(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t' && c != '\f')
                .count();
        if (control > decoded.length() * MAX_CONTROL_RATIO) {
            throw new UnreadableDocumentException("The file looks like binary data, not text");
        }
        return decoded;
    }

    private static boolean startsWith(byte[] content, int... prefix) {
        if (content.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((content[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
