package com.mrakin.usecases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mrakin.domain.exception.UrlNotFoundException;
import com.mrakin.domain.model.Url;
import com.mrakin.domain.ports.UrlRepositoryPort;
import com.mrakin.usecases.generator.ShortCodeGenerator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UrlUseCaseTest {

    @Mock
    private UrlRepositoryPort urlRepositoryPort;
    
    @Mock
    private ShortCodeGenerator shortCodeGenerator;
    
    @Mock
    private MeterRegistry meterRegistry;
    
    @Mock
    private Counter counter;

    private ShortenUrlUseCase shortenUrlUseCase;
    private GetOriginalUrlUseCase getOriginalUrlUseCase;

    @BeforeEach
    void setUp() {
        when(meterRegistry.counter(any())).thenReturn(counter);
        shortenUrlUseCase = new ShortenUrlUseCase(urlRepositoryPort, shortCodeGenerator, meterRegistry, 10000L);
        getOriginalUrlUseCase = new GetOriginalUrlUseCase(urlRepositoryPort, meterRegistry);
    }

    @ParameterizedTest
    @CsvSource({
        "https://google.com, goog123",
        "https://github.com, gith456",
        "https://openai.com, open789"
    })
    void shorten_NewUrl_ShouldSaveAndReturn(String originalUrl, String expectedShortCode) {
        when(urlRepositoryPort.findByOriginalUrl(originalUrl)).thenReturn(Optional.empty());
        when(urlRepositoryPort.save(any(Url.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shortCodeGenerator.generate(originalUrl)).thenReturn(expectedShortCode);

        Url result = shortenUrlUseCase.shorten(originalUrl);

        assertNotNull(result);
        assertEquals(originalUrl, result.getOriginalUrl());
        assertEquals(expectedShortCode, result.getShortCode());
        verify(urlRepositoryPort).save(any(Url.class));
        verify(urlRepositoryPort, never()).updateLastAccessed(any());
    }

    @Test
    void shorten_ExistingUrl_ShouldReturnFromDb() {
        String originalUrl = "https://existing.com";
        Url existingUrl = Url.builder()
                .originalUrl(originalUrl)
                .shortCode("exist123")
                .build();
        when(urlRepositoryPort.findByOriginalUrl(originalUrl)).thenReturn(Optional.of(existingUrl));

        Url result = shortenUrlUseCase.shorten(originalUrl);

        assertEquals(existingUrl, result);
        verify(urlRepositoryPort, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({
        "short1, https://url1.com",
        "short2, https://url2.com"
    })
    void getOriginal_ExistingCode_ShouldReturnUrl(String shortCode, String originalUrl) {
        Url url = Url.builder()
                .originalUrl(originalUrl)
                .shortCode(shortCode)
                .build();
        when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.of(url));

        Url result = getOriginalUrlUseCase.getOriginal(shortCode);

        assertEquals(originalUrl, result.getOriginalUrl());
        verify(urlRepositoryPort).updateLastAccessed(shortCode);
    }

    @Test
    void getOriginal_NonExistingCode_ShouldThrowException() {
        String shortCode = "notfound";
        when(urlRepositoryPort.findByShortCode(shortCode)).thenReturn(Optional.empty());

        assertThrows(UrlNotFoundException.class, () -> getOriginalUrlUseCase.getOriginal(shortCode));
    }

    @Test
    void shorten_LimitReached_ShouldDeleteOldestAndSave() {
        String url = "https://example.com";
        String code = "short123";
        shortenUrlUseCase = new ShortenUrlUseCase(urlRepositoryPort, shortCodeGenerator, meterRegistry, 5L);
        when(urlRepositoryPort.findByOriginalUrl(url)).thenReturn(Optional.empty());
        when(urlRepositoryPort.count()).thenReturn(5L);
        when(shortCodeGenerator.generate(url)).thenReturn(code);
        when(urlRepositoryPort.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Url result = shortenUrlUseCase.shorten(url);

        assertNotNull(result);
        assertEquals(code, result.getShortCode());
        verify(urlRepositoryPort).deleteOldest();
        verify(urlRepositoryPort).save(any());
        verify(urlRepositoryPort, never()).updateLastAccessed(any());
    }
}
