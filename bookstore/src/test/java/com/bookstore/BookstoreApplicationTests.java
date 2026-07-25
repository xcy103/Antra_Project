package com.bookstore;

import com.bookstore.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests: the full application context must load (now including the JPA
 * datasource, Flyway migrations and schema validation against a real PostgreSQL)
 * and the app must report itself healthy over HTTP.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookstoreApplicationTests extends AbstractPostgresIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
        // Fails if the application context cannot be built.
    }

    @Test
    void healthEndpointReportsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
