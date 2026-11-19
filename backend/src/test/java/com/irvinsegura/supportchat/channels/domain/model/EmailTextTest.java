package com.irvinsegura.supportchat.channels.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailTextTest {

    @Test
    void theQuotedThreadBelowAnAttributionIsCutOff() {
        assertThat(EmailText.withoutQuotes("""
                ¿Y cuánto tarda?

                On Tue, 10 Nov 2026 at 9:00, Soporte <soporte@example.com> wrote:
                > Llega en 3 días.
                """)).isEqualTo("¿Y cuánto tarda?");
        assertThat(EmailText.withoutQuotes("""
                Gracias

                El mar, 10 nov 2026 a las 9:00, Soporte (<soporte@example.com>) escribió:
                Llega en 3 días.
                """)).isEqualTo("Gracias");
    }

    @Test
    void anAttributionWrappedOverTwoLinesIsFound() {
        assertThat(EmailText.withoutQuotes("""
                Perfecto
                El mar, 10 nov 2026 a las 9:00, Soporte de la tienda <soporte@example.com>
                escribió:
                Llega en 3 días.
                """)).isEqualTo("Perfecto");
    }

    @Test
    void textThatOnlyStartsLikeAnAttributionIsKept() {
        assertThat(EmailText.withoutQuotes("El pedido no llegó\nOn hold since Monday"))
                .isEqualTo("El pedido no llegó\nOn hold since Monday");
    }

    @Test
    void outlookHeadersSeparatorsAndSignaturesEndTheMessage() {
        assertThat(EmailText.withoutQuotes("""
                Sigue sin llegar.

                From: Soporte <soporte@example.com>
                Sent: Tuesday, November 10, 2026 9:00 AM
                Subject: Tu pedido
                """)).isEqualTo("Sigue sin llegar.");
        assertThat(EmailText.withoutQuotes("Ok\n-----Original Message-----\nHola")).isEqualTo("Ok");
        assertThat(EmailText.withoutQuotes("Ok\r\n-- \r\nAna López\r\nGerente")).isEqualTo("Ok");
        // "--" without the space is not the signature mark
        assertThat(EmailText.withoutQuotes("Ok\n--\nAna")).isEqualTo("Ok\n--\nAna");
    }

    @Test
    void quotedLinesAreDropped() {
        assertThat(EmailText.withoutQuotes("> ¿Algo más?\nSí, el número de guía\n>> antes\n\n\n\nGracias"))
                .isEqualTo("Sí, el número de guía\n\nGracias");
        assertThat(EmailText.withoutQuotes(null)).isEmpty();
    }

    @Test
    void htmlBecomesTextWithoutQuotesStylesOrEntities() {
        String html = """
                <html><head><style>p { color: red }</style><title>x</title></head><body>
                <div>Hola,&nbsp;¿dónde está mi pedido &#35;123?</div><div><br></div>
                <p>Precio &lt; 10 &amp; envío &#x2014; gratis</p>
                <script>alert(1)</script>
                <blockquote type="cite"><p>Mensaje anterior</p></blockquote>
                </body></html>""";

        assertThat(EmailText.fromHtml(html))
                .isEqualTo("Hola, ¿dónde está mi pedido #123?\n\nPrecio < 10 & envío — gratis");
        assertThat(EmailText.fromHtml("&amp;lt;b&amp;gt;")).isEqualTo("&lt;b&gt;");
        assertThat(EmailText.fromHtml(null)).isEmpty();
    }
}
