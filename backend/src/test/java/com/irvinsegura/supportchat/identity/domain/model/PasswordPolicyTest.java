package com.irvinsegura.supportchat.identity.domain.model;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    private final Email email = Email.of("maria.lopez@example.com");

    @Test
    void acceptsALongVariedPassword() {
        assertThatCode(() -> PasswordPolicy.check("correct horse battery staple", email)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "aaaaaaaaaaaaaaaa", "ababababababab", "my maria.lopez password"})
    void rejectsWeakPasswords(String password) {
        assertThatThrownBy(() -> PasswordPolicy.check(password, email)).isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void rejectsPasswordsBcryptWouldTruncate() {
        // 25 three-byte characters: within the length rule in characters, over BCrypt's 72 bytes
        String password = "€£¥".repeat(25);

        assertThatThrownBy(() -> PasswordPolicy.check(password, email)).isInstanceOf(WeakPasswordException.class);
    }

    @Test
    void emailsAreNormalizedAndValidated() {
        org.assertj.core.api.Assertions.assertThat(Email.of("  Maria@Example.COM ").value())
                .isEqualTo("maria@example.com");
        assertThatThrownBy(() -> Email.of("not-an-email")).isInstanceOf(InvalidEmailException.class);
        assertThatThrownBy(() -> Email.of("a@b@c.com")).isInstanceOf(InvalidEmailException.class);
    }
}
