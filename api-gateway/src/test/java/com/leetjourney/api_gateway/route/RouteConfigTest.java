package com.leetjourney.api_gateway.route;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lightweight unit tests for the route configuration classes. We don't load a
 * Spring context here; we instantiate the @Configuration classes directly and
 * verify that each declared @Bean factory returns a non-null RouterFunction.
 * This guards against compile-time/refactoring regressions without requiring
 * any backend services or Resilience4j infrastructure to be live.
 */
class RouteConfigTest {

    @Test
    void deviceServiceRoutes_allBeanFactoriesProduceNonNullRouterFunctions() {
        DeviceServiceRoutes routes = new DeviceServiceRoutes();

        RouterFunction<ServerResponse> main = routes.deviceRoute();
        RouterFunction<ServerResponse> fallback = routes.deviceFallbackRoute();
        RouterFunction<ServerResponse> apiDocs = routes.deviceServiceApiDocs();

        assertThat(main).isNotNull();
        assertThat(fallback).isNotNull();
        assertThat(apiDocs).isNotNull();
    }

    @Test
    void userServiceRoutes_allBeanFactoriesProduceNonNullRouterFunctions() {
        UserServiceRoutes routes = new UserServiceRoutes();

        assertThat(routes.userRoute()).isNotNull();
        assertThat(routes.userFallbackRoute()).isNotNull();
        assertThat(routes.userServiceApiDocs()).isNotNull();
    }

    @Test
    void ingestionServiceRoutes_allBeanFactoriesProduceNonNullRouterFunctions() {
        IngestionServiceRoutes routes = new IngestionServiceRoutes();

        assertThat(routes.ingestionRoute()).isNotNull();
        assertThat(routes.ingestionFallbackRoute()).isNotNull();
    }

    @Test
    void insightServiceRoutes_allBeanFactoriesProduceNonNullRouterFunctions() {
        InsightServiceRoutes routes = new InsightServiceRoutes();

        assertThat(routes.insightRoute()).isNotNull();
        assertThat(routes.insightFallbackRoute()).isNotNull();
    }
}
