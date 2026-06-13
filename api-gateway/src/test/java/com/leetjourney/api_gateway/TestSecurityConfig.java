package com.leetjourney.api_gateway;

import com.leetjourney.api_gateway.config.SecurityConfig;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.mockito.Mockito.mock;

/**
 * Test entry point that re-scans the api-gateway packages while excluding the
 * production {@link SecurityConfig} (which requires a reachable Keycloak JWKS).
 * Provides a permit-all SecurityFilterChain plus a mocked JwtDecoder.
 */
@SpringBootApplication
@ComponentScan(
        basePackages = "com.leetjourney.api_gateway",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = SecurityConfig.class))
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .build();
    }

    @Bean
    public JwtDecoder testJwtDecoder() {
        return mock(JwtDecoder.class);
    }
}
