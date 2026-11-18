package com.irvinsegura.supportchat.shared.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private final SecretCipher cipher = new SecretCipher("0123456789abcdef0123456789abcdef".getBytes(
            StandardCharsets.UTF_8));

    @Test
    void encryptsWithAFreshNonceAndDecryptsBack() {
        String first = cipher.encrypt("whsec_secret");
        String second = cipher.encrypt("whsec_secret");

        assertThat(first).startsWith("v1:").doesNotContain("whsec_secret").isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("whsec_secret");
    }

    @Test
    void aTamperedValueIsRejected() {
        String stored = cipher.encrypt("token");
        String tampered = stored.substring(0, stored.length() - 2) + (stored.endsWith("A") ? "BB" : "AA");

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void anotherKeyCannotDecrypt() {
        SecretCipher other = new SecretCipher("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> other.decrypt(cipher.encrypt("token"))).isInstanceOf(RuntimeException.class);
    }
}
