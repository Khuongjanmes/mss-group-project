package com.openevent.identity.auth.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.openevent.common.web.ApiException;

/** Kiểm tra quy tắc xác minh ID token Google bằng token ký thật với khóa RSA sinh trong test. */
class NimbusGoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "my-client.apps.googleusercontent.com";
    private static KeyPair googleKey;
    private static KeyPair attackerKey;

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        googleKey = generator.generateKeyPair();
        attackerKey = generator.generateKeyPair();
    }

    private static String sign(KeyPair key, JWTClaimsSet claims) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(key.getPrivate()));
        return jwt.serialize();
    }

    private static JWTClaimsSet.Builder validClaims() {
        return new JWTClaimsSet.Builder()
                .issuer("https://accounts.google.com")
                .audience(List.of(CLIENT_ID))
                .subject("google-sub-1")
                .issueTime(java.util.Date.from(Instant.now()))
                .expirationTime(java.util.Date.from(Instant.now().plusSeconds(3600)))
                .claim("email", "Person@Gmail.com")
                .claim("email_verified", true)
                .claim("name", "Person")
                .claim("picture", "https://lh3.example.com/p.png")
                .jwtID(UUID.randomUUID().toString());
    }

    private static NimbusGoogleIdTokenVerifier verifier() {
        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) googleKey.getPublic()).build();
        return new NimbusGoogleIdTokenVerifier(CLIENT_ID, decoder);
    }

    private static void assertRejected(String token) {
        assertThatThrownBy(() -> verifier().verify(token))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(ex.getCode()).isEqualTo("INVALID_GOOGLE_TOKEN");
                });
    }

    @Test
    void acceptsValidTokenAndNormalizesEmail() throws Exception {
        GoogleIdentity identity = verifier().verify(sign(googleKey, validClaims().build()));

        assertThat(identity.subject()).isEqualTo("google-sub-1");
        assertThat(identity.email()).isEqualTo("person@gmail.com");
        assertThat(identity.name()).isEqualTo("Person");
        assertThat(identity.picture()).isEqualTo("https://lh3.example.com/p.png");
        assertThat(identity.emailVerified()).isTrue();
    }

    @Test
    void acceptsIssuerWithoutScheme() throws Exception {
        assertThat(verifier().verify(sign(googleKey, validClaims().issuer("accounts.google.com").build()))).isNotNull();
    }

    @Test
    void rejectsTokenSignedByAnotherKey() throws Exception {
        assertRejected(sign(attackerKey, validClaims().build()));
    }

    @Test
    void rejectsTokenIssuedForAnotherApplication() throws Exception {
        assertRejected(sign(googleKey, validClaims().audience("another-client").build()));
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        assertRejected(sign(googleKey, validClaims().issuer("https://evil.example.com").build()));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        assertRejected(sign(googleKey, validClaims()
                .expirationTime(java.util.Date.from(Instant.now().minusSeconds(3600))).build()));
    }

    @Test
    void rejectsTokenWithoutEmail() throws Exception {
        assertRejected(sign(googleKey, validClaims().claim("email", null).build()));
    }

    @Test
    void rejectsGarbage() {
        assertRejected("garbage");
    }

    @Test
    void unverifiedEmailIsReportedToCaller() throws Exception {
        GoogleIdentity identity = verifier().verify(sign(googleKey, validClaims().claim("email_verified", false).build()));

        assertThat(identity.emailVerified()).isFalse();
    }

    @Test
    void googleKeyServerFailureIsServiceUnavailable() {
        JwtDecoder failing = token -> {
            throw new JwtException("Couldn't retrieve remote JWK set");
        };

        assertThatThrownBy(() -> new NimbusGoogleIdTokenVerifier(CLIENT_ID, failing).verify("any"))
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getCode()).isEqualTo("GOOGLE_UNAVAILABLE");
                });
    }
}
