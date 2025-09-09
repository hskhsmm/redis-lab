package io.wte.redis_lab.leaderboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 사용자 순위 조회 응답 DTO
 */
@Schema(description = "사용자 순위 조회 응답")
public record UserRankResponse(
        
        @Schema(description = "사용자 ID", example = "1001")
        String userId,
        
        @Schema(description = "현재 순위 (0부터 시작, -1은 순위 없음)", example = "5")
        Long rank,
        
        @Schema(description = "총 거리(km)", example = "87.3")
        Double totalDistance,
        
        @Schema(description = "리더보드 범위", example = "weekly")
        String scope
) {
}