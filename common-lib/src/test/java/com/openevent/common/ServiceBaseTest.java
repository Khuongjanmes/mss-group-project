package com.openevent.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.openevent.common.security.AuthenticatedUser;
import com.openevent.common.web.ApiException;

/** Hành vi mà mọi service nhận được khi thêm common-lib. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "openevent.security.public-paths=GET /api/public/**",
        "management.health.rabbit.enabled=false"})
class ServiceBaseTest {

    @SpringBootApplication
    @RestController
    static class TestApp {

        @GetMapping("/api/public/hello")
        Map<String, String> publicHello() {
            return Map.of("message", "hello");
        }

        @GetMapping("/api/public/guarded")
        @PreAuthorize("hasRole('ADMIN')")
        Map<String, String> publicGuarded() {
            return Map.of("message", "guarded");
        }

        @GetMapping("/api/private/me")
        Map<String, Object> me(@AuthenticationPrincipal AuthenticatedUser user) {
            return Map.of("id", user.id().toString(), "email", user.email(), "roles", user.roles());
        }

        @GetMapping("/api/private/admin")
        @PreAuthorize("hasRole('ADMIN')")
        Map<String, String> admin() {
            return Map.of("message", "admin");
        }

        @GetMapping("/api/private/business-error")
        void businessError() {
            throw new ApiException(HttpStatus.CONFLICT, "SOLD_OUT", "Hết vé");
        }

        @GetMapping("/api/private/crash")
        void crash() {
            throw new IllegalStateException("database password is hunter2");
        }

        @GetMapping("/internal/ping")
        Map<String, String> internal() {
            return Map.of("message", "internal");
        }
    }

    private final HttpClient http = HttpClient.newHttpClient();
    private final String userId = UUID.randomUUID().toString();

    @LocalServerPort
    int port;

    private HttpResponse<String> get(String path, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (headers.length > 0) {
            request.headers(headers);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getAs(String path, String roles) throws Exception {
        return get(path, "X-User-Id", userId, "X-User-Email", "a@example.com", "X-User-Roles", roles);
    }

    @Test
    void publicPathNeedsNoIdentity() throws Exception {
        assertThat(get("/api/public/hello").statusCode()).isEqualTo(200);
    }

    @Test
    void otherPathsRequireIdentityByDefault() throws Exception {
        HttpResponse<String> response = get("/api/private/me");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        assertThat(response.body()).contains("\"code\":\"UNAUTHORIZED\"");
    }

    @Test
    void unknownPathDoesNotRevealExistenceToAnonymous() throws Exception {
        assertThat(get("/api/private/nothing-here").statusCode()).isEqualTo(401);
    }

    @Test
    void identityHeadersBecomePrincipal() throws Exception {
        HttpResponse<String> response = getAs("/api/private/me", "ATTENDEE,ORGANIZER");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(userId, "a@example.com", "ATTENDEE", "ORGANIZER");
    }

    @Test
    void malformedUserIdIsAnonymous() throws Exception {
        assertThat(get("/api/private/me", "X-User-Id", "x", "X-User-Roles", "ADMIN").statusCode()).isEqualTo(401);
    }

    @Test
    void preAuthorizeReturnsJson403ForWrongRole() throws Exception {
        HttpResponse<String> response = getAs("/api/private/admin", "ATTENDEE");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.body()).contains("\"code\":\"FORBIDDEN\"");
    }

    @Test
    void preAuthorizeAllowsCorrectRole() throws Exception {
        assertThat(getAs("/api/private/admin", "ADMIN").statusCode()).isEqualTo(200);
    }

    @Test
    void anonymousOnGuardedPublicPathGets401Not403() throws Exception {
        assertThat(get("/api/public/guarded").statusCode()).isEqualTo(401);
    }

    @Test
    void apiExceptionKeepsStatusAndCode() throws Exception {
        HttpResponse<String> response = getAs("/api/private/business-error", "ATTENDEE");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("\"code\":\"SOLD_OUT\"", "/api/private/business-error");
    }

    @Test
    void unexpectedExceptionDoesNotLeakDetails() throws Exception {
        HttpResponse<String> response = getAs("/api/private/crash", "ATTENDEE");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("\"code\":\"INTERNAL_ERROR\"").doesNotContain("hunter2", "IllegalState");
    }

    @Test
    void unknownPathForAuthenticatedUserIsJson404() throws Exception {
        HttpResponse<String> response = getAs("/api/private/nothing-here", "ATTENDEE");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"status\":404");
    }

    @Test
    void internalPathNeedsNoIdentity() throws Exception {
        assertThat(get("/internal/ping").statusCode()).isEqualTo(200);
    }

    @Test
    void healthIsPublicButSensitiveActuatorIsNot() throws Exception {
        assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
        assertThat(get("/actuator/env").statusCode()).isEqualTo(401);
    }

    @Test
    void noSessionCookieIsCreated() throws Exception {
        assertThat(getAs("/api/private/me", "ATTENDEE").headers().firstValue("Set-Cookie")).isEmpty();
    }
}
