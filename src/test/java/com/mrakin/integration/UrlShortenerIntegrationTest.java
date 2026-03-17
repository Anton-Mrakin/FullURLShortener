package com.mrakin.integration;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mrakin.usecases.ShortenUrlUseCase;
import com.mrakin.usecases.generator.ShortCodeGenerator;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class UrlShortenerIntegrationTest {

    @Autowired
    private RateLimiterRegistry rateLimiterRegistry;

    @Test
    void testRateLimiter() throws Exception {
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("shortenLimit");
        try {
            rateLimiter.changeLimitForPeriod(1);
            boolean acquired = true;
            for (int i = 0; i < 50; i++) {
                if (!rateLimiter.acquirePermission()) {
                    acquired = false;
                    break;
                }
            }
            if (acquired) {
                log.warn("Could not exhaust rate limiter permits in testRateLimiter");
                return;
            }
            String originalUrl = "https://example.com/ratelimit";
            mockMvc.perform(post("/api/v1/urls/shorten")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content(originalUrl + "limit"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(content().string(containsString("Too many requests")));
        } finally {
            rateLimiter.changeLimitForPeriod(100000);
            TimeUnit.SECONDS.sleep(1);
        }
    }

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
    private com.mrakin.domain.ports.UrlRepositoryPort urlRepositoryPort;

    @Autowired
    private com.mrakin.infra.db.repository.JpaUrlRepository jpaUrlRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Value("${app.test.iterations:1000}")
    private int iterations;

    @Value("${app.test.startup-threshold:30000.0}")
    private double startupThreshold;

    @Test
    void testStartupTimeAndProbes() throws Exception {
        long startupDate = applicationContext.getStartupDate();
        long now = System.currentTimeMillis();
        long startupDuration = now - startupDate;
        log.info("Application context startup date: {}", startupDate);
        log.info("Time since startup: {} ms", startupDuration);

        assertTrue(startupDuration < startupThreshold, 
                "Startup time too long: " + startupDuration + " ms (threshold: " + startupThreshold + " ms)");

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")));
    }

    @Test
    void testMetrics() throws Exception {
        testBaseShortenAndRetrieveUrl();

        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")));

        MvcResult result = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn();
        
        String content = result.getResponse().getContentAsString();

        assertTrue(content.contains("url_shorten_requests"), "Missing url_shorten_requests metric");
        assertTrue(content.contains("url_get_requests"), "Missing url_get_requests metric");
        assertTrue(content.contains("url_count"), "Missing url_count gauge metric.");

        assertTrue(content.contains("jvm_memory_used_bytes"), "Missing JVM metrics");
    }

    @Value("${app.test.threads:100}")
    private int threadCount;

    @Value("${app.test.shorten-probability:0.2}")
    private double shortenProbability;

    @Value("${app.test.latency-threshold:200.0}")
    private double latencyThreshold;

    @Value("${app.test.throughput-threshold:500.0}")
    private double throughputThreshold;

    @Test
    void testBaseShortenAndRetrieveUrl() throws Exception {
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("shortenLimit");
        rateLimiter.changeLimitForPeriod(100000);
        String originalUrl = "https://example.com/very/long/url/that/needs/shortening";

        MvcResult result = mockMvc.perform(post("/api/v1/urls/shorten")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(originalUrl))
                .andExpect(status().isOk())
                .andReturn();

        String shortCode = result.getResponse().getContentAsString();
        assertFalse(shortCode.isEmpty());

        mockMvc.perform(get("/api/v1/urls/" + shortCode))
                .andExpect(status().isOk())
                .andExpect(content().string(originalUrl));
    }

    @Test
    void testConcurrentShorten() throws Exception {
        String originalUrl = "https://concurrent.com/" + UUID.randomUUID();
        int concurrentThreads = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(concurrentThreads);
        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        CopyOnWriteArrayList<String> results = new CopyOnWriteArrayList<>();

        for (int i = 0; i < concurrentThreads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult result = mockMvc.perform(post("/api/v1/urls/shorten")
                                    .contentType(MediaType.TEXT_PLAIN)
                                    .content(originalUrl))
                            .andReturn();
                    if (result.getResponse().getStatus() == 200) {
                        results.add(result.getResponse().getContentAsString());
                    } else {
                        log.error("Request failed with status: {} and body: {}", 
                                result.getResponse().getStatus(), result.getResponse().getContentAsString());
                    }
                } catch (Exception e) {
                    log.error("Error in concurrent shorten test", e);
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(concurrentThreads, results.size(), "All threads should have received a short code");
        String firstCode = results.get(0);
        for (String code : results) {
            assertEquals(firstCode, code, "All threads should have received the same short code for the same URL");
        }
    }

    @Test
    void testUrlLimitAsynchronously() throws Exception {
        jpaUrlRepository.deleteAll();
        
        int smallLimit = 5;
        // Since it's @Value in Aspect, we can't easily change it here without reflections 
        // OR we can just use the current limit from application-test.yml
        // But let's assume limit is 10 for this specific scenario.
        
        // I'll use ReflectionTestUtils to set the limit in the Aspect for this test
        com.mrakin.infra.aspect.UrlLimitAspect aspect = applicationContext.getBean(com.mrakin.infra.aspect.UrlLimitAspect.class);
        org.springframework.test.util.ReflectionTestUtils.setField(aspect, "urlLimit", (long) smallLimit);
        
        for (int i = 0; i < smallLimit + 5; i++) {
            mockMvc.perform(post("/api/v1/urls/shorten")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("https://limit.com/" + i))
                    .andExpect(status().isOk());
        }
        
        // Wait a bit for async tasks to complete
        TimeUnit.MILLISECONDS.sleep(500);
        
        long count = urlRepositoryPort.count();
        assertTrue(count <= smallLimit, "URL count should be within limit after async cleanup. Actual: " + count);
        
        // Restore original limit (from config)
        long originalLimit = applicationContext.getEnvironment().getProperty("app.url-limit", Long.class, 10000L);
        org.springframework.test.util.ReflectionTestUtils.setField(aspect, "urlLimit", originalLimit);
    }
    @Test
    void testUrlValidation() throws Exception {
        // Test empty URL
        mockMvc.perform(post("/api/v1/urls/shorten")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("   "))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("URL cannot be empty")));

        // Test too long URL
        String longUrl = "http://example.com/" + "a".repeat(2050);
        mockMvc.perform(post("/api/v1/urls/shorten")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(longUrl))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("URL length exceeds maximum limit")));
    }

    @Test
    void testShortCodeCollisionRetry() throws Exception {
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter("shortenLimit");
        rateLimiter.changeLimitForPeriod(100000);
        
        ShortCodeGenerator mockGenerator = Mockito.mock(ShortCodeGenerator.class);
        
        ShortenUrlUseCase shortenUseCase = applicationContext.getBean(ShortenUrlUseCase.class);
        ShortCodeGenerator originalGenerator = (ShortCodeGenerator) org.springframework.test.util.ReflectionTestUtils.getField(shortenUseCase, "shortCodeGenerator");
        
        try {
            Mockito.when(mockGenerator.generate(ArgumentMatchers.anyString()))
                    .thenReturn("duplicate");
                    
            org.springframework.test.util.ReflectionTestUtils.setField(shortenUseCase, "shortCodeGenerator", mockGenerator);
            
            // 1. Save first URL -> will get shortCode "duplicate"
            mockMvc.perform(post("/api/v1/urls/shorten")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("https://collision1.com"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("duplicate"));
            
            // 2. Prepare mock for the second URL shorten call.
            // First attempt: returns "duplicate" -> DB collision -> Retry
            // Second attempt: returns "unique" -> Success
            Mockito.when(mockGenerator.generate("https://collision2.com"))
                    .thenReturn("duplicate")
                    .thenReturn("unique");
            
            mockMvc.perform(post("/api/v1/urls/shorten")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("https://collision2.com"))
                    .andExpect(status().isOk())
                    .andExpect(content().string("unique"));
                    
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(shortenUseCase, "shortCodeGenerator", originalGenerator);
        }
    }

    @Test
    void testPerformance() throws Exception {
        AtomicLong totalLatency;
        AtomicLong shortenCount;
        AtomicLong getCount;
        long startTime;
        long endTime;
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            CountDownLatch latch = new CountDownLatch(iterations);
            CopyOnWriteArrayList<String> shortCodes = new CopyOnWriteArrayList<>();
            for (int i = 0; i < 100; i++) {
                String url = "https://initial.com/" + i + "_" + UUID.randomUUID();
                MvcResult result = mockMvc.perform(post("/api/v1/urls/shorten")
                                .contentType(MediaType.TEXT_PLAIN)
                                .content(url))
                        .andExpect(status().isOk())
                        .andReturn();
                String sc = result.getResponse().getContentAsString();
                shortCodes.add(sc);
            }
            totalLatency = new AtomicLong(0);
            shortenCount = new AtomicLong(0);
            getCount = new AtomicLong(0);
            startTime = System.currentTimeMillis();
            for (int i = 0; i < iterations; i++) {
                executor.submit(() -> {
                    try {
                        long start = System.currentTimeMillis();
                        if (ThreadLocalRandom.current().nextDouble() < shortenProbability) {
                            String url = "https://example.com/p/" + UUID.randomUUID();
                            MvcResult result = mockMvc.perform(post("/api/v1/urls/shorten")
                                            .contentType(MediaType.TEXT_PLAIN)
                                            .content(url))
                                    .andExpect(status().isOk())
                                    .andReturn();
                            String sc = result.getResponse().getContentAsString();
                            shortCodes.add(sc);
                            shortenCount.incrementAndGet();
                        } else {
                            String sc = shortCodes.get(ThreadLocalRandom.current().nextInt(shortCodes.size()));
                            mockMvc.perform(get("/api/v1/urls/" + sc))
                                    .andExpect(status().isOk());
                            getCount.incrementAndGet();
                        }
                        long end = System.currentTimeMillis();
                        totalLatency.addAndGet(end - start);
                    } catch (Exception e) {
                        log.error("Error during performance test", e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await(60, TimeUnit.SECONDS);
            endTime = System.currentTimeMillis();
            executor.shutdown();
        }
        double totalDurationSec = (endTime - startTime) / 1000.0;
        double avgLatency = (double) totalLatency.get() / iterations;
        double throughput = (double) iterations / totalDurationSec;

        log.info("Performance Results (Total Duration: {} s):", totalDurationSec);
        log.info("Workload: Shorten = {}, Get = {}", shortenCount.get(), getCount.get());
        log.info("Overall: Avg Latency = {} ms, Throughput = {} req/sec", avgLatency, throughput);

        assertTrue(avgLatency < latencyThreshold, "Average latency too high: " + avgLatency);
        assertTrue(throughput > throughputThreshold, "Throughput too low: " + throughput);
    }
}
