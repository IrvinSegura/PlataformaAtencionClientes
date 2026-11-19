package com.irvinsegura.supportchat.actions.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.irvinsegura.supportchat.actions.domain.model.Integration.Settings;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");

    @Test
    void aShopifyStoreIsAlwaysItsMyshopifyDomain() {
        assertThat(shopify("MiTienda").account()).isEqualTo("mitienda.myshopify.com");
        assertThat(shopify("https://mitienda.myshopify.com/admin/orders").account())
                .isEqualTo("mitienda.myshopify.com");
        assertThat(shopify("mitienda.myshopify.com").username()).isNull();
        // Anything else could point the platform to another host
        assertThatThrownBy(() -> shopify("mitienda.example.com")).isInstanceOf(InvalidIntegrationException.class);
        assertThatThrownBy(() -> shopify("evil.com/x.myshopify.com")).isInstanceOf(InvalidIntegrationException.class);
        assertThatThrownBy(() -> shopify("127.0.0.1")).isInstanceOf(InvalidIntegrationException.class);
    }

    @Test
    void zendeskNeedsItsSubdomainAndTheAgentsEmail() {
        Integration zendesk = Integration.connect(UUID.randomUUID(), IntegrationProvider.ZENDESK,
                new Settings("https://MiTienda.zendesk.com/", " Agente@MiTienda.com ", "token", true), NOW);

        assertThat(zendesk.account()).isEqualTo("mitienda");
        assertThat(zendesk.username()).isEqualTo("agente@mitienda.com");
        assertThatThrownBy(() -> Integration.connect(UUID.randomUUID(), IntegrationProvider.ZENDESK,
                new Settings("mitienda", "", "token", true), NOW)).isInstanceOf(InvalidIntegrationException.class);
        assertThatThrownBy(() -> Integration.connect(UUID.randomUUID(), IntegrationProvider.ZENDESK,
                new Settings("mi.tienda", "agente@mitienda.com", "token", true), NOW))
                .isInstanceOf(InvalidIntegrationException.class);
    }

    @Test
    void connectingNeedsATokenAndABlankOneKeepsTheSaved() {
        assertThatThrownBy(() -> Integration.connect(UUID.randomUUID(), IntegrationProvider.SHOPIFY,
                new Settings("mitienda", null, " ", true), NOW)).isInstanceOf(InvalidIntegrationException.class);
        Integration integration = shopify("mitienda");

        integration.change(new Settings("otra", null, "", false), NOW.plusSeconds(1));

        assertThat(integration.token()).isEqualTo("shpat_123");
        assertThat(integration.account()).isEqualTo("otra.myshopify.com");
        assertThat(integration.enabled()).isFalse();
        assertThatThrownBy(() -> integration.change(new Settings("otra", null, "with space", true), NOW))
                .isInstanceOf(InvalidIntegrationException.class);
        assertThat(new Settings("otra", null, "shpat_secret", true).toString()).doesNotContain("shpat_secret");
    }

    @Test
    void integrationActionsHaveReservedNamesAndKnowTheVerifiedEmail() {
        assertThat(IntegrationAction.ofName("shopify_cancel_order")).contains(IntegrationAction.SHOPIFY_CANCEL_ORDER);
        assertThat(IntegrationAction.of(IntegrationProvider.ZENDESK))
                .containsExactly(IntegrationAction.ZENDESK_CREATE_TICKET);
        assertThat(IntegrationAction.isReserved("zendesk_anything")).isTrue();
        assertThat(IntegrationAction.isReserved("order_status")).isFalse();
        assertThat(IntegrationAction.SHOPIFY_ORDER_STATUS.requiresConfirmation()).isFalse();
        assertThat(IntegrationAction.SHOPIFY_CANCEL_ORDER.requiresConfirmation()).isTrue();

        UUID run = UUID.randomUUID();
        Map<String, Object> given = Map.of("email", "eve@example.com");
        assertThat(new IntegrationCall(run, run, "EMAIL", "ana@example.com", given).customerEmail())
                .isEqualTo("ana@example.com");
        assertThat(new IntegrationCall(run, run, "WEB", null, given).customerEmail()).isEqualTo("eve@example.com");
        assertThat(new IntegrationCall(run, run, "WHATSAPP", "5215551111", Map.of()).customerEmail()).isNull();
    }

    private static Integration shopify(String store) {
        return Integration.connect(UUID.randomUUID(), IntegrationProvider.SHOPIFY,
                new Settings(store, "ignored@x.com", "shpat_123", true), NOW);
    }
}
