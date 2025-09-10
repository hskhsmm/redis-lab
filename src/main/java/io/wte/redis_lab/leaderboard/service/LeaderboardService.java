package io.wte.redis_lab.leaderboard.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final StringRedisTemplate redisTemplate;

    /**
     * 멱등성 보장을 위한 Lua 스크립트
     * SETNX로 중복 처리를 방지하고, 첫 번째 요청만 점수를 가산한다.
     *
     * KEYS[1]: 리더보드 키 (예: lb:distance:weekly:2025-37)
     * KEYS[2]: 중복 방지 키 (예: lb:dedup:event123)
     * ARGV[1]: 사용자 ID
     * ARGV[2]: 가산할 점수 (거리 + 타임스탬프)
     * ARGV[3]: 중복 방지 키의 TTL (밀리초)
     */
    private final DefaultRedisScript<Double> incrementOnceScript = new DefaultRedisScript<>(
            """
            if redis.call('SETNX', KEYS[2], '1') == 1 then
              redis.call('PEXPIRE', KEYS[2], ARGV[3])
              return redis.call('ZINCRBY', KEYS[1], ARGV[2], ARGV[1])
            else
              return redis.call('ZSCORE', KEYS[1], ARGV[1])
            end
            """, Double.class
    );

    /**
     * 멱등성을 보장하며 거리 점수를 가산한다.
     * 동일한 eventId로는 한 번만 처리되며, 동점 시 최근 기록이 우선된다.
     *
     * @param leaderboardKey 리더보드 키 (스코프별로 다름)
     * @param dedupKey 중복 방지용 키
     * @param userId 사용자 ID
     * @param deltaKm 가산할 거리(km)
     * @param dedupTtlMs 중복 방지 키의 TTL(밀리초)
     * @return 가산 후 총 점수
     */
    public double addDistanceOnce(String leaderboardKey, String dedupKey, String userId,
                                  double deltaKm, long dedupTtlMs) {
        // 동점 타이브레이커: 최근 기록 우선을 위해 현재 시각을 아주 작은 값으로 더함
        double scoreWithTimestamp = deltaKm + (System.currentTimeMillis() / 1e15);

        Double result = redisTemplate.execute(incrementOnceScript,
                List.of(leaderboardKey, dedupKey),
                userId, String.valueOf(scoreWithTimestamp), String.valueOf(dedupTtlMs));

        log.debug("거리 가산 - 사용자: {}, 점수: {}, 총합: {}", userId, deltaKm, result);
        return result != null ? result : 0.0;
    }

    /**
     * 상위 N명의 리더보드를 조회한다.
     * 점수가 높은 순으로 정렬되며, 동점 시 최근 기록이 우선된다.
     *
     * @param key 리더보드 키
     * @param n 조회할 인원 수
     * @return 순위별 사용자와 점수 리스트
     */
    public List<ScoredValue> getTopN(String key, int n) {
        // ZREVRANGE: 점수 높은 순으로 0~n-1 범위 조회
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, n - 1);

        if (tuples == null) return List.of();

        return tuples.stream()
                .map(tuple -> new ScoredValue(tuple.getValue(), tuple.getScore()))
                .toList();
    }

    /**
     * 특정 사용자의 순위와 점수를 조회한다.
     *
     * @param key 리더보드 키
     * @param userId 사용자 ID
     * @return 순위와 점수 (순위는 0부터 시작, 없으면 -1)
     */
    public RankScore getRankScore(String key, String userId) {
        // ZREVRANK: 점수 높은 순으로 정렬된 순위 (0부터 시작)
        Long rank = redisTemplate.opsForZSet().reverseRank(key, userId);
        // ZSCORE: 해당 사용자의 점수
        Double score = redisTemplate.opsForZSet().score(key, userId);

        return new RankScore(
                rank != null ? rank : -1,
                score != null ? score : 0.0
        );
    }

    /**
     * 특정 사용자 주변의 리더보드를 조회한다.
     * 내 앞뒤로 k명씩 총 2k+1명의 리더보드를 반환한다.
     *
     * @param key 리더보드 키
     * @param userId 기준 사용자 ID
     * @param k 앞뒤로 조회할 인원 수
     * @return 주변 사용자들의 점수 리스트
     */
    public List<ScoredValue> getAroundUser(String key, String userId, int k) {
        Long rank = redisTemplate.opsForZSet().reverseRank(key, userId);
        if (rank == null) {
            log.debug("사용자 {}가 리더보드 {}에 없음", userId, key);
            return List.of();
        }

        // 내 순위 기준으로 앞뒤 k명씩 범위 계산
        long start = Math.max(rank - k, 0);
        long end = rank + k;

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);

        if (tuples == null) return List.of();

        return tuples.stream()
                .map(tuple -> new ScoredValue(tuple.getValue(), tuple.getScore()))
                .toList();
    }

    /**
     * 리더보드의 총 참가자 수를 조회한다.
     *
     * @param key 리더보드 키
     * @return 총 참가자 수
     */
    public long getTotalMembers(String key) {
        Long count = redisTemplate.opsForZSet().count(key, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        return count != null ? count : 0L;
    }

    /**
     * 사용자 ID와 점수를 담는 레코드
     */
    public record ScoredValue(String userId, Double score) {}

    /**
     * 순위와 점수를 담는 레코드
     */
    public record RankScore(long rank, double score) {
    }
}
