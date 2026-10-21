package com.sendwise.api.domain.rules;

import com.sendwise.api.domain.ApiError;

import java.time.Instant;
import java.time.ZonedDateTime;

public final class AdPolicy {
    private AdPolicy() {
    }

    public record Decision(
            boolean inBlockedHours,
            Boolean hasPrefix,
            Boolean hasOptOut,
            Boolean hasCompanyName,
            boolean allowed
    ) {
    }

    public static Decision decide(String purpose, String body, String companyName, Instant when, MessageRules rules) {
        boolean blocked = inBlockedHours(when, rules);
        if (!"AD".equals(purpose)) {
            return new Decision(blocked, null, null, null, true);
        }
        String text = body == null ? "" : body;
        boolean hasPrefix = text.startsWith(rules.requiredPrefix());
        boolean hasOptOut = rules.optOutText() != null && text.contains(rules.optOutText());
        boolean hasCompanyName = !rules.companyNameRequired()
                || (companyName != null && !companyName.isEmpty() && text.contains(companyName));
        return new Decision(
                blocked,
                hasPrefix,
                hasOptOut,
                hasCompanyName,
                !blocked && hasPrefix && hasOptOut && hasCompanyName
        );
    }

    public static void check(String purpose, String body, String companyName, Instant when, MessageRules rules) {
        Decision decision = decide(purpose, body, companyName, when, rules);
        if (decision.allowed()) {
            return;
        }
        if (decision.inBlockedHours()) {
            throw new ApiError(400, "AD_BLOCKED_HOURS", "광고 문자는 오전 8시부터 오후 9시 사이에만 보낼 수 있어요");
        }
        if (Boolean.FALSE.equals(decision.hasPrefix())) {
            throw new ApiError(400, "AD_PREFIX_REQUIRED", "광고 문자는 " + rules.requiredPrefix() + "로 시작해야 해요");
        }
        if (Boolean.FALSE.equals(decision.hasOptOut())) {
            throw new ApiError(400, "AD_OPTOUT_REQUIRED", "광고 문자에 무료수신거부 문구가 있어야 해요");
        }
        throw new ApiError(400, "AD_COMPANY_REQUIRED", "광고 문자에 업체명이 있어야 해요");
    }

    public static boolean inBlockedHours(Instant when, MessageRules rules) {
        ZonedDateTime zoned = when.atZone(rules.zone());
        int minutes = zoned.getHour() * 60 + zoned.getMinute();
        int from = rules.blockedFrom().getHour() * 60 + rules.blockedFrom().getMinute();
        int to = rules.blockedTo().getHour() * 60 + rules.blockedTo().getMinute();
        boolean afterFrom = rules.fromInclusive() ? minutes >= from : minutes > from;
        boolean beforeTo = rules.toInclusive() ? minutes <= to : minutes < to;
        if (from == to) {
            return false;
        }
        if (from > to) {
            return afterFrom || beforeTo;
        }
        return afterFrom && beforeTo;
    }
}
