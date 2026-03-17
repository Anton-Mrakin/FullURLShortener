package com.mrakin.infra.aspect;

import com.mrakin.domain.ports.UrlRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class UrlLimitAspect {

    private final UrlRepositoryPort urlRepositoryPort;

    @Value("${app.url-limit:10000}")
    private long urlLimit;

    @Async
    @AfterReturning(pointcut = "@annotation(com.mrakin.usecases.CleanupUrlLimit)")
    public void manageUrlLimit() {
        try {
            long deletedCount = urlRepositoryPort.deleteOldest(urlLimit);
            if (deletedCount > 0) {
                log.info("URL limit cleanup performed: {} records removed.", deletedCount);
            }
        } catch (Exception e) {
            log.error("Error during asynchronous batch URL limit cleanup", e);
        }
    }
}
