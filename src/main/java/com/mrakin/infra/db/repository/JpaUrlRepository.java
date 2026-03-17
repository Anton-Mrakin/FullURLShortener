package com.mrakin.infra.db.repository;

import com.mrakin.infra.db.entity.UrlEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaUrlRepository extends JpaRepository<UrlEntity, Long> {
    Optional<UrlEntity> findByShortCode(String shortCode);
    Optional<UrlEntity> findByOriginalUrl(String originalUrl);
    Optional<UrlEntity> findFirstByOrderByLastAccessedAsc();
}
