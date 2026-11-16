package com.irvinsegura.supportchat.identity.domain.port;

public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
