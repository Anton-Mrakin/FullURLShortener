package com.mrakin.infra.db;

import com.mrakin.domain.model.Url;
import com.mrakin.domain.ports.UrlRepositoryPort;
import com.mrakin.infra.db.entity.UrlEntity;
import com.mrakin.infra.db.mapper.UrlDbMapper;
import com.mrakin.infra.db.repository.JpaUrlRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
public class UrlRepositoryAdapter implements UrlRepositoryPort {

    private final JpaUrlRepository jpaUrlRepository;
    private final UrlDbMapper urlDbMapper;

    public UrlRepositoryAdapter(JpaUrlRepository jpaUrlRepository,
                                UrlDbMapper urlDbMapper,
                                MeterRegistry meterRegistry) {
        this.jpaUrlRepository = jpaUrlRepository;
        this.urlDbMapper = urlDbMapper;
        meterRegistry.gauge("url.count", jpaUrlRepository, JpaUrlRepository::count);
    }

    @Override
    @Transactional
    public Url save(Url url) {
        UrlEntity entity = urlDbMapper.toEntity(url);
        UrlEntity saved = jpaUrlRepository.save(entity);
        return urlDbMapper.toDomain(saved);
    }

    @Override
    public Optional<Url> findByShortCode(String shortCode) {
        return jpaUrlRepository.findByShortCode(shortCode)
                .map(urlDbMapper::toDomain);
    }

    @Override
    public Optional<Url> findByOriginalUrl(String originalUrl) {
        return jpaUrlRepository.findByOriginalUrl(originalUrl)
                .map(urlDbMapper::toDomain);
    }

    @Override
    @Transactional(timeout = 60)
    @Retry(name = "dbRetry")
    @SchedulerLock(name = "cleanupShortUrls", lockAtMostFor = "1m", lockAtLeastFor = "10s")
    public long deleteOldest(long urlLimit) {
        return jpaUrlRepository.deleteOldestRecords(urlLimit);
    }

    @Override
    @Transactional
    @Retry(name = "dbRetry")
    public void updateLastAccessed(String shortCode) {
        jpaUrlRepository.findByShortCode(shortCode)
                .ifPresent(entity -> {
                    entity.setLastAccessed(LocalDateTime.now());
                    jpaUrlRepository.save(entity);
                });
    }
}
