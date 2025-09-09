package io.wte.redis_lab.leaderboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 리더보드 항목 DTO
 */
@Schema(description = "리더보드 항목")
public record LeaderboardEntry(
        
        @Schema(description = "순위 (0부터 시작)", example = "0")
        Long rank,
        
        @Schema(description = "사용자 ID", example = "1001")
        String userId,
        
        @Schema(description = "총 거리(km)", example = "127.5")
        Double totalDistance
) {
}