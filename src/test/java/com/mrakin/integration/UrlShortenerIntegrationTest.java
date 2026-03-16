package com.mrakin.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mrakin.infra.rest.dto.UrlRequestDto;
import com.mrakin.infra.rest.dto.UrlResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class UrlShortenerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("shortener")
            .withUsername("user")
            .withPassword("password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.test.iterations:1000}")
    private int iterations;

    @Value("${app.test.threads:100}")
    private int threadCount;

    @Value("${app.test.latency.shorten:10.0}")
    private double shortenLatencyThreshold;

    @Value("${app.test.latency.get:3.0}")
    private double getLatencyThreshold;

    @Value("${app.test.throughput.shorten:100.0}")
    private double shortenThroughputThreshold;

    @Value("${app.test.throughput.get:300.0}")
    private double getThroughputThreshold;

    @Test
    void testShortenAndRetrieveUrl() throws Exception {
        String originalUrl = "https://example.com/very/long/url/that/needs/shortening";
        UrlRequestDto request = new UrlRequestDto(originalUrl);

        // 1. Shorten
        MvcResult result = mockMvc.perform(post("/api/v1/urls/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value(originalUrl))
                .andExpect(jsonPath("$.shortCode").exists())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        UrlResponseDto response = objectMapper.readValue(content, UrlResponseDto.class);
        String shortCode = response.getShortCode();

        // 2. Retrieve
        mockMvc.perform(get("/api/v1/urls/" + shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalUrl").value(originalUrl))
                .andExpect(jsonPath("$.shortCode").value(shortCode));
    }

    @Test
    void testPerformance() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(iterations);

        long startShorten = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    String url = "https://example.com/" + index;
                    UrlRequestDto request = new UrlRequestDto(url);
                    mockMvc.perform(post("/api/v1/urls/shorten")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andExpect(status().isOk());
                } catch (Exception e) {
                    log.error("Error during performance shorten test", e);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(30, TimeUnit.SECONDS);
        long endShorten = System.currentTimeMillis();

        long totalTimeShorten = endShorten - startShorten;
        double avgShorten = (double) totalTimeShorten / iterations;
        double throughputShorten = (double) iterations / (totalTimeShorten / 1000.0);

        log.info("[DEBUG_LOG] Shorten URLs ({} total, {} threads):", iterations, threadCount);
        log.info("[DEBUG_LOG] Avg shorten time: {} ms", avgShorten);
        log.info("[DEBUG_LOG] Shorten throughput: {} req/sec", throughputShorten);

        assertTrue(avgShorten < shortenLatencyThreshold, "Average shorten latency too high: " + avgShorten);
        assertTrue(throughputShorten > shortenThroughputThreshold, "Shorten throughput too low: " + throughputShorten);

        // Для теста получения возьмем один существующий код
        String lastUrl = "https://example.com/0";
        MvcResult lastResult = mockMvc.perform(post("/api/v1/urls/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UrlRequestDto(lastUrl))))
                .andReturn();
        String shortCode = objectMapper.readValue(lastResult.getResponse().getContentAsString(), UrlResponseDto.class).getShortCode();

        CountDownLatch latchGet = new CountDownLatch(iterations);
        long startRetrieve = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            executor.submit(() -> {
                try {
                    mockMvc.perform(get("/api/v1/urls/" + shortCode))
                            .andExpect(status().isOk());
                } catch (Exception e) {
                    log.error("Error during performance retrieve test", e);
                } finally {
                    latchGet.countDown();
                }
            });
        }
        latchGet.await(30, TimeUnit.SECONDS);
        long endRetrieve = System.currentTimeMillis();

        long totalTimeGet = endRetrieve - startRetrieve;
        double avgGet = (double) totalTimeGet / iterations;
        double throughputGet = (double) iterations / (totalTimeGet / 1000.0);

        log.info("[DEBUG_LOG] Retrieve URLs ({} total, {} threads):", iterations, threadCount);
        log.info("[DEBUG_LOG] Avg retrieve time: {} ms", avgGet);
        log.info("[DEBUG_LOG] Retrieve throughput: {} req/sec", throughputGet);

        executor.shutdown();

        assertTrue(avgGet < getLatencyThreshold, "Average get latency too high: " + avgGet);
        assertTrue(throughputGet > getThroughputThreshold, "Retrieve throughput too low: " + throughputGet);
    }
}
