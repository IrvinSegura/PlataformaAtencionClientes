package com.irvinsegura.supportchat.knowledge.infrastructure.extraction;

import java.util.ArrayList;
import java.util.List;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * Text of a spreadsheet, written so each row still makes sense on its own once the document is cut into passages.
 * The first row with text in each sheet is taken as the column names, and every later row becomes one paragraph of
 * "Column: value" pairs: a price list then reads "Producto: Pizza grande; Precio: 189" instead of a bare
 * "Pizza grande | 189" whose meaning was in a header several passages away. Each sheet starts with its name.
 * <p>
 * Reads Tika's XHTML for spreadsheets: one {@code <div>} per sheet with an {@code <h1>} name and a {@code <table>}.
 */
final class SpreadsheetTextHandler extends LimitedTextHandler {

    private final StringBuilder sheetName = new StringBuilder();
    private final StringBuilder cell = new StringBuilder();
    private List<String> header;
    private List<String> row;
    private int dataRows;
    private boolean inHead;
    private boolean inSheetName;
    private boolean inCell;

    SpreadsheetTextHandler(int maxChars) {
        super(maxChars);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        switch (localName) {
            case "head" -> inHead = true;
            case "h1" -> {
                inSheetName = true;
                sheetName.setLength(0);
            }
            case "table" -> {
                header = null;
                dataRows = 0;
            }
            case "tr" -> row = new ArrayList<>();
            case "td", "th" -> {
                inCell = true;
                cell.setLength(0);
            }
            default -> {
                // Other elements carry no structure worth keeping
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        switch (localName) {
            case "head" -> inHead = false;
            case "h1" -> {
                inSheetName = false;
                String name = sheetName.toString().strip();
                if (!name.isEmpty()) {
                    paragraph();
                    text.append("# ").append(name);
                    paragraph();
                }
            }
            case "td", "th" -> {
                inCell = false;
                if (row != null) {
                    row.add(cell.toString().strip());
                }
            }
            case "tr" -> {
                endRow();
                checkLimit();
            }
            case "table" -> {
                // A sheet with a single row has no records: keep that row as it is
                if (header != null && dataRows == 0) {
                    text.append(String.join(" | ", header.stream().filter(value -> !value.isEmpty()).toList()));
                    paragraph();
                }
                header = null;
            }
            default -> {
                // Nothing to close
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (inHead) {
            return;
        }
        if (inCell) {
            cell.append(ch, start, length);
        } else if (inSheetName) {
            sheetName.append(ch, start, length);
        } else {
            // Text outside the grid: headers, footers, comments
            String loose = new String(ch, start, length).strip();
            if (!loose.isEmpty()) {
                text.append(loose);
                paragraph();
                checkLimit();
            }
        }
    }

    private void endRow() {
        List<String> values = row;
        row = null;
        if (values == null || values.stream().allMatch(String::isEmpty)) {
            return;
        }
        if (header == null) {
            header = values;
            return;
        }
        List<String> pairs = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (value.isEmpty()) {
                continue;
            }
            String name = i < header.size() && !header.get(i).isEmpty() ? header.get(i) : "Column " + (i + 1);
            pairs.add(name + ": " + value);
        }
        text.append(String.join("; ", pairs));
        paragraph();
        dataRows++;
    }
}
