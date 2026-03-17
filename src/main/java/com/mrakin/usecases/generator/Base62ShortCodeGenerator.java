package com.mrakin.usecases.generator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

@Service("base62Generator")
public class Base62ShortCodeGenerator implements ShortCodeGenerator {

    private static final String BASE62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int BASE = 62;

    @Value("${app.short-code-length:8}")
    private int shortCodeLength;

    @Value("${app.generator.seed:#{null}}")
    private Long seed;

    private final ThreadLocal<Random> threadLocalRandom = ThreadLocal.withInitial(() ->
            seed != null ? new Random(seed) : new Random()
    );

    @Override
    public String generate(String originalUrl) {
        if (originalUrl == null || originalUrl.isBlank()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }

        Random random = (seed != null) ? threadLocalRandom.get() : ThreadLocalRandom.current();
        long randomValue = random.nextLong() & Long.MAX_VALUE;
        
        return encode(randomValue);
    }

    private String encode(long value) {
        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.insert(0, BASE62.charAt((int) (value % BASE)));
            value /= BASE;
        }
        
        while (sb.length() < shortCodeLength) {
            sb.append('0');
        }
        
        return sb.length() > shortCodeLength ? sb.substring(0, shortCodeLength) : sb.toString();
    }
}
