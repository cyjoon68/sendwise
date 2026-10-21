package com.sendwise.api.application;

import com.sendwise.api.domain.ApiError;
import com.sendwise.api.infra.persistence.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final MessageRepository messages;

    public StatsService(MessageRepository messages) {
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats(LocalDate from, LocalDate to, String groupBy) {
        if (from == null || to == null) {
            throw new ApiError(400, "RANGE_REQUIRED", "시작일과 종료일이 필요해요");
        }
        if (to.isBefore(from)) {
            throw new ApiError(400, "INVALID_RANGE", "종료일이 시작일보다 빨라요");
        }
        if (!"carrier".equals(groupBy) && !"code".equals(groupBy)) {
            throw new ApiError(400, "INVALID_GROUP", "groupBy는 carrier 또는 code예요");
        }
        Instant start = from.atStartOfDay(SEOUL).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(SEOUL).toInstant();
        List<Object[]> rows = "carrier".equals(groupBy)
                ? messages.statsByCarrier(start, end)
                : messages.statsByCode(start, end);
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Object[] row : rows) {
            long total = ((Number) row[1]).longValue();
            long failed = row[2] == null ? 0 : ((Number) row[2]).longValue();
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("key", row[0]);
            group.put("total", total);
            group.put("failed", failed);
            group.put("failRate", total == 0 ? 0 : failed / (double) total);
            groups.add(group);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", from.toString());
        body.put("to", to.toString());
        body.put("groupBy", groupBy);
        body.put("groups", groups);
        return body;
    }
}
