package com.bookstore.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The config-server boots and actually serves a service's configuration from the
 * config-repo (including the marker that proves the source). No database needed.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerApplicationTests {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void servesUserServiceConfig() {
        // Spring Cloud Config endpoint: /{application}/{profile}
        ResponseEntity<String> response = rest.getForEntity("/user-service/default", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("config-server (user-service.yml)");
        assertThat(response.getBody()).contains("config-server (shared application.yml)");
    }
}
