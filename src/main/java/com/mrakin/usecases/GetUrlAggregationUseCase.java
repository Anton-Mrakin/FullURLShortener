package com.mrakin.usecases;

import com.mrakin.infra.db.repository.ClickHouseAccessLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GetUrlAggregationUseCase {

    private final ClickHouseAccessLogRepository clickHouseAccessLogRepository;

    public long getAggregation(String originalUrl, LocalDateTime from, LocalDateTime to) {
        return clickHouseAccessLogRepository.getAggregation(originalUrl, from, to);
    }
}
