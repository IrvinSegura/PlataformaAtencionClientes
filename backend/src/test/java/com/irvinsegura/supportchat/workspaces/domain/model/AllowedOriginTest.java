package com.irvinsegura.supportchat.workspaces.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AllowedOriginTest {

    @ParameterizedTest
    @CsvSource({
            "https://shop.example.com, https://shop.example.com",
            "HTTPS://Shop.Example.com/, https://shop.example.com",
            "https://shop.example.com:443, https://shop.example.com",
            "https://shop.example.com:8443, https://shop.example.com:8443",
            "http://localhost:5173, http://localhost:5173",
            "http://127.0.0.1, http://127.0.0.1"})
    void normalizesValidOrigins(String input, String expected) {
        assertThat(AllowedOrigin.parse(input).value()).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "http://shop.example.com",
            "https://shop.example.com/path",
            "https://shop.example.com?x=1",
            "https://user@shop.example.com",
            "javascript:alert(1)",
            "*",
            "shop.example.com"})
    void rejectsAnythingThatIsNotASafeOrigin(String input) {
        assertThatThrownBy(() -> AllowedOrigin.parse(input)).isInstanceOf(InvalidWorkspaceSettingsException.class);
    }
}
