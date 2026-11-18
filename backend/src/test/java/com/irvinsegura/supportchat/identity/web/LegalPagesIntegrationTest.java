package com.irvinsegura.supportchat.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.irvinsegura.supportchat.support.WithExternalServices;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The privacy policy is a public page anyone can open without signing in, the URL Meta asks for before an app goes
 * live, and it names whoever runs the platform without letting that text inject markup.
 */
class LegalPagesIntegrationTest extends WithExternalServices {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void thePrivacyPolicyIsPublicAndMayUseItsOwnStyles() throws Exception {
        MvcResult result = mockMvc.perform(get("/legal/privacy"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith("text/html").containsIgnoringCase("utf-8");
        assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                .isEqualTo(LegalController.CONTENT_SECURITY_POLICY);
        String page = result.getResponse().getContentAsString();
        assertThat(page)
                .contains("Política de privacidad")
                .contains("id=\"eliminacion\"")
                .contains("Plataforma de Atención al Cliente")
                .doesNotContain("{{");
    }

    @Test
    void theTermsOfServiceArePublicAndNameTheJurisdiction() throws Exception {
        MvcResult result = mockMvc.perform(get("/legal/terms"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getHeader("Content-Security-Policy"))
                .isEqualTo(LegalController.CONTENT_SECURITY_POLICY);
        assertThat(result.getResponse().getContentAsString())
                .contains("Términos y condiciones", "11 de noviembre de 2026", "Estados Unidos Mexicanos",
                        "href=\"/legal/privacy\"", "id=\"planes\"")
                .doesNotContain("{{");
    }

    @Test
    void linkCheckersAskingWithHeadAreAnsweredToo() throws Exception {
        mockMvc.perform(head("/legal/privacy")).andExpect(status().isOk());
        mockMvc.perform(head("/legal/terms")).andExpect(status().isOk());
    }

    @Test
    void theFormerSpanishAddressesMoveToTheNewOnes() throws Exception {
        mockMvc.perform(get("/legal/privacidad"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/legal/privacy"));
        mockMvc.perform(get("/legal/terminos"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/legal/terms"));
    }

    @Test
    void theContactIsKeptOutOfCloudflaresEmailRewriting() {
        String page = LegalController.render("{{contact}}",
                new LegalProperties(null, "privacy@acme.test", null, null, null, null));

        assertThat(page).startsWith("<!--email_off-->").endsWith("<!--/email_off-->");
    }

    @Test
    void theOperatorAndContactAreEscaped() {
        String page = LegalController.render("{{operator}} · {{contact}} · {{updated}}",
                new LegalProperties("Acme <script>", "privacy@acme.test\"><b>", "1 de enero", null, null, null));

        assertThat(page)
                .startsWith("Acme &lt;script&gt;")
                .contains("href=\"mailto:privacy@acme.test&quot;&gt;&lt;b&gt;\"")
                .endsWith("1 de enero")
                .doesNotContain("<script>", "<b>");
    }

    @Test
    void withoutAContactThePageSaysSoInsteadOfLeavingAPlaceholder() {
        String page = LegalController.render("{{contact}}", new LegalProperties(null, " ", null, null, null, null));

        assertThat(page).contains("sin configurar").doesNotContain("mailto:");
    }

    @Test
    void productionDoesNotStartWithoutAContact() {
        assertThatThrownBy(() -> LegalController.checkContact("", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LEGAL_CONTACT_EMAIL");
        assertThatCode(() -> LegalController.checkContact("", false)).doesNotThrowAnyException();
        assertThatCode(() -> LegalController.checkContact("privacidad@acme.mx", true)).doesNotThrowAnyException();
    }

    @Test
    void aContactThatIsNotAnEmailAddressIsRefused() {
        assertThatThrownBy(() -> LegalController.checkContact("privacidad@acme", false))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> LegalController.checkContact("privacidad acme.mx", true))
                .isInstanceOf(IllegalStateException.class);
    }
}
