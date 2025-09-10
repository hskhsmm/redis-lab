package io.wte.redis_lab.leaderboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.WeekFields;

@Slf4j
@Component
@RequiredArgsConstructor
public class LeaderboardKeyFactory {

    private final StringRedisTemplate redisTemplate;

    private static final String LB_PREFIX = "lb:distance:";
    private static final String DEDUP_PREFIX = "lb:dedup:";

    /**
     * 전체 시즌 리더보드 키를 반환한다.
     * 시즌이 끝날 때까지 계속 누적되는 전역 리더보드이다.
     *
     * @return 전체 시즌 키
     */
    public String getAllTimeKey() {
        return LB_PREFIX + "all";
    }

    /**
     * 주간 리더보드 키를 생성한다.
     * ISO 주차 기준으로 매주 새로운 키가 생성된다.
     *
     * 예시: lb:distance:weekly:2025-37 (2025년 37주차)
     *
     * @param date 기준 날짜
     * @return 주간 리더보드 키
     */
    public String getWeeklyKey(LocalDate date) {
        WeekFields weekFields = WeekFields.ISO; // ISO 8601 표준 (월요일 시작)
        int weekOfYear = date.get(weekFields.weekOfWeekBasedYear());
        int weekBasedYear = date.get(weekFields.weekBasedYear());

        String key = String.format("%sweekly:%d-%02d", LB_PREFIX, weekBasedYear, weekOfYear);

        // 첫 사용 시 자동으로 TTL 설정 (26주 보관)
        ensureTTL(key, Duration.ofDays(26 * 7));

        return key;
    }

    /**
     * 일간 리더보드 키를 생성한다.
     * 매일 새로운 키가 생성되며 자동으로 TTL이 설정된다.
     *
     * 예시: lb:distance:daily:2025-09-09
     *
     * @param date 기준 날짜
     * @return 일간 리더보드 키
     */
    public String getDailyKey(LocalDate date) {
        String key = LB_PREFIX + "daily:" + date.toString();

        // 첫 사용 시 자동으로 TTL 설정 (35일 보관)
        ensureTTL(key, Duration.ofDays(35));

        return key;
    }

    /**
     * 중복 방지용 키를 생성한다.
     * 동일한 이벤트가 여러 번 처리되는 것을 방지하기 위해 사용된다.
     *
     * @param eventId 이벤트 고유 식별자
     * @return 중복 방지 키
     */
    public String getDedupKey(String eventId) {
        return DEDUP_PREFIX + eventId;
    }

    /**
     * 키에 TTL이 설정되지 않은 경우에만 TTL을 설정한다.
     * 이미 TTL이 있거나 키가 없는 경우에는 아무 작업하지 않는다.
     *
     * @param key Redis 키
     * @param ttl 설정할 TTL
     */
    private void ensureTTL(String key, Duration ttl) {
        try {
            // 키가 존재하는지 확인
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                // 현재 TTL 확인 (-1은 TTL 없음을 의미)
                Long currentTtl = redisTemplate.getExpire(key);
                if (currentTtl != null && currentTtl == -1) {
                    redisTemplate.expire(key, ttl);
                    log.debug("TTL 설정 완료 - 키: {}, TTL: {}", key, ttl);
                }
            }
        } catch (Exception e) {
            log.warn("TTL 설정 실패 - 키: {}, 오류: {}", key, e.getMessage());
        }
    }
}
