package com.mrakin.infra.config;

import com.clickhouse.jdbc.ClickHouseDataSource;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

@Configuration
@Slf4j
public class ClickHouseConfig {

    @Value("${clickhouse.url:jdbc:clickhouse:http://localhost:8123/default}")
    private String url;

    @Value("${clickhouse.user:default}")
    private String user;

    @Value("${clickhouse.password:}")
    private String password;

    private DataSource clickhouseDataSource() throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", user);
        properties.setProperty("password", password);
        return new ClickHouseDataSource(url, properties);
    }

    @Bean(name = "clickhouseJdbcTemplate")
    public NamedParameterJdbcTemplate clickhouseJdbcTemplate() throws SQLException {
        return new NamedParameterJdbcTemplate(clickhouseDataSource());
    }

    @PostConstruct
    public void init() {
        log.info("ClickHouse initializing with URL: {}", url);
        try (Connection conn = clickhouseDataSource().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS url_access_log (" +
                    "short_code String, " +
                    "full_url String, " +
                    "accessed_at DateTime64(3)" +
                    ") ENGINE = MergeTree() ORDER BY (full_url, accessed_at)");
            log.info("ClickHouse table url_access_log ensured");
        } catch (Exception e) {
            log.warn("Could not create ClickHouse table: {}. It might already exist or DB is not reachable yet.", e.getMessage());
        }
    }
}
