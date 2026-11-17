package com.irvinsegura.supportchat.knowledge.infrastructure.extraction;

import com.irvinsegura.supportchat.knowledge.domain.model.KnowledgeLimits;
import com.irvinsegura.supportchat.knowledge.domain.model.UnreadableDocumentException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/**
 * The readable text of a web page. Scripts, styles, menus and forms are dropped; headers and footers are kept, since
 * that is where many businesses put their address, phone and opening hours. Each block (paragraph, heading, list item,
 * table row) becomes its own paragraph.
 */
final class HtmlText {

    // Never text a customer would ask about; navigation menus repeat on every page of a site
    private static final String NOISE = "script, style, noscript, template, svg, canvas, iframe, object, embed, nav, "
            + "form, button, select, [hidden], [aria-hidden=true], [role=navigation]";

    private HtmlText() {
    }

    static String extract(byte[] content) {
        Document page;
        try {
            // The charset comes from the BOM or the page's meta tag, UTF-8 otherwise
            page = Jsoup.parse(new ByteArrayInputStream(content), null, "");
        } catch (IOException e) {
            throw new UnreadableDocumentException("The page could not be read");
        }
        page.select(NOISE).remove();
        StringBuilder text = new StringBuilder();
        String title = page.title().strip();
        if (!title.isEmpty()) {
            text.append(title).append("\n\n");
        }
        NodeTraversor.traverse(new BlockVisitor(text), page.body());
        String result = text.toString().strip();
        if (result.isEmpty() || result.equals(title)) {
            throw new UnreadableDocumentException(
                    "The page has no readable text; it may need JavaScript to show its content");
        }
        return result;
    }

    private static final class BlockVisitor implements NodeVisitor {

        private final StringBuilder text;

        BlockVisitor(StringBuilder text) {
            this.text = text;
        }

        @Override
        public void head(Node node, int depth) {
            if (text.length() > KnowledgeLimits.MAX_EXTRACTED_CHARS) {
                throw new UnreadableDocumentException("The page has too much text");
            }
            if (node instanceof TextNode textNode) {
                boolean preformatted = textNode.parent() instanceof Element parent && parent.nameIs("pre");
                String value = preformatted ? textNode.getWholeText() : textNode.text();
                if (!value.isBlank()) {
                    text.append(value);
                }
            } else if (node instanceof Element element) {
                if (element.nameIs("br")) {
                    text.append('\n');
                } else if (element.isBlock()) {
                    paragraph();
                    if (element.nameIs("li")) {
                        text.append("- ");
                    }
                } else if (element.nameIs("td") || element.nameIs("th")) {
                    text.append(' ');
                }
            }
        }

        @Override
        public void tail(Node node, int depth) {
            if (node instanceof Element element) {
                if (element.isBlock()) {
                    paragraph();
                } else if (element.nameIs("td") || element.nameIs("th")) {
                    text.append(" |");
                }
            }
        }

        private void paragraph() {
            int length = text.length();
            boolean atBreak = length >= 2 && text.charAt(length - 1) == '\n' && text.charAt(length - 2) == '\n';
            if (length > 0 && !atBreak) {
                text.append("\n\n");
            }
        }
    }
}
