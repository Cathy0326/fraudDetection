package com.cathy.frauddetection;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared container base for integration tests.
 *
 * Singleton container pattern: containers are started once, in a static
 * initializer, and never stopped by this code. JUnit's @Testcontainers +
 * @Container would restart them per test class; with six test classes
 * touching Postgres or Redis, that means twelve cold starts instead of two.
 * The Ryuk reaper (bundled with Testcontainers) kills them when the JVM exits.
 */
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine"));

    // UNVERIFIED: GenericContainer has no built-in ConnectionDetails type the
    // way PostgreSQLContainer does, so @ServiceConnection needs an explicit
    // name to find the Redis factory. Confirm in IntelliJ before relying on
    // this. If it doesn't resolve, delete this field and uncomment the
    // @DynamicPropertySource fallback below instead.
    @ServiceConnection("redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:8-alpine"))
                    .withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    // Fallback if @ServiceConnection("redis") does not resolve:
    //
    // @DynamicPropertySource
    // static void redisProperties(DynamicPropertyRegistry registry) {
    //     registry.add("spring.data.redis.host", REDIS::getHost);
    //     registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    // }
}