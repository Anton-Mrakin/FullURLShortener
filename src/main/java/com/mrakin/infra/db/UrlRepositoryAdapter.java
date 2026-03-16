package com.mrakin.infra.db;

import com.mrakin.domain.model.Url;
import com.mrakin.domain.ports.UrlRepositoryPort;
import com.mrakin.infra.db.entity.UrlEntity;
import com.mrakin.infra.db.mapper.UrlDbMapper;
import com.mrakin.infra.db.repository.JpaUrlRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UrlRepositoryAdapter implements UrlRepositoryPort {

    private final JpaUrlRepository jpaUrlRepository;
    private final UrlDbMapper urlDbMapper;

    @Override
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
    public long count() {
        return jpaUrlRepository.count();
    }
}
