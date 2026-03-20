package com.mrakin.infra.rest;

import com.mrakin.usecases.GetOriginalUrlUseCase;
import com.mrakin.usecases.GetUrlAggregationUseCase;
import com.mrakin.usecases.ShortenUrlUseCase;
import com.mrakin.infra.rest.mapper.UrlRestMapper;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
public class UrlController {

    private final ShortenUrlUseCase shortenUrlUseCase;
    private final GetOriginalUrlUseCase getOriginalUrlUseCase;
    private final GetUrlAggregationUseCase getUrlAggregationUseCase;
    private final UrlRestMapper urlRestMapper;

    @PostMapping("/shorten")
    @RateLimiter(name = "shortenLimit")
    public ResponseEntity<String> shorten(@RequestBody String originalUrl) {
        var urlDomain = urlRestMapper.toDomain(originalUrl.trim());
        var shortenedUrl = shortenUrlUseCase.shorten(urlDomain.getOriginalUrl());
        return ResponseEntity.ok(urlRestMapper.toShortCode(shortenedUrl));
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<String> getOriginal(@PathVariable String shortCode) {
        var url = getOriginalUrlUseCase.getOriginal(shortCode);
        return ResponseEntity.ok(urlRestMapper.toOriginalUrl(url));
    }

    @GetMapping("/stats/aggregation")
    public ResponseEntity<Long> getAggregation(
            @RequestParam String originalUrl,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(getUrlAggregationUseCase.getAggregation(originalUrl, from, to));
    }
}
