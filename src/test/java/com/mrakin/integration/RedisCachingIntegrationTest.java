package com.mrakin.integration;

import com.mrakin.domain.ports.UrlRepositoryPort;
import com.mrakin.usecases.GetOriginalUrlUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "spring.cache.type=redis")
@Testcontainers
@ActiveProfiles("test")
class RedisCachingIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.0-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private GetOriginalUrlUseCase getOriginalUrlUseCase;

    @MockBean
    private UrlRepositoryPort urlRepositoryPort;

    @Test
    void testGetOriginalUrlCaching() {
        String shortCode = "testCode";
        String originalUrl = "https://example.com";
        com.mrakin.domain.model.Url url = com.mrakin.domain.model.Url.builder()
                .shortCode(shortCode)
                .originalUrl(originalUrl)
                .build();

        // Setup repository spy behavior
        Mockito.doReturn(Optional.of(url)).when(urlRepositoryPort).findByShortCode(shortCode);

        // First call - should go to repository
        com.mrakin.domain.model.Url result1 = getOriginalUrlUseCase.getOriginal(shortCode);
        assertEquals(originalUrl, result1.getOriginalUrl());

        // Second call - should come from cache, so repository findByShortCode should NOT be called again
        com.mrakin.domain.model.Url result2 = getOriginalUrlUseCase.getOriginal(shortCode);
        assertEquals(originalUrl, result2.getOriginalUrl());

        // Verify repository was called exactly once
        verify(urlRepositoryPort, times(1)).findByShortCode(shortCode);
    }
}
