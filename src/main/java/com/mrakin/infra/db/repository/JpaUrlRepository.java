package com.mrakin.infra.db.repository;

import com.mrakin.infra.db.entity.UrlEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaUrlRepository extends JpaRepository<UrlEntity, Long> {
    Optional<UrlEntity> findByShortCode(String shortCode);

    Optional<UrlEntity> findByOriginalUrl(String originalUrl);

    @Modifying
    @Query(value = "select pg_advisory_xact_lock(:key)", nativeQuery = true)
    boolean lock(@Param("key") long key);

    @Modifying
    @Query(value = "DELETE FROM urls WHERE id IN (SELECT id FROM urls ORDER BY last_accessed DESC OFFSET :urlLimit ROWS)", nativeQuery = true)
    int deleteOldestRecords(@Param("urlLimit") long urlLimit);
}
