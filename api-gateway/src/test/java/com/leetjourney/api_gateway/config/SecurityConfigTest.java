package com.leetjourney.api_gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SecurityConfig has tight coupling to HttpSecurity which is hard to mock.
 * We restrict ourselves to verifying that the {@code excludedUrls} field is
 * wired to a String[] which is the type permittedAll(String...) expects.
 */
class SecurityConfigTest {

    @Test
    void excludedUrlsField_isStringArray() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "excludedUrls",
                new String[]{"/actuator/**", "/swagger-ui/**"});
        ReflectionTestUtils.setField(config, "jwkSetUri", "http://localhost/jwks");

        Object value = ReflectionTestUtils.getField(config, "excludedUrls");

        assertThat(value).isInstanceOf(String[].class);
        assertThat((String[]) value).containsExactly("/actuator/**", "/swagger-ui/**");
    }

    @Test
    void jwtDecoder_buildsNimbusDecoderFromJwkSetUri() {
        SecurityConfig config = new SecurityConfig();
        ReflectionTestUtils.setField(config, "jwkSetUri", "http://localhost:8091/realms/test/protocol/openid-connect/certs");

        // NimbusJwtDecoder.withJwkSetUri lazily resolves the URI, so calling
        // jwtDecoder() should succeed without making a network request.
        assertThat(config.jwtDecoder()).isNotNull();
    }
}
