package com.mrakin.usecases;

import com.mrakin.domain.exception.UrlNotFoundException;
import com.mrakin.domain.model.Url;
import com.mrakin.domain.ports.UrlRepositoryPort;
import com.mrakin.usecases.UrlAccessedKafkaEvent;
import com.mrakin.usecases.UrlAccessedClickHouseEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class GetOriginalUrlUseCase {

    private final UrlRepositoryPort urlRepositoryPort;
    private final Counter getCounter;

    public GetOriginalUrlUseCase(UrlRepositoryPort urlRepositoryPort,
                                 MeterRegistry meterRegistry) {
        this.urlRepositoryPort = urlRepositoryPort;
        this.getCounter = meterRegistry.counter("url.get.requests");
    }

    @Transactional
    @UrlAccessedKafkaEvent(key = "#shortCode")
    @UrlAccessedClickHouseEvent(shortCode = "#shortCode")
    @Cacheable(value = "urls", key = "#shortCode")
    public Url getOriginal(String shortCode) {
        getCounter.increment();
        log.info("Retrieving original URL for short code: {}", shortCode);
        Url url = urlRepositoryPort.findByShortCode(shortCode)
                .orElseThrow(() -> {
                    log.error("URL with short code {} not found", shortCode);
                    return new UrlNotFoundException("URL with short code " + shortCode + " not found");
                });
        urlRepositoryPort.updateLastAccessed(shortCode);
        return url;
    }
}
