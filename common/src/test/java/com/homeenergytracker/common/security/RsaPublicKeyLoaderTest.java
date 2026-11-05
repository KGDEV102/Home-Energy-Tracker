package com.homeenergytracker.common.security;

import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RsaPublicKeyLoaderTest {

    @Test
    void loadsBase64X509RsaPublicKey() throws Exception {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        var expected = (RSAPublicKey) keyPairGenerator.generateKeyPair().getPublic();
        var encoded = Base64.getEncoder().encodeToString(expected.getEncoded());

        var actual = RsaPublicKeyLoader.fromBase64(encoded);

        assertThat(actual.getModulus()).isEqualTo(expected.getModulus());
        assertThat(actual.getPublicExponent()).isEqualTo(expected.getPublicExponent());
    }

    @Test
    void rejectsMalformedPublicKey() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> RsaPublicKeyLoader.fromBase64("not-a-public-key"))
                .withMessage("Could not load Base64 X.509 RSA public key");
    }

    @Test
    void rejectsRsaKeysSmallerThan2048Bits() throws Exception {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        var encoded = Base64.getEncoder()
                .encodeToString(keyPairGenerator.generateKeyPair().getPublic().getEncoded());

        assertThatIllegalArgumentException()
                .isThrownBy(() -> RsaPublicKeyLoader.fromBase64(encoded))
                .withMessage("RSA public key must be at least 2048 bits");
    }
}
