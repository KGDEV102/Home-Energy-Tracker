package com.homeenergytracker.common.security;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RsaPublicKeyLoader {

    private RsaPublicKeyLoader() {
    }

    public static RSAPublicKey fromBase64(String publicKeyBase64) {
        if (publicKeyBase64 == null || publicKeyBase64.isBlank()) {
            throw new IllegalArgumentException("RSA public key must not be blank");
        }

        RSAPublicKey publicKey;
        try {
            byte[] keyBytes = Base64.getDecoder().decode(publicKeyBase64.replaceAll("\\s", ""));
            publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException exception) {
            throw new IllegalArgumentException("Could not load Base64 X.509 RSA public key", exception);
        }

        if (publicKey.getModulus().bitLength() < 2048) {
            throw new IllegalArgumentException("RSA public key must be at least 2048 bits");
        }
        return publicKey;
    }
}
