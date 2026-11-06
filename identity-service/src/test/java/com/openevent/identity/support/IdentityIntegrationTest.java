package com.openevent.identity.support;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.openevent.identity.auth.google.GoogleIdTokenVerifier;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Chạy Identity thật trên HTTP thật với PostgreSQL thật; chỉ giả lập việc gọi ra Google. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "openevent.jwt.secret=" + IdentityIntegrationTest.JWT_SECRET,
        "openevent.bootstrap.admin-email=" + IdentityIntegrationTest.ADMIN_EMAIL,
        "openevent.bootstrap.admin-password=" + IdentityIntegrationTest.ADMIN_PASSWORD})
public abstract class IdentityIntegrationTest {

    public static final String JWT_SECRET = "integration-test-secret-integration-test-secret";
    public static final String ADMIN_EMAIL = "root-admin@openevent.test";
    public static final String ADMIN_PASSWORD = "Sup3r-Secret!";
    public static final String PASSWORD = "Passw0rd!";

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    @MockitoBean
    protected GoogleIdTokenVerifier googleVerifier;

    @LocalServerPort
    protected int port;

    public record Response(int status, JsonNode body, String raw) {
        public String code() {
            return body.path("code").asString();
        }
    }

    /** Thông tin danh tính như Gateway truyền xuống. */
    public record Caller(String id, String roles) {
    }

    protected Response send(String method, String path, Object body, Caller caller) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body instanceof String s ? s : json.writeValueAsString(body));
            request.method(method, publisher).header("Content-Type", "application/json");
            if (caller != null) {
                request.header("X-User-Id", caller.id()).header("X-User-Roles", caller.roles());
            }
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode node = response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
            return new Response(response.statusCode(), node, response.body());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected Response post(String path, Object body) {
        return send("POST", path, body, null);
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected Response register(String email) {
        return post("/api/auth/register", java.util.Map.of("email", email, "password", PASSWORD, "fullName", "Nguyễn Văn A"));
    }

    protected Response login(String email, String password) {
        return post("/api/auth/login", java.util.Map.of("email", email, "password", password));
    }

    protected Response refresh(String refreshToken) {
        return post("/api/auth/refresh", java.util.Map.of("refreshToken", refreshToken));
    }

    protected Caller attendee(Response auth) {
        return new Caller(auth.body().path("user").path("id").asString(), "ATTENDEE");
    }

    protected Caller admin() {
        return new Caller(login(ADMIN_EMAIL, ADMIN_PASSWORD).body().path("user").path("id").asString(), "ADMIN");
    }
}
