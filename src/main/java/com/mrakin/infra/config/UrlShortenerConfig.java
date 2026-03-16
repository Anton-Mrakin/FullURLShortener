package com.mrakin.infra.config;

import com.mrakin.domain.ports.UrlRepositoryPort;
import com.mrakin.usecases.GetOriginalUrlUseCase;
import com.mrakin.usecases.ShortCodeGenerator;
import com.mrakin.usecases.ShortenUrlUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement(order = 100)
public class UrlShortenerConfig {

    @Value("${app.url-limit:10000}")
    private long urlLimit;

    @Bean
    public ShortenUrlUseCase shortenUrlUseCase(UrlRepositoryPort urlRepositoryPort, 
                                               ShortCodeGenerator shortCodeGenerator,
                                               MeterRegistry meterRegistry) {
        return new ShortenUrlUseCase(urlRepositoryPort, shortCodeGenerator, meterRegistry, urlLimit);
    }

    @Bean
    public GetOriginalUrlUseCase getOriginalUrlUseCase(UrlRepositoryPort urlRepositoryPort,
                                                       MeterRegistry meterRegistry) {
        return new GetOriginalUrlUseCase(urlRepositoryPort, meterRegistry);
    }
}
