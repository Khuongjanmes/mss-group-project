package com.openevent.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.nimbusds.jose.JWSAlgorithm;
import com.openevent.gateway.support.StubDownstream;
import com.openevent.gateway.support.TestTokens;

/** Chạy Gateway thật trên HTTP thật. Eureka được thay bằng danh sách instance tĩnh trỏ tới service giả. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "eureka.client.enabled=false",
        "openevent.jwt.secret=" + TestTokens.SECRET,
        "openevent.cors.allowed-origins=https://app.example.com,http://localhost:5173",
        "spring.cloud.gateway.server.webflux.httpclient.response-timeout=1s"})
class GatewayTest {

    private static final StubDownstream STUB = new StubDownstream();
    private static final String ANY_ID = UUID.randomUUID().toString();

    @DynamicPropertySource
    static void discovery(DynamicPropertyRegistry registry) throws Exception {
        for (String service : new String[] {"identity-service", "event-service", "ticket-service", "order-service"}) {
            registry.add("spring.cloud.discovery.client.simple.instances." + service + "[0].uri",
                    () -> "http://localhost:" + STUB.port());
        }
        // payment-service có instance nhưng không ai nghe cổng đó; notification-service không có instance nào.
        int deadPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
        }
        int port = deadPort;
        registry.add("spring.cloud.discovery.client.simple.instances.payment-service[0].uri",
                () -> "http://localhost:" + port);
    }

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @BeforeEach
    void clearStub() {
        STUB.clear();
    }

    private HttpResponse<String> send(String method, String rawPath, String body, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + rawPath))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (headers.length > 0) {
            request.headers(headers);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String... headers) throws Exception {
        return send("GET", path, null, headers);
    }

    private static String[] bearer(String token) {
        return new String[] {"Authorization", "Bearer " + token};
    }

    // ---------- Kiểm tra token ----------

    @Test
    void privatePathWithoutTokenIs401Json() throws Exception {
        HttpResponse<String> response = get("/api/orders/my");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
        assertThat(response.headers().firstValue("WWW-Authenticate").orElse("")).startsWith("Bearer");
        assertThat(response.body()).contains("\"code\":\"UNAUTHORIZED\"");
        assertThat(STUB.requests()).isEmpty();
    }

    @Test
    void validTokenIsForwardedAsIdentityHeaders() throws Exception {
        String userId = UUID.randomUUID().toString();
        String token = TestTokens.hs256(TestTokens.claims(userId, "ATTENDEE", "ORGANIZER").build(), TestTokens.SECRET);

        HttpResponse<String> response = get("/api/orders/my", bearer(token));

        assertThat(response.statusCode()).isEqualTo(200);
        StubDownstream.Received received = STUB.last();
        assertThat(received.header("X-User-Id")).isEqualTo(userId);
        assertThat(received.header("X-User-Email")).isEqualTo(TestTokens.EMAIL);
        assertThat(received.header("X-User-Roles").split(",")).containsExactlyInAnyOrder("ATTENDEE", "ORGANIZER");
    }

    @Test
    void authorizationHeaderIsNotForwardedDownstream() throws Exception {
        get("/api/orders/my", bearer(TestTokens.token("ATTENDEE")));

        assertThat(STUB.last().hasHeader("Authorization")).isFalse();
    }

    static Stream<Arguments> invalidTokens() {
        String id = UUID.randomUUID().toString();
        String valid = TestTokens.hs256(TestTokens.claims(id, "ADMIN").build(), TestTokens.SECRET);
        String[] parts = valid.split("\\.");
        String tamperedPayload = parts[0] + "."
                + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                        TestTokens.claims(id, "ADMIN").subject(UUID.randomUUID().toString()).build().toString().getBytes())
                + "." + parts[2];
        return Stream.of(
                Arguments.of("chữ ký bằng secret khác", TestTokens.hs256(TestTokens.claims(id, "ADMIN").build(),
                        "another-secret-another-secret-another-secret-0123456789")),
                Arguments.of("alg=none", TestTokens.unsigned(TestTokens.claims(id, "ADMIN").build())),
                Arguments.of("RS256 với khóa lạ", TestTokens.rs256(TestTokens.claims(id, "ADMIN").build())),
                Arguments.of("HS512 thay vì HS256", TestTokens.mac(JWSAlgorithm.HS512,
                        TestTokens.claims(id, "ADMIN").build(), TestTokens.SECRET)),
                Arguments.of("sửa payload", tamperedPayload),
                Arguments.of("hết hạn", TestTokens.hs256(TestTokens.claims(id, "ADMIN")
                        .expirationTime(Date.from(Instant.now().minusSeconds(3600))).build(), TestTokens.SECRET)),
                Arguments.of("không có exp", TestTokens.hs256(TestTokens.claims(id, "ADMIN")
                        .expirationTime(null).build(), TestTokens.SECRET)),
                Arguments.of("issuer sai", TestTokens.hs256(TestTokens.claims(id, "ADMIN")
                        .issuer("evil").build(), TestTokens.SECRET)),
                Arguments.of("không có issuer", TestTokens.hs256(TestTokens.claims(id, "ADMIN")
                        .issuer(null).build(), TestTokens.SECRET)),
                Arguments.of("subject không phải UUID", TestTokens.hs256(TestTokens.claims("admin", "ADMIN").build(),
                        TestTokens.SECRET)),
                Arguments.of("không có subject", TestTokens.hs256(TestTokens.claims(id, "ADMIN")
                        .subject(null).build(), TestTokens.SECRET)),
                Arguments.of("chuỗi rác", "garbage"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    void invalidTokenIsRejected(String description, String token) throws Exception {
        HttpResponse<String> response = get("/api/orders/my", bearer(token));

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(STUB.requests()).isEmpty();
    }

    @Test
    void tokenInQueryStringIsNotAccepted() throws Exception {
        assertThat(get("/api/orders/my?access_token=" + TestTokens.token("ATTENDEE")).statusCode()).isIn(400, 401);
        assertThat(STUB.requests()).isEmpty();
    }

    // ---------- Chống giả mạo danh tính ----------

    @Test
    void spoofedIdentityHeadersAreReplacedByTokenValues() throws Exception {
        String userId = UUID.randomUUID().toString();
        String token = TestTokens.hs256(TestTokens.claims(userId, "ATTENDEE").build(), TestTokens.SECRET);

        get("/api/orders/my", "Authorization", "Bearer " + token,
                "X-User-Id", ANY_ID, "X-User-Roles", "ADMIN", "X-User-Email", "evil@example.com");

        StubDownstream.Received received = STUB.last();
        assertThat(received.headerValues("X-User-Id")).containsExactly(userId);
        assertThat(received.headerValues("X-User-Roles")).containsExactly("ATTENDEE");
        assertThat(received.headerValues("X-User-Email")).containsExactly(TestTokens.EMAIL);
    }

    @Test
    void spoofedIdentityHeadersAreRemovedOnPublicPaths() throws Exception {
        HttpResponse<String> response = get("/api/events", "x-user-id", ANY_ID, "X-USER-ROLES", "ADMIN",
                "X-User-Email", "evil@example.com", "X-User-Anything", "x");

        assertThat(response.statusCode()).isEqualTo(200);
        StubDownstream.Received received = STUB.last();
        assertThat(received.hasHeader("X-User-Id")).isFalse();
        assertThat(received.hasHeader("X-User-Roles")).isFalse();
        assertThat(received.hasHeader("X-User-Email")).isFalse();
        assertThat(received.hasHeader("X-User-Anything")).isFalse();
    }

    @Test
    void spoofedHeadersWithoutTokenDoNotAuthenticate() throws Exception {
        assertThat(get("/api/orders/my", "X-User-Id", ANY_ID, "X-User-Roles", "ADMIN").statusCode()).isEqualTo(401);
    }

    // ---------- Đường dẫn công khai ----------

    @ParameterizedTest
    @CsvSource({
            "POST, /api/auth/register", "POST, /api/auth/login", "POST, /api/auth/google",
            "POST, /api/auth/refresh", "POST, /api/auth/logout",
            "GET, /api/events", "GET, /api/events/123", "GET, /api/categories", "GET, /api/venues",
            "GET, /api/ticket-types", "GET, /api/ticket-types/123"})
    void publicPathNeedsNoToken(String method, String path) throws Exception {
        assertThat(send(method, path, method.equals("POST") ? "{}" : null).statusCode()).isEqualTo(200);
        assertThat(STUB.requests()).hasSize(1);
    }

    @Test
    void vnpayReturnPassesAuthenticationWithoutToken() throws Exception {
        // payment-service trong test trỏ vào cổng chết: 503 nghĩa là request đã qua lớp xác thực.
        assertThat(get("/api/payments/vnpay-return").statusCode()).isEqualTo(503);
    }

    @Test
    void expiredTokenDoesNotBlockAuthEndpointsSoSpaCanRefresh() throws Exception {
        String expired = TestTokens.hs256(TestTokens.claims(ANY_ID, "ATTENDEE")
                .expirationTime(Date.from(Instant.now().minusSeconds(3600))).build(), TestTokens.SECRET);

        HttpResponse<String> response = send("POST", "/api/auth/refresh", "{}", bearer(expired));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(STUB.last().hasHeader("X-User-Id")).isFalse();
    }

    @Test
    void validTokenOnPublicEventPathStillForwardsIdentity() throws Exception {
        String userId = UUID.randomUUID().toString();
        String token = TestTokens.hs256(TestTokens.claims(userId, "ATTENDEE").build(), TestTokens.SECRET);

        get("/api/events", bearer(token));

        assertThat(STUB.last().header("X-User-Id")).isEqualTo(userId);
    }

    @Test
    void authSubPathsAreNotPublicByPrefix() throws Exception {
        assertThat(get("/api/auth/login").statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/auth/admin-only", "{}").statusCode()).isEqualTo(401);
    }

    // ---------- Phân quyền theo vai trò ----------

    @ParameterizedTest(name = "{0} {1} với {2} -> {3}")
    @CsvSource({
            // Hồ sơ cá nhân: chỉ cần đăng nhập
            "GET,  /api/users/me,                     ATTENDEE,  200",
            "PUT,  /api/users/me,                     ATTENDEE,  200",
            "PUT,  /api/users/me/password,            ATTENDEE,  200",
            // Quản trị người dùng
            "GET,  /api/users,                        ATTENDEE,  403",
            "GET,  /api/users,                        ORGANIZER, 403",
            "GET,  /api/users,                        ADMIN,     200",
            "GET,  /api/users/" + "11111111-1111-1111-1111-111111111111, ATTENDEE, 403",
            "PUT,  /api/users/x/lock,                 ORGANIZER, 403",
            "PUT,  /api/users/x/lock,                 ADMIN,     200",
            "PUT,  /api/users/x/roles/organizer,      ADMIN,     200",
            // Sự kiện
            "POST, /api/events,                       ATTENDEE,  403",
            "POST, /api/events,                       ORGANIZER, 200",
            "PUT,  /api/events/1,                     ATTENDEE,  403",
            "PUT,  /api/events/1,                     ORGANIZER, 200",
            "PUT,  /api/events/1/approve,             ORGANIZER, 403",
            "PUT,  /api/events/1/approve,             ADMIN,     200",
            "PUT,  /api/events/1/reject,              ORGANIZER, 403",
            "PUT,  /api/events/1/reject,              ADMIN,     200",
            "DELETE, /api/events/1,                   ATTENDEE,  403",
            // Danh mục, địa điểm
            "POST, /api/categories,                   ORGANIZER, 403",
            "POST, /api/categories,                   ADMIN,     200",
            "DELETE, /api/categories/1,               ORGANIZER, 403",
            "POST, /api/venues,                       ATTENDEE,  403",
            "POST, /api/venues,                       ORGANIZER, 200",
            // Vé
            "POST, /api/ticket-types,                 ATTENDEE,  403",
            "POST, /api/ticket-types,                 ORGANIZER, 200",
            "PUT,  /api/ticket-types/1,               ADMIN,     403",
            "POST, /api/tickets/check-in,             ATTENDEE,  403",
            "POST, /api/tickets/check-in,             ORGANIZER, 200",
            "GET,  /api/tickets/my,                   ATTENDEE,  200",
            // Đơn hàng, thanh toán, thông báo
            "POST, /api/orders,                       ATTENDEE,  200",
            "POST, /api/payments,                     ATTENDEE,  503",
            "GET,  /api/payments,                     ATTENDEE,  403",
            "PUT,  /api/payments/1/refunded,          ORGANIZER, 403"})
    void roleRules(String method, String path, String role, int expected) throws Exception {
        HttpResponse<String> response = send(method.trim(), path.trim(),
                method.trim().equals("GET") || method.trim().equals("DELETE") ? null : "{}",
                bearer(TestTokens.token(role.trim())));

        assertThat(response.statusCode()).isEqualTo(expected);
        if (expected == 403) {
            assertThat(response.body()).contains("\"code\":\"FORBIDDEN\"");
            assertThat(STUB.requests()).isEmpty();
        }
    }

    @Test
    void tokenWithoutRolesCanStillNotReachAdminApis() throws Exception {
        assertThat(get("/api/users", bearer(TestTokens.token())).statusCode()).isEqualTo(403);
    }

    @Test
    void roleClaimIsCaseSensitive() throws Exception {
        assertThat(get("/api/users", bearer(TestTokens.token("admin"))).statusCode()).isEqualTo(403);
    }

    // ---------- API nội bộ và actuator ----------

    @Test
    void internalApiIsForbiddenEvenForAdmin() throws Exception {
        assertThat(get("/internal/users/1", bearer(TestTokens.token("ADMIN"))).statusCode()).isEqualTo(403);
        assertThat(get("/internal/users/1").statusCode()).isEqualTo(401);
        assertThat(STUB.requests()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/events/..%2f..%2finternal/users/1",
            "/api/events/%2e%2e/%2e%2e/internal/users/1",
            "/api/events/..;/..;/internal/users/1",
            "/api/events/%252e%252e/internal/users/1",
            "/internal%2fusers/1",
            "/%69nternal/users/1",
            "//internal/users/1",
            "/./internal/users/1",
            "/INTERNAL/users/1"})
    void pathTricksDoNotReachInternalApi(String path) throws Exception {
        get(path, bearer(TestTokens.token("ADMIN")));

        assertThat(STUB.requests()).noneMatch(request -> {
            String decoded = java.net.URLDecoder.decode(
                    java.net.URLDecoder.decode(request.rawPath(), java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.charset.StandardCharsets.UTF_8);
            return java.net.URI.create("http://x" + decoded.replace(";", "")).normalize().getPath()
                    .toLowerCase().startsWith("/internal");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/users/..%2fevents",
            "/api/events/..%2fusers",
            "/api/events/%2e%2e/users"})
    void pathTricksDoNotBypassRoleRules(String path) throws Exception {
        get(path);

        assertThat(STUB.requests()).noneMatch(request -> request.rawPath().toLowerCase().contains("users"));
    }

    @Test
    void healthIsPublicAndHidesDetails() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("components");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/env", "/actuator/gateway/routes", "/actuator/beans", "/actuator"})
    void otherActuatorEndpointsAreForbiddenEvenForAdmin(String path) throws Exception {
        assertThat(get(path, bearer(TestTokens.token("ADMIN"))).statusCode()).isEqualTo(403);
    }

    // ---------- Định tuyến ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/users/me", "/api/orders/my", "/api/tickets/my"})
    void pathIsForwardedUnchanged(String path) throws Exception {
        get(path, bearer(TestTokens.token("ATTENDEE")));

        assertThat(STUB.last().rawPath()).isEqualTo(path);
    }

    @Test
    void queryStringIsPreservedIncludingVietnamese() throws Exception {
        get("/api/events?keyword=%C4%90%C3%A0%20N%E1%BA%B5ng&page=2");

        assertThat(STUB.last().rawQuery()).isEqualTo("keyword=%C4%90%C3%A0%20N%E1%BA%B5ng&page=2");
    }

    @Test
    void requestBodyIsPreservedIncludingVietnamese() throws Exception {
        String body = "{\"fullName\":\"Nguyễn Văn Á\"}";

        send("POST", "/api/auth/register", body, "Content-Type", "application/json");

        assertThat(STUB.last().body()).isEqualTo(body);
        assertThat(STUB.last().header("Content-Type")).isEqualTo("application/json");
    }

    @Test
    void unknownPathForAuthenticatedUserIsJson404() throws Exception {
        HttpResponse<String> response = get("/api/nothing-here", bearer(TestTokens.token("ATTENDEE")));

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("\"status\":404");
        assertThat(STUB.requests()).isEmpty();
    }

    // ---------- Lỗi của service phía sau ----------

    @Test
    void serviceWithoutInstanceIsJson503WithoutLeakingInternals() throws Exception {
        HttpResponse<String> response = get("/api/notifications/my", bearer(TestTokens.token("ATTENDEE")));

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"").doesNotContain("notification-service", "lb://");
    }

    @Test
    void connectionRefusedIsJson503WithoutLeakingInternals() throws Exception {
        HttpResponse<String> response = send("POST", "/api/payments", "{}", bearer(TestTokens.token("ATTENDEE")));

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("\"code\":\"SERVICE_UNAVAILABLE\"").doesNotContain("localhost", "Connection");
    }

    @Test
    void slowServiceIsJson504() throws Exception {
        HttpResponse<String> response = get("/api/orders/slow", bearer(TestTokens.token("ATTENDEE")));

        assertThat(response.statusCode()).isEqualTo(504);
        assertThat(response.body()).contains("\"code\":\"GATEWAY_TIMEOUT\"");
    }

    // ---------- CORS ----------

    @Test
    void preflightFromAllowedOriginSucceedsWithoutToken() throws Exception {
        HttpResponse<String> response = send("OPTIONS", "/api/orders", null,
                "Origin", "https://app.example.com",
                "Access-Control-Request-Method", "POST",
                "Access-Control-Request-Headers", "authorization,content-type");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains("https://app.example.com");
        assertThat(response.headers().firstValue("Access-Control-Allow-Methods").orElse("")).contains("POST");
        assertThat(response.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase()).contains("authorization");
        assertThat(STUB.requests()).isEmpty();
    }

    @Test
    void preflightFromUnknownOriginIsRejected() throws Exception {
        HttpResponse<String> response = send("OPTIONS", "/api/orders", null,
                "Origin", "https://evil.example.com", "Access-Control-Request-Method", "POST");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void actualRequestGetsSingleCorsHeader() throws Exception {
        HttpResponse<String> response = get("/api/events", "Origin", "http://localhost:5173");

        assertThat(response.headers().allValues("Access-Control-Allow-Origin")).containsExactly("http://localhost:5173");
    }

    @Test
    void errorResponsesAlsoCarryCorsHeadersSoSpaCanReadThem() throws Exception {
        HttpResponse<String> response = get("/api/orders/my", "Origin", "http://localhost:5173");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).contains("http://localhost:5173");
    }
}
