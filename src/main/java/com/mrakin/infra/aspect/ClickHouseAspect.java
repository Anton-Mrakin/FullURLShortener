package com.mrakin.infra.aspect;

import com.mrakin.domain.model.Url;
import com.mrakin.infra.db.repository.ClickHouseAccessLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class ClickHouseAspect {

    private final ClickHouseAccessLogRepository repository;

    @AfterReturning(pointcut = "@annotation(com.mrakin.usecases.UrlAccessedClickHouseEvent)", returning = "result")
    public void logAccess(Object result) {
        if (result instanceof Url url) {
            log.debug("Logging ClickHouse event for URL: {}", url.getOriginalUrl());
            try {
                repository.save(url.getShortCode(), url.getOriginalUrl(), LocalDateTime.now());
            } catch (Exception e) {
                log.error("Failed to save event to ClickHouse", e);
            }
        }
    }
}
