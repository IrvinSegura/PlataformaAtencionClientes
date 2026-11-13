package com.irvinsegura.supportchat.shared.web;

import io.swagger.v3.oas.annotations.Hidden;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * The platform's public privacy policy, the page Meta and app stores ask for before an app goes live, and its terms of
 * service, which operators accept on signing up. Each is rendered once at startup from a template with the operator's
 * name, contact and jurisdiction, all HTML-escaped.
 */
@RestController
@Hidden
class LegalController {

    private static final Logger log = LoggerFactory.getLogger(LegalController.class);
    // The API's policy forbids everything; this page only needs its own inline styles
    static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

    private final String privacyPolicy;
    private final String terms;

    LegalController(LegalProperties legal) {
        if (legal.contactEmail().isEmpty()) {
            log.warn("supportchat.legal.contact-email is not set: the legal pages show no way to reach you");
        }
        this.privacyPolicy = render(template("legal/privacidad.html"), legal);
        this.terms = render(template("legal/terminos.html"), legal);
    }

    @GetMapping(value = "/legal/privacidad", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> privacyPolicy() {
        return page(privacyPolicy);
    }

    @GetMapping(value = "/legal/terminos", produces = MediaType.TEXT_HTML_VALUE)
    ResponseEntity<String> terms() {
        return page(terms);
    }

    private static ResponseEntity<String> page(String html) {
        return ResponseEntity.ok()
                .header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }

    static String render(String template, LegalProperties legal) {
        String contact = legal.contactEmail().isEmpty()
                ? "<em>(correo de contacto sin configurar)</em>"
                // Cloudflare would swap the address for a script-decoded link this page's CSP blocks
                : "<!--email_off--><a href=\"mailto:" + escape(legal.contactEmail()) + "\">"
                        + escape(legal.contactEmail()) + "</a><!--/email_off-->";
        return template
                .replace("{{operator}}", escape(legal.operatorName()))
                .replace("{{updated}}", escape(legal.updated()))
                .replace("{{termsUpdated}}", escape(legal.termsUpdated()))
                .replace("{{jurisdiction}}", escape(legal.jurisdiction()))
                .replace("{{contact}}", contact);
    }

    // Only the characters that break markup; accented letters stay as they are in a UTF-8 page
    private static String escape(String text) {
        return HtmlUtils.htmlEscape(text, StandardCharsets.UTF_8.name());
    }

    private static String template(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing legal page " + path, e);
        }
    }
}
