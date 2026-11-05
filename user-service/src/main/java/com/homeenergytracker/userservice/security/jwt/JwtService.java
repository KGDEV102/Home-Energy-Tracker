package com.homeenergytracker.userservice.security.jwt;

import com.homeenergytracker.common.security.RsaPublicKeyLoader;
import com.homeenergytracker.userservice.security.UserDetails.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {
    private static final byte[] KEY_PAIR_CHALLENGE =
            "home-energy-tracker-jwt-key-pair-check"
                    .getBytes(StandardCharsets.UTF_8);

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final long expiration;

    public JwtService(
            @Value("${jwt.private-key}") String privateKeyBase64,
            @Value("${jwt.public-key}") String publicKeyBase64,
            @Value("${jwt.expiration}") long expiration
    ) {
        if (expiration <= 0) {
            throw new IllegalArgumentException(
                    "JWT expiration must be greater than zero"
            );
        }
        if (privateKeyBase64 == null || privateKeyBase64.isBlank()) {
            throw new IllegalArgumentException(
                    "RSA private key must not be blank"
            );
        }

        try {
            byte[] decoded = Base64.getDecoder()
                    .decode(privateKeyBase64.replaceAll("\\s", ""));

            this.privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decoded));
            this.publicKey = RsaPublicKeyLoader.fromBase64(publicKeyBase64);

            validatePrivateKeyStrength(this.privateKey);
            validateKeyPair(this.privateKey, this.publicKey);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Could not load and validate RSA key pair",
                    exception
            );
        }

        this.expiration = expiration;
    }

    private void validatePrivateKeyStrength(RSAPrivateKey rsaPrivateKey) {
        if (rsaPrivateKey.getModulus().bitLength() < 2048) {
            throw new IllegalArgumentException(
                    "RSA private key must be at least 2048 bits"
            );
        }
    }

    private void validateKeyPair(
            RSAPrivateKey rsaPrivateKey,
            RSAPublicKey rsaPublicKey
    ) throws GeneralSecurityException {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(rsaPrivateKey);
        signer.update(KEY_PAIR_CHALLENGE);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(rsaPublicKey);
        verifier.update(KEY_PAIR_CHALLENGE);

        if (!verifier.verify(signature)) {
            throw new IllegalArgumentException(
                    "JWT private and public keys do not form a matching RSA key pair"
            );
        }
    }

    public String generateToken(CustomUserDetails customUserDetails) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(customUserDetails.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(
                        Date.from(now.plusMillis(expiration))
                )
                .signWith(privateKey,Jwts.SIG.RS256)
                .compact();
    }
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /*
     * Method dùng chung để lấy một claim cụ thể.
     */
    public <T> T extractClaim(
            String token,
            Function<Claims, T> claimsResolver
    ) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public Long extractUserId(String token) {
        Long subject = Long.parseLong(extractClaim(token, Claims::getSubject));
        return subject;

    }
    public boolean validateToken(
            String token,
            CustomUserDetails userDetails
    ) {
        try {
            Claims claims = extractAllClaims(token);

            Long subject = Long.parseLong(claims.getSubject());
            Date expirationDate = claims.getExpiration();

            if (subject == null || expirationDate == null) {
                return false;
            }


            return subject.equals(userDetails.getId())
                    && expirationDate.after(new Date());

        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

}
