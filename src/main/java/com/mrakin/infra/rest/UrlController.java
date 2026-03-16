package com.mrakin.infra.rest;

import com.mrakin.usecases.GetOriginalUrlUseCase;
import com.mrakin.usecases.ShortenUrlUseCase;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
public class UrlController {

    private final ShortenUrlUseCase shortenUrlUseCase;
    private final GetOriginalUrlUseCase getOriginalUrlUseCase;

    @PostMapping("/shorten")
    @RateLimiter(name = "shortenLimit")
    public ResponseEntity<String> shorten(@RequestBody String originalUrl) {
        var url = shortenUrlUseCase.shorten(originalUrl.trim());
        return ResponseEntity.ok(url.getShortCode());
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<String> getOriginal(@PathVariable String shortCode) {
        var url = getOriginalUrlUseCase.getOriginal(shortCode);
        return ResponseEntity.ok(url.getOriginalUrl());
    }
}
