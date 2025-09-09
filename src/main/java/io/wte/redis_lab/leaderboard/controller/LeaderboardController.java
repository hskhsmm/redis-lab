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
