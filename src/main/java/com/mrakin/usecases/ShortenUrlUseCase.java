package com.mrakin.usecases;

import com.mrakin.domain.model.Url;
import com.mrakin.domain.ports.UrlRepositoryPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ShortenUrlUseCase {

    private final UrlRepositoryPort urlRepositoryPort;
    private final ShortCodeGenerator shortCodeGenerator;
    private final long urlLimit;
    private final Counter shortenCounter;

    public ShortenUrlUseCase(UrlRepositoryPort urlRepositoryPort,
                             ShortCodeGenerator shortCodeGenerator,
                             MeterRegistry meterRegistry,
                             long urlLimit) {
        this.urlRepositoryPort = urlRepositoryPort;
        this.shortCodeGenerator = shortCodeGenerator;
        this.urlLimit = urlLimit;
        this.shortenCounter = meterRegistry.counter("url_shorten_requests_total");
        
        meterRegistry.gauge("url_stored_total", urlRepositoryPort, 
                UrlRepositoryPort::count);
    }

    public Url shorten(String originalUrl) {
        shortenCounter.increment();
        log.info("Shortening URL: {}", originalUrl);
        
        return urlRepositoryPort.findByOriginalUrl(originalUrl)
                .orElseGet(() -> {
                    long currentCount = urlRepositoryPort.count();
                    if (currentCount >= urlLimit) {
                        log.warn("URL limit reached: {}", urlLimit);
                        throw new IllegalStateException("Storage limit reached. Max allowed: " + urlLimit);
                    }
                    
                    String shortCode = shortCodeGenerator.generate(originalUrl);
                    Url url = Url.builder()
                            .originalUrl(originalUrl)
                            .shortCode(shortCode)
                            .build();
                    Url saved = urlRepositoryPort.save(url);
                    log.debug("Saved new URL: {} -> {}", originalUrl, shortCode);
                    return saved;
                });
    }
}
