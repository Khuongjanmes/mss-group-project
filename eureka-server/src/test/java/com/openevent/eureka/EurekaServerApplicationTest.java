package com.openevent.eureka;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Chạy Eureka thật trên cổng ngẫu nhiên và dùng đúng REST API mà client Eureka sử dụng. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EurekaServerApplicationTest {

    private final HttpClient http = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    private HttpResponse<String> get(String path, String accept) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", accept).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> register(String app, String instanceId) throws Exception {
        String body = """
                {"instance":{"instanceId":"%2$s","hostName":"host-1","app":"%1$s","ipAddr":"10.0.0.1",
                "status":"UP","port":{"$":9000,"@enabled":"true"},
                "dataCenterInfo":{"@class":"com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo","name":"MyOwn"}}}
                """.formatted(app, instanceId);
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/eureka/apps/" + app))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthEndpointIsUpWithoutExposingDetails() throws Exception {
        HttpResponse<String> response = get("/actuator/health", "application/json");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"").doesNotContain("components");
    }

    @Test
    void dashboardIsServed() throws Exception {
        HttpResponse<String> response = get("/", "text/html");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Instances currently registered with Eureka");
    }

    @Test
    void registeredInstanceAppearsInRegistryAndDisappearsOnDeregister() throws Exception {
        assertThat(register("TEST-SERVICE", "test-1").statusCode()).isEqualTo(204);

        HttpResponse<String> found = get("/eureka/apps/TEST-SERVICE", "application/json");
        assertThat(found.statusCode()).isEqualTo(200);
        assertThat(found.body()).contains("test-1");

        HttpResponse<String> removed = http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/eureka/apps/TEST-SERVICE/test-1"))
                        .DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(removed.statusCode()).isEqualTo(200);
        assertThat(get("/eureka/apps/TEST-SERVICE", "application/json").body()).doesNotContain("test-1");
    }

    @Test
    void actuatorDoesNotExposeSensitiveEndpoints() throws Exception {
        assertThat(get("/actuator/env", "application/json").statusCode()).isEqualTo(404);
    }
}
