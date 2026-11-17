package com.irvinsegura.supportchat.identity.infrastructure.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

/**
 * Signs access tokens with RS256. Only the private key can issue tokens, so the public key could later be given to
 * other services to verify them without being able to forge any.
 */
@Configuration(proxyBeanMethods = false)
class JwtConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JwtConfiguration.class);

    @Bean
    KeyPair jwtSigningKeyPair(IdentityProperties properties, ResourceLoader resourceLoader) {
        IdentityProperties.Jwt jwt = properties.jwt();
        if (StringUtils.hasText(jwt.privateKeyLocation()) && StringUtils.hasText(jwt.publicKeyLocation())) {
            RSAPublicKey publicKey = readKey(resourceLoader, jwt.publicKeyLocation(), RsaKeyConverters.x509());
            RSAPrivateKey privateKey = readKey(resourceLoader, jwt.privateKeyLocation(), RsaKeyConverters.pkcs8());
            return new KeyPair(publicKey, privateKey);
        }
        if (!jwt.generateKeysIfMissing()) {
            throw new IllegalStateException("JWT signing keys are missing: set supportchat.identity.jwt."
                    + "private-key-location and supportchat.identity.jwt.public-key-location");
        }
        log.warn("No JWT signing keys configured, using a temporary key pair. Tokens become invalid on restart.");
        return generateKeyPair();
    }

    @Bean
    JwtEncoder jwtEncoder(KeyPair jwtSigningKeyPair) {
        try {
            RSAKey key = new RSAKey.Builder((RSAPublicKey) jwtSigningKeyPair.getPublic())
                    .privateKey(jwtSigningKeyPair.getPrivate())
                    // The key id lets verifiers pick the right key once keys are rotated
                    .keyIDFromThumbprint()
                    .build();
            return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot build the JWT signing key", e);
        }
    }

    @Bean
    JwtDecoder jwtDecoder(KeyPair jwtSigningKeyPair, IdentityProperties properties) {
        // Pinned to RS256: a token claiming another algorithm (e.g. "none" or HS256) is rejected
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) jwtSigningKeyPair.getPublic())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        // Checks expiry, not-before and that this application issued it
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.jwt().issuer()));
        return decoder;
    }

    private static <K> K readKey(ResourceLoader resourceLoader, String location, Converter<InputStream, K> converter) {
        try (InputStream input = resourceLoader.getResource(location).getInputStream()) {
            return converter.convert(input);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read JWT key from " + location, e);
        }
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA is required on every Java platform", e);
        }
    }
}
