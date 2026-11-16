package com.irvinsegura.supportchat.identity.infrastructure.security;

import com.irvinsegura.supportchat.identity.domain.port.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt with a configurable cost (12 by default, roughly a quarter of a second per check), which makes offline
 * guessing of a leaked hash expensive.
 */
@Component
class BcryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder;

    BcryptPasswordHasher(BCryptPasswordEncoder encoder) {
        this.encoder = encoder;
    }

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hash) {
        return rawPassword != null && encoder.matches(rawPassword, hash);
    }
}
