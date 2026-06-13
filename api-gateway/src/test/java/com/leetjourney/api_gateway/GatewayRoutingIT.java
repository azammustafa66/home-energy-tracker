package com.leetjourney.api_gateway;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for api-gateway. WireMock stands in for the downstream
 * user-service (port 8080) and ingestion-service (port 8082) used in the
 * hardcoded route definitions. OAuth is replaced via {@link TestSecurityConfig}.
 *
 * Validates:
 *   - happy-path routing through the gateway
 *   - Resilience4j circuit-breaker fallback when downstream returns 5xx
 */
@SpringBootTest(
        classes = TestSecurityConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Tag("integration")
class GatewayRoutingIT {

    static WireMockServer userBackend;
    static WireMockServer ingestionBackend;

    @LocalServerPort
    int gatewayPort;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void startBackends() {
        userBackend = new WireMockServer(8080);
        userBackend.start();
        ingestionBackend = new WireMockServer(8082);
        ingestionBackend.start();
    }

    @AfterAll
    static void stopBackends() {
        if (userBackend != null) userBackend.stop();
        if (ingestionBackend != null) ingestionBackend.stop();
    }

    @BeforeEach
    void resetStubs() {
        userBackend.resetAll();
        ingestionBackend.resetAll();
    }

    private HttpResponse<String> getJson(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + gatewayPort + path))
                .GET()
                .build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void userRoute_proxiesToUserService() throws Exception {
        userBackend.stubFor(get(urlPathMatching("/api/v1/user/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":1,\"name\":\"Ada\"}")));

        HttpResponse<String> resp = getJson("/api/v1/user/1");
        assertThat(resp.statusCode()).isEqualTo(200);
        assertThat(resp.body()).contains("Ada");
    }

    @Test
    void userRoute_circuitBreakerFallback_on5xx() throws Exception {
        userBackend.stubFor(get(urlPathMatching("/api/v1/user/.*"))
                .willReturn(aResponse().withStatus(500)));

        // The fallback returns 503 with body "User service is down".
        // Drive enough calls for the breaker to open.
        boolean sawFallback = false;
        for (int i = 0; i < 12; i++) {
            HttpResponse<String> resp = getJson("/api/v1/user/1");
            String body = resp.body() == null ? "" : resp.body();
            if (body.contains("User service is down") || resp.statusCode() == 503) {
                sawFallback = true;
                break;
            }
        }
        assertThat(sawFallback)
                .as("expected Resilience4j fallback to engage after repeated downstream 5xx")
                .isTrue();
    }
}
