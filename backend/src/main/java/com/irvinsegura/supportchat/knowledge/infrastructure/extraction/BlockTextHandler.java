package com.irvinsegura.supportchat.knowledge.infrastructure.extraction;

import java.util.Set;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Text of a word-processing document, one paragraph per block: paragraphs, headings, list items and table rows
 * (cells separated by "|"). Tika's {@code <head>} (file metadata) is left out.
 */
final class BlockTextHandler extends LimitedTextHandler {

    private static final Set<String> BLOCKS = Set.of("p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "tr", "table",
            "div", "blockquote", "pre", "ul", "ol", "dl", "dt", "dd");

    private boolean inHead;

    BlockTextHandler(int maxChars) {
        super(maxChars);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        switch (localName) {
            case "head" -> inHead = true;
            case "br" -> text.append('\n');
            case "td", "th" -> text.append(' ');
            case "li" -> {
                paragraph();
                text.append("- ");
            }
            default -> {
                if (BLOCKS.contains(localName)) {
                    paragraph();
                }
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        if (localName.equals("head")) {
            inHead = false;
        } else if (localName.equals("td") || localName.equals("th")) {
            text.append(" |");
        } else if (BLOCKS.contains(localName)) {
            paragraph();
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (!inHead) {
            text.append(ch, start, length);
            checkLimit();
        }
    }
}
