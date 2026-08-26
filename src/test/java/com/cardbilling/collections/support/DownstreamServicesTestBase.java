package com.cardbilling.collections.support;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.redis.testcontainers.RedisContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.utility.DockerImageName;

/**
 * A real Redis and stubbed versions of the two services this one talks to.
 *
 * <p>{@code billing-service} and {@code notification-service} are separate deployables that may not
 * exist yet, let alone be running — so they are WireMock servers stubbed to the contracts in
 * {@code ARCHITECTURE.md}. Redis is not stubbed: the whole resilience story is what happens to a
 * cache entry across a freshness window and a TTL, and an in-memory fake would be testing the fake.
 *
 * <p>Containers and servers are static and started once for the JVM rather than per class —
 * Testcontainers' Ryuk sidecar tears them down when the run ends.
 */
public abstract class DownstreamServicesTestBase {

    protected static final RedisContainer REDIS =
            new RedisContainer(DockerImageName.parse("redis:8-alpine").asCompatibleSubstituteFor("redis"));

    protected static final WireMockServer BILLING_SERVICE =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    protected static final WireMockServer NOTIFICATION_SERVICE =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        REDIS.start();
        BILLING_SERVICE.start();
        NOTIFICATION_SERVICE.start();
    }

    @DynamicPropertySource
    static void downstreamProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("collections.billing-service.base-url", BILLING_SERVICE::baseUrl);
        registry.add("collections.notification-service.base-url", NOTIFICATION_SERVICE::baseUrl);
    }
}
