package com.bookstore.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared PostgreSQL for all integration tests, using the Testcontainers
 * <em>singleton container</em> pattern: started once per JVM in a static
 * initializer and reused across every test class; Testcontainers' Ryuk stops it
 * when the run ends.
 *
 * <p>This deliberately avoids {@code @Container}'s per-class start/stop lifecycle.
 * With a static container inherited by several classes, {@code @Container} stops
 * the shared container in one class's {@code afterAll} while Spring's cached
 * application context (reused by another class) still points at it — which made
 * the DB health check hang for 30s and fail once four test classes ran together.
 */
public abstract class AbstractPostgresIT {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
