package com.homeenergytracker.userservice.security.jwt;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class JwtServiceTest {

    @Test
    void acceptsMatchingRsaKeyPair() throws Exception {
        KeyPair keyPair = generateKeyPair(2048);

        assertThatCode(() -> jwtService(keyPair, keyPair, 900_000L))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMismatchedRsaKeyPair() throws Exception {
        KeyPair signingKeyPair = generateKeyPair(2048);
        KeyPair verificationKeyPair = generateKeyPair(2048);

        assertThatIllegalStateException()
                .isThrownBy(() -> jwtService(
                        signingKeyPair,
                        verificationKeyPair,
                        900_000L
                ))
                .withMessage("Could not load and validate RSA key pair")
                .withCauseInstanceOf(IllegalArgumentException.class)
                .satisfies(exception -> assertThat(exception.getCause())
                        .hasMessage(
                                "JWT private and public keys do not form a matching RSA key pair"
                        ));
    }

    @Test
    void rejectsRsaPrivateKeySmallerThan2048Bits() throws Exception {
        KeyPair weakPrivateKeyPair = generateKeyPair(1024);
        KeyPair strongPublicKeyPair = generateKeyPair(2048);

        assertThatIllegalStateException()
                .isThrownBy(() -> jwtService(
                        weakPrivateKeyPair,
                        strongPublicKeyPair,
                        900_000L
                ))
                .withMessage("Could not load and validate RSA key pair")
                .satisfies(exception -> assertThat(exception.getCause())
                        .hasMessage(
                                "RSA private key must be at least 2048 bits"
                        ));
    }

    @Test
    void rejectsNonPositiveExpiration() throws Exception {
        KeyPair keyPair = generateKeyPair(2048);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> jwtService(keyPair, keyPair, 0L))
                .withMessage("JWT expiration must be greater than zero");
    }

    @Test
    void rejectsBlankPrivateKey() throws Exception {
        KeyPair keyPair = generateKeyPair(2048);
        String publicKey = Base64.getEncoder().encodeToString(
                keyPair.getPublic().getEncoded()
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new JwtService(" ", publicKey, 900_000L))
                .withMessage("RSA private key must not be blank");
    }

    @Test
    void rejectsMalformedPrivateKey() throws Exception {
        KeyPair keyPair = generateKeyPair(2048);
        String publicKey = Base64.getEncoder().encodeToString(
                keyPair.getPublic().getEncoded()
        );

        assertThatIllegalStateException()
                .isThrownBy(() -> new JwtService(
                        "not-a-private-key",
                        publicKey,
                        900_000L
                ))
                .withMessage("Could not load and validate RSA key pair")
                .withCauseInstanceOf(IllegalArgumentException.class);
    }

    private JwtService jwtService(
            KeyPair privateKeyPair,
            KeyPair publicKeyPair,
            long expiration
    ) {
        return new JwtService(
                Base64.getEncoder().encodeToString(
                        privateKeyPair.getPrivate().getEncoded()
                ),
                Base64.getEncoder().encodeToString(
                        publicKeyPair.getPublic().getEncoded()
                ),
                expiration
        );
    }

    private KeyPair generateKeyPair(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }
}
