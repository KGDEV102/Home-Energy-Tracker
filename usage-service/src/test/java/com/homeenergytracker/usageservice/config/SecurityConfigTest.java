package com.homeenergytracker.usageservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void jwtDecoderVerifiesRs256TokenUsingSharedPublicKey() throws Exception {
        KeyPair keyPair = generateKeyPair();
        String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        JwtDecoder decoder = new SecurityConfig().jwtDecoder(publicKeyBase64);

        var jwt = decoder.decode(createToken(keyPair.getPrivate(), "42"));

        assertThat(jwt.getSubject()).isEqualTo("42");
    }

    private KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private String createToken(PrivateKey privateKey, String subject) throws Exception {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        long now = Instant.now().getEpochSecond();
        String header = encoder.encodeToString("{\"alg\":\"RS256\"}"
                .getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(("{\"sub\":\"" + subject
                + "\",\"iat\":" + now + ",\"exp\":" + (now + 300) + "}")
                .getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));

        return signingInput + "." + encoder.encodeToString(signature.sign());
    }
}
