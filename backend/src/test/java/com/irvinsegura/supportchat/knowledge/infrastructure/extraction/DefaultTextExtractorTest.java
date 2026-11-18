package com.irvinsegura.supportchat.knowledge.infrastructure.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.supportchat.knowledge.domain.model.DocumentType;
import com.irvinsegura.supportchat.knowledge.domain.model.UnreadableDocumentException;
import com.irvinsegura.supportchat.knowledge.domain.model.UnsupportedDocumentException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

class DefaultTextExtractorTest {

    private final DefaultTextExtractor extractor = new DefaultTextExtractor();

    @Test
    void wordDocumentKeepsParagraphsAndTables() throws IOException {
        byte[] docx = docx();

        DocumentType type = DocumentType.detect("politicas.docx", docx);
        String text = extractor.extract(type, docx);

        assertThat(type).isEqualTo(DocumentType.WORD);
        assertThat(text).contains("Política de devoluciones")
                .contains("Aceptamos devoluciones durante 30 días.")
                .contains("Envío estándar")
                .contains("3 a 5 días hábiles");
        // Paragraphs stay apart so the chunker can keep them whole
        assertThat(text).contains("Política de devoluciones\n\n");
    }

    @Test
    void spreadsheetRowsReadAsColumnValuePairs() throws IOException {
        byte[] xlsx = workbook(new XSSFWorkbook());

        DocumentType type = DocumentType.detect("precios.xlsx", xlsx);
        String text = extractor.extract(type, xlsx);

        assertThat(type).isEqualTo(DocumentType.SPREADSHEET);
        assertThat(text).contains("# Menú")
                .contains("Producto: Pizza grande")
                .contains("Precio: 189")
                .contains("Producto: Refresco");
        assertThat(text).doesNotContain("Pizza grande | 189");
    }

    @Test
    void legacyExcelIsReadToo() throws IOException {
        byte[] xls = workbook(new HSSFWorkbook());

        DocumentType type = DocumentType.detect("precios.xls", xls);
        String text = extractor.extract(type, xls);

        assertThat(type).isEqualTo(DocumentType.LEGACY_SPREADSHEET);
        assertThat(text).contains("Producto: Pizza grande").contains("Precio: 189");
    }

    @Test
    void htmlDropsScriptsMenusAndFormsButKeepsTheFooter() {
        byte[] html = """
                <html><head><title>Horarios</title><style>p{color:red}</style></head>
                <body>
                  <nav><a href="/">Inicio</a><a href="/menu">Menú</a></nav>
                  <h1>Horarios</h1>
                  <p>Abrimos de lunes a sábado de 9 a 21 h.</p>
                  <script>trackVisitor()</script>
                  <form><input name="email"><button>Suscribirme</button></form>
                  <footer>Av. Reforma 123, CDMX</footer>
                </body></html>
                """.getBytes(StandardCharsets.UTF_8);

        String text = extractor.extract(DocumentType.detect("horarios.html", html), html);

        assertThat(text).contains("Horarios").contains("Abrimos de lunes a sábado de 9 a 21 h.")
                .contains("Av. Reforma 123, CDMX");
        assertThat(text).doesNotContain("trackVisitor").doesNotContain("Suscribirme").doesNotContain("Inicio")
                .doesNotContain("color:red");
    }

    @Test
    void textInALegacyEncodingIsDecoded() {
        byte[] latin1 = "Devolución en 30 días, sin preguntas.".getBytes(Charset.forName("windows-1252"));

        String text = extractor.extract(DocumentType.detect("faq.txt", latin1), latin1);

        assertThat(text).contains("Devolución en 30 días");
    }

    @Test
    void binaryDataNamedTxtIsRejected() {
        byte[] binary = new byte[512];
        for (int i = 0; i < binary.length; i++) {
            binary[i] = (byte) (i * 37);
        }

        assertThatThrownBy(() -> extractor.extract(DocumentType.PLAIN_TEXT, binary))
                .isInstanceOf(UnreadableDocumentException.class);
    }

    @Test
    void aRenamedFileIsNotTrustedByItsExtension() throws IOException {
        byte[] xlsx = workbook(new XSSFWorkbook());

        // A ZIP that claims to be a PDF or a text file is not read as one
        assertThatThrownBy(() -> DocumentType.detect("precios.pdf", xlsx))
                .isInstanceOf(UnsupportedDocumentException.class);
        assertThatThrownBy(() -> extractor.extract(DocumentType.WORD, "not a zip".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(UnreadableDocumentException.class);
    }

    private static byte[] docx() throws IOException {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Política de devoluciones");
            document.createParagraph().createRun().setText("Aceptamos devoluciones durante 30 días.");
            XWPFTable table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("Envío");
            table.getRow(0).getCell(1).setText("Tiempo");
            table.getRow(1).getCell(0).setText("Envío estándar");
            table.getRow(1).getCell(1).setText("3 a 5 días hábiles");
            document.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] workbook(Workbook workbook) throws IOException {
        try (workbook; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Menú");
            String[][] rows = {{"Producto", "Precio"}, {"Pizza grande", "189"}, {"Refresco", "35"}};
            for (int i = 0; i < rows.length; i++) {
                Row row = sheet.createRow(i);
                for (int j = 0; j < rows[i].length; j++) {
                    row.createCell(j).setCellValue(rows[i][j]);
                }
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
