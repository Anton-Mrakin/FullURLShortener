package com.mrakin.infra.maintenance;

import com.mrakin.domain.ports.UrlRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UrlMaintenanceService {

    private final UrlRepositoryPort urlRepositoryPort;

    @Value("${app.url-limit:10000}")
    private long urlLimit;

    /**
     * Periodic cleanup of old URLs to maintain the limit.
     * Runs frequently to ensure store is clean.
     * Timeout is set to 1 minute to prevent hanging.
     */
    @Transactional(timeout = 60)
    @Scheduled(fixedRateString = "${app.maintenance.rate:PT1M}")
    public void cleanupOldUrls() {
        try {
            long deletedCount = urlRepositoryPort.deleteOldest(urlLimit);
            if (deletedCount > 0) {
                log.info("Scheduled cleanup: {} records removed to maintain limit {}.", deletedCount, urlLimit);
            }
        } catch (Exception e) {
            log.error("Error during scheduled URL cleanup", e);
        }
    }
}
