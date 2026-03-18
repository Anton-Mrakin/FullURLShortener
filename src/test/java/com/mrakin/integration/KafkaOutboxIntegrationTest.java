package com.mrakin.integration;

import com.mrakin.domain.event.UrlAccessedEvent;
import com.mrakin.domain.model.Url;
import com.mrakin.usecases.GetOriginalUrlUseCase;
import com.mrakin.usecases.ShortenUrlUseCase;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class KafkaOutboxIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("shortener")
            .withUsername("user")
            .withPassword("password");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        // Disable Redis for this test to simplify
        registry.add("spring.cache.type", () -> "none");
    }

    @Autowired
    private ShortenUrlUseCase shortenUrlUseCase;

    @Autowired
    private GetOriginalUrlUseCase getOriginalUrlUseCase;

    private KafkaConsumer<String, UrlAccessedEvent> consumer;

    @BeforeEach
    void setUp() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, UrlAccessedEvent.class.getName());

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("url-accessed"));
    }

    @Test
    void testUrlAccessedEventSentToKafkaWithCorrectPartitioning() {
        // 1. Create a URL
        String originalUrl = "https://google.com/search?q=test";
        String shortCode = shortenUrlUseCase.shorten(originalUrl).getShortCode();

        // 2. Access the URL (this should trigger the event via Aspect + Modulith Outbox)
        Url url = getOriginalUrlUseCase.getOriginal(shortCode);
        assertEquals(originalUrl, url.getOriginalUrl());

        // 3. Verify Kafka message
        ConsumerRecord<String, UrlAccessedEvent> record = pollForRecord();
        assertEquals("google.com", record.key());
        assertEquals(originalUrl, record.value().url().getOriginalUrl());
        
        // Hash of "google.com" with 10 partitions should be consistent
        int partition = record.partition();
        log.info("Event for google.com landed in partition: {}", partition);
        assertTrue(partition >= 0 && partition < 10);

        // 4. Test malformed URL
        String malformedUrl = "not-a-url";
        String malformedShortCode = shortenUrlUseCase.shorten(malformedUrl).getShortCode();
        getOriginalUrlUseCase.getOriginal(malformedShortCode);

        ConsumerRecord<String, UrlAccessedEvent> malformedRecord = pollForRecord();
        assertEquals("", malformedRecord.key());
        assertEquals(0, malformedRecord.partition(), "Malformed URLs should go to partition 0");
    }

    private ConsumerRecord<String, UrlAccessedEvent> pollForRecord() {
        for (int i = 0; i < 10; i++) {
            ConsumerRecords<String, UrlAccessedEvent> records = consumer.poll(Duration.ofSeconds(1));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        throw new AssertionError("No Kafka record found after polling");
    }
}
