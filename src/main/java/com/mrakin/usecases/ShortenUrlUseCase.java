package com.mrakin.usecases;

import com.mrakin.domain.model.Url;
import com.mrakin.domain.ports.UrlRepositoryPort;
import com.mrakin.usecases.generator.ShortCodeGenerator;
import com.mrakin.usecases.validation.UrlValidator;
import com.mrakin.usecases.CleanupUrlLimit;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ShortenUrlUseCase {

    private final UrlRepositoryPort urlRepositoryPort;
    private final ShortCodeGenerator shortCodeGenerator;
    private final List<UrlValidator> urlValidators;
    private final Counter shortenCounter;

    public ShortenUrlUseCase(UrlRepositoryPort urlRepositoryPort,
                             @Qualifier("selectedShortCodeGenerator")
                             ShortCodeGenerator shortCodeGenerator,
                             List<UrlValidator> urlValidators,
                             MeterRegistry meterRegistry) {
        this.urlRepositoryPort = urlRepositoryPort;
        this.shortCodeGenerator = shortCodeGenerator;
        this.urlValidators = urlValidators;
        this.shortenCounter = meterRegistry.counter("url.shorten.requests");
    }

    @Retry(name = "shortenRetry")
    @CleanupUrlLimit
    public Url shorten(String originalUrl) {
        urlValidators.forEach(v -> v.validate(originalUrl));
        shortenCounter.increment();
        log.info("Shortening URL: {}", originalUrl);

        return urlRepositoryPort.findByOriginalUrl(originalUrl)
                .orElseGet(() -> {
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
