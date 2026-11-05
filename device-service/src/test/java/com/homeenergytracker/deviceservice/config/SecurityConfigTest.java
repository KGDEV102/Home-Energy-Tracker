package com.homeenergytracker.deviceservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void jwtDecoderVerifiesRs256TokenUsingSharedPublicKey() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        JwtDecoder decoder = new SecurityConfig().jwtDecoder(publicKey);

        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        long now = Instant.now().getEpochSecond();
        String header = encoder.encodeToString("{\"alg\":\"RS256\"}"
                .getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(("{\"sub\":\"10\",\"iat\":" + now
                + ",\"exp\":" + (now + 300) + "}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        String token = signingInput + "." + encoder.encodeToString(signature.sign());

        assertThat(decoder.decode(token).getSubject()).isEqualTo("10");
    }
}
