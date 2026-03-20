package com.mrakin.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public class ClickHouseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("shortener")
            .withUsername("user")
            .withPassword("password");

    @Container
    static ClickHouseContainer clickHouse = new ClickHouseContainer("clickhouse/clickhouse-server:23.8");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("clickhouse.url", () -> "jdbc:clickhouse:http://" + clickHouse.getHost() + ":" + clickHouse.getMappedPort(8123) + "/default");
        registry.add("clickhouse.user", clickHouse::getUsername);
        registry.add("clickhouse.password", clickHouse::getPassword);

        registry.add("spring.cache.type", () -> "none");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockBean
    private JmsTemplate jmsTemplate;

    @Test
    void shouldTrackUrlAccessInClickHouseAndReturnAggregatedData() throws InterruptedException {
        String originalUrl = "https://example.com/clickhouse-test-" + System.currentTimeMillis();

        // 1. Shorten URL
        ResponseEntity<String> shortenResponse = restTemplate.postForEntity("/api/v1/urls/shorten", originalUrl, String.class);
        assertThat(shortenResponse.getStatusCode().is2xxSuccessful()).isTrue();
        String shortCode = shortenResponse.getBody();
        assertThat(shortCode).isNotNull();

        LocalDateTime beforeAccess = LocalDateTime.now().minusSeconds(2);

        // 2. Access URL 3 times
        for (int i = 0; i < 3; i++) {
            ResponseEntity<String> getResponse = restTemplate.getForEntity("/api/v1/urls/" + shortCode, String.class);
            assertThat(getResponse.getStatusCode().is2xxSuccessful()).isTrue();
        }

        LocalDateTime afterAccess = LocalDateTime.now().plusSeconds(2);

        // Wait for ClickHouse to be ready for query (it's usually synchronous but just in case)
        Thread.sleep(1000);

        // 3. Request aggregated data
        String from = beforeAccess.format(DateTimeFormatter.ISO_DATE_TIME);
        String to = afterAccess.format(DateTimeFormatter.ISO_DATE_TIME);

        Map<String, String> params = Map.of(
                "originalUrl", originalUrl,
                "from", from,
                "to", to
        );

        ResponseEntity<Long> statsResponse = restTemplate.getForEntity(
                "/api/v1/urls/stats/aggregation?originalUrl={originalUrl}&from={from}&to={to}",
                Long.class,
                params);

        assertThat(statsResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(statsResponse.getBody()).isEqualTo(3L);
    }
}
