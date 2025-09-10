package io.wte.redis_lab.leaderboard.controller;

import io.wte.redis_lab.leaderboard.dto.AddProgressRequest;
import io.wte.redis_lab.leaderboard.dto.LeaderboardEntry;
import io.wte.redis_lab.leaderboard.dto.UserRankResponse;
import io.wte.redis_lab.leaderboard.service.LeaderboardService;
import io.wte.redis_lab.leaderboard.service.LeaderboardKeyFactory;
import io.wte.redis_lab.common.dto.ApiResponse;
import io.wte.redis_lab.common.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RestController
@RequestMapping("/v1/leaderboard")
@RequiredArgsConstructor
@Tag(name = "Leaderboard API", description = "러닝 리더보드 관리 API")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final LeaderboardKeyFactory keyFactory;
    private final StringRedisTemplate redisTemplate;

    // 중복 방지 키의 TTL (7일)
    private static final long DEDUP_TTL_MS = 7L * 24 * 60 * 60 * 1000;

    @Operation(
            summary = "러닝 진행상황 추가",
            description = "사용자의 러닝 기록을 리더보드에 추가합니다. " +
                    "동일한 eventId로는 중복 처리되지 않으며, 스코프별로 점수가 누적됩니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "진행상황 추가 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (유효성 검사 실패)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/progress")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addProgress(
            @Valid @RequestBody AddProgressRequest request) {

        log.info("러닝 진행상황 추가 - 사용자: {}, 거리: {}km, 이벤트: {}",
                request.userId(), request.deltaKm(), request.eventId());

        Map<String, Object> results = new HashMap<>();
        LocalDate today = LocalDate.now();
        String dedupKey = keyFactory.getDedupKey(request.eventId());
        String userId = String.valueOf(request.userId());

        // 각 스코프별로 점수 가산 처리
        for (String scope : request.scopes()) {
            String leaderboardKey = getLeaderboardKey(scope, today);

            // Lua 스크립트로 멱등성 보장하며 점수 가산
            double totalScore = leaderboardService.addDistanceOnce(
                    leaderboardKey, dedupKey, userId, request.deltaKm(), DEDUP_TTL_MS);

            // 가산 후 현재 순위도 함께 조회
            LeaderboardService.RankScore rankScore =
                    leaderboardService.getRankScore(leaderboardKey, userId);

            results.put(scope, Map.of(
                    "totalDistance", totalScore,
                    "rank", rankScore.rank(),
                    "added", request.deltaKm()
            ));
        }

        return ResponseEntity.ok(
                ApiResponse.success("러닝 진행상황이 성공적으로 추가되었습니다.", results));
    }

    @Operation(
            summary = "리더보드 상위 조회",
            description = "지정된 범위의 리더보드에서 상위 N명을 조회합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "리더보드 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 스코프"
            )
    })
    @GetMapping("/top")
    public ResponseEntity<ApiResponse<List<LeaderboardEntry>>> getTopLeaderboard(
            @Parameter(description = "리더보드 범위", example = "weekly")
            @RequestParam String scope,

            @Parameter(description = "조회할 상위 인원 수", example = "10")
            @RequestParam(defaultValue = "10") int limit) {

        if (limit <= 0 || limit > 100) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<List<LeaderboardEntry>>error("조회 인원은 1~100 사이여야 합니다."));
        }

        String leaderboardKey = getLeaderboardKey(scope, LocalDate.now());
        List<LeaderboardService.ScoredValue> scoredValues =
                leaderboardService.getTopN(leaderboardKey, limit);

        // 순위를 포함하여 응답 생성 (0부터 시작)
        AtomicLong rankCounter = new AtomicLong(0);
        List<LeaderboardEntry> entries = scoredValues.stream()
                .map(sv -> new LeaderboardEntry(
                        rankCounter.getAndIncrement(),
                        sv.userId(),
                        sv.score()))
                .toList();

        return ResponseEntity.ok(
                ApiResponse.success("리더보드 조회 성공", entries));
    }

    @Operation(
            summary = "사용자 순위 조회",
            description = "특정 사용자의 현재 순위와 점수를 조회합니다."
    )
    @GetMapping("/rank/{userId}")
    public ResponseEntity<ApiResponse<UserRankResponse>> getUserRank(
            @Parameter(description = "사용자 ID", example = "1001")
            @PathVariable String userId,

            @Parameter(description = "리더보드 범위", example = "weekly")
            @RequestParam String scope) {

        String leaderboardKey = getLeaderboardKey(scope, LocalDate.now());
        LeaderboardService.RankScore rankScore =
                leaderboardService.getRankScore(leaderboardKey, userId);

        UserRankResponse response = new UserRankResponse(
                userId, rankScore.rank(), rankScore.score(), scope);

        return ResponseEntity.ok(
                ApiResponse.success("사용자 순위 조회 성공", response));
    }

    @Operation(
            summary = "주변 사용자 리더보드 조회",
            description = "특정 사용자 주변의 리더보드를 조회합니다. 해당 사용자를 중심으로 앞뒤 k명씩 조회합니다."
    )
    @GetMapping("/around/{userId}")
    public ResponseEntity<ApiResponse<List<LeaderboardEntry>>> getAroundUser(
            @Parameter(description = "기준 사용자 ID", example = "1001")
            @PathVariable String userId,

            @Parameter(description = "리더보드 범위", example = "weekly")
            @RequestParam String scope,

            @Parameter(description = "앞뒤로 조회할 인원 수", example = "3")
            @RequestParam(defaultValue = "3") int around) {

        if (around < 0 || around > 20) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<List<LeaderboardEntry>>error("주변 조회 범위는 0~20 사이여야 합니다."));
        }

        String leaderboardKey = getLeaderboardKey(scope, LocalDate.now());

        // 먼저 사용자의 현재 순위를 확인
        LeaderboardService.RankScore userRank =
                leaderboardService.getRankScore(leaderboardKey, userId);

        if (userRank.rank() == -1) {
            return ResponseEntity.ok(
                    ApiResponse.success("해당 사용자는 리더보드에 없습니다.", List.of()));
        }

        // 주변 사용자들 조회
        List<LeaderboardService.ScoredValue> scoredValues =
                leaderboardService.getAroundUser(leaderboardKey, userId, around);

        // 실제 순위 계산을 위한 시작 순위
        long startRank = Math.max(userRank.rank() - around, 0);
        AtomicLong rankCounter = new AtomicLong(startRank);

        List<LeaderboardEntry> entries = scoredValues.stream()
                .map(sv -> new LeaderboardEntry(
                        rankCounter.getAndIncrement(),
                        sv.userId(),
                        sv.score()))
                .toList();

        return ResponseEntity.ok(
                ApiResponse.success("주변 사용자 조회 성공", entries));
    }

    @Operation(
            summary = "테스트 데이터 생성",
            description = "리더보드 테스트를 위한 랜덤 사용자 데이터를 생성합니다."
    )
    @PostMapping("/test-data")
    public ResponseEntity<ApiResponse<Map<String, Object>>> generateTestData(
            @Parameter(description = "생성할 사용자 수", example = "20")
            @RequestParam(defaultValue = "20") int userCount,
            
            @Parameter(description = "대상 스코프", example = "weekly")
            @RequestParam(defaultValue = "weekly") String scope) {

        if (userCount <= 0 || userCount > 100) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("사용자 수는 1~100 사이여야 합니다."));
        }

        LocalDate today = LocalDate.now();
        String leaderboardKey = getLeaderboardKey(scope, today);
        
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;

        // 랜덤 사용자 데이터 생성
        for (int i = 1; i <= userCount; i++) {
            try {
                String userId = "user" + String.format("%03d", i);
                // 0.1km ~ 50km 랜덤 거리
                double distance = Math.round((Math.random() * 49.9 + 0.1) * 10.0) / 10.0;
                
                // 고유한 이벤트 ID로 중복 방지 키 생성
                String eventId = "test-event-" + System.currentTimeMillis() + "-" + i;
                String dedupKey = keyFactory.getDedupKey(eventId);
                
                leaderboardService.addDistanceOnce(
                    leaderboardKey, dedupKey, userId, distance, DEDUP_TTL_MS);
                
                successCount++;
                
                // 약간의 딜레이로 타임스탬프 차이 생성
                Thread.sleep(1);
                
            } catch (Exception e) {
                log.warn("테스트 데이터 생성 실패 - 사용자 {}: {}", i, e.getMessage());
            }
        }
        
        result.put("requestedUsers", userCount);
        result.put("successCount", successCount);
        result.put("scope", scope);
        result.put("leaderboardKey", leaderboardKey);
        result.put("totalMembers", leaderboardService.getTotalMembers(leaderboardKey));

        return ResponseEntity.ok(
                ApiResponse.success("테스트 데이터 생성 완료", result));
    }

    @Operation(
            summary = "리더보드 데이터 초기화",
            description = "지정된 스코프의 리더보드 데이터를 모두 삭제합니다."
    )
    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<Map<String, Object>>> clearLeaderboard(
            @Parameter(description = "초기화할 스코프", example = "weekly")
            @RequestParam String scope) {
        
        LocalDate today = LocalDate.now();
        String leaderboardKey = getLeaderboardKey(scope, today);
        
        Long deletedCount = redisTemplate.delete(leaderboardKey) ? 1L : 0L;
        
        Map<String, Object> result = new HashMap<>();
        result.put("scope", scope);
        result.put("leaderboardKey", leaderboardKey);
        result.put("cleared", deletedCount > 0);
        
        return ResponseEntity.ok(
                ApiResponse.success("리더보드 초기화 완료", result));
    }

    /**
     * 스코프와 날짜를 기반으로 적절한 리더보드 키를 반환한다.
     *
     * @param scope 리더보드 범위 (all, weekly, daily)
     * @param date 기준 날짜
     * @return Redis 리더보드 키
     * @throws IllegalArgumentException 유효하지 않은 스코프인 경우
     */
    private String getLeaderboardKey(String scope, LocalDate date) {
        return switch (scope.toLowerCase()) {
            case "all" -> keyFactory.getAllTimeKey();
            case "weekly" -> keyFactory.getWeeklyKey(date);
            case "daily" -> keyFactory.getDailyKey(date);
            default -> throw new IllegalArgumentException("유효하지 않은 스코프: " + scope);
        };
    }
}