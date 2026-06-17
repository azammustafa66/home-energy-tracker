package com.demo.ingestionservice.simulation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

/**
 * Fetches and caches a client_credentials access token from Keycloak.
 * Refreshes 30s before expiry to keep the simulator from racing the expiration.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "simulation.mode")
public class ClientCredentialsTokenProvider {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${simulation.auth.token-uri}")
    private String tokenUri;
    @Value("${simulation.auth.client-id}")
    private String clientId;
    @Value("${simulation.auth.client-secret}")
    private String clientSecret;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    public synchronized String getToken() {
        if (cachedToken != null && Instant.now().isBefore(expiresAt.minusSeconds(30))) {
            return cachedToken;
        }
        return refresh();
    }

    @SuppressWarnings("unchecked")
    private String refresh() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        Map<String, Object> resp = restTemplate.postForObject(
                tokenUri, new HttpEntity<>(form, headers), Map.class);

        if (resp == null || resp.get("access_token") == null) {
            throw new IllegalStateException("No access_token in Keycloak response");
        }
        cachedToken = (String) resp.get("access_token");
        int expiresIn = ((Number) resp.getOrDefault("expires_in", 60)).intValue();
        expiresAt = Instant.now().plusSeconds(expiresIn);
        log.info("Fetched new ingestion token, expires at {}", expiresAt);
        return cachedToken;
    }
}
