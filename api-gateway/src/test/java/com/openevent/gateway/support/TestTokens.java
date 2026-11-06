package com.openevent.gateway.support;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

public final class TestTokens {

    public static final String SECRET = "gateway-test-secret-gateway-test-secret-gateway-test-secret-0123";
    public static final String ISSUER = "openevent-identity";
    public static final String EMAIL = "user@example.com";

    private TestTokens() {
    }

    public static JWTClaimsSet.Builder claims(String subject, String... roles) {
        return new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .subject(subject)
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(600)))
                .claim("email", EMAIL)
                .claim("roles", List.of(roles));
    }

    public static String token(String... roles) {
        return hs256(claims(UUID.randomUUID().toString(), roles).build(), SECRET);
    }

    public static String hs256(JWTClaimsSet claims, String secret) {
        return mac(JWSAlgorithm.HS256, claims, secret);
    }

    public static String mac(JWSAlgorithm algorithm, JWTClaimsSet claims, String secret) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
            jwt.sign(new MACSigner(secret.getBytes()));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String rs256(JWTClaimsSet claims) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(pair.getPrivate()));
            return jwt.serialize();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    /** Token không chữ ký (alg=none) với payload hợp lệ. */
    public static String unsigned(JWTClaimsSet claims) {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString("{\"alg\":\"none\"}".getBytes()) + "."
                + encoder.encodeToString(claims.toString().getBytes()) + ".";
    }
}
