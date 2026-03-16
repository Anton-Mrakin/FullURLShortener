package com.mrakin.usecases;

import com.mrakin.domain.exception.UrlNotFoundException;
import com.mrakin.domain.model.Url;
import com.mrakin.domain.ports.UrlRepositoryPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetOriginalUrlUseCase {

    private final UrlRepositoryPort urlRepositoryPort;
    private final Counter getCounter;

    public GetOriginalUrlUseCase(UrlRepositoryPort urlRepositoryPort,
                                 MeterRegistry meterRegistry) {
        this.urlRepositoryPort = urlRepositoryPort;
        this.getCounter = meterRegistry.counter("url_get_requests_total");
    }

    public Url getOriginal(String shortCode) {
        getCounter.increment();
        log.info("Retrieving original URL for short code: {}", shortCode);
        return urlRepositoryPort.findByShortCode(shortCode)
                .orElseThrow(() -> {
                    log.error("URL with short code {} not found", shortCode);
                    return new UrlNotFoundException("URL with short code " + shortCode + " not found");
                });
    }
}
