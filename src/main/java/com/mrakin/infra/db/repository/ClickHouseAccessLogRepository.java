package com.mrakin.infra.db.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ClickHouseAccessLogRepository {

    @Qualifier("clickhouseJdbcTemplate")
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public void save(String shortCode, String fullUrl, LocalDateTime accessedAt) {
        String sql = "INSERT INTO url_access_log (short_code, full_url, accessed_at) VALUES (:shortCode, :fullUrl, :accessedAt)";
        jdbcTemplate.update(sql, Map.of(
                "shortCode", shortCode,
                "fullUrl", fullUrl,
                "accessedAt", accessedAt
        ));
    }

    public long getAggregation(String fullUrl, LocalDateTime from, LocalDateTime to) {
        String sql = "SELECT count() FROM url_access_log WHERE full_url = :fullUrl AND accessed_at BETWEEN :from AND :to";
        Long count = jdbcTemplate.queryForObject(sql, Map.of(
                "fullUrl", fullUrl,
                "from", from,
                "to", to
        ), Long.class);
        return count != null ? count : 0L;
    }
}
