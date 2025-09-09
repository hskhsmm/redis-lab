package io.wte.redis_lab.leaderboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 러닝 진행상황 추가 요청 DTO
 */
@Schema(description = "러닝 진행상황 추가 요청")
public record AddProgressRequest(
        
        @Schema(description = "사용자 ID", example = "1001")
        @Positive(message = "사용자 ID는 양수여야 합니다")
        Long userId,
        
        @Schema(description = "추가할 거리(km)", example = "5.2")
        @Positive(message = "거리는 양수여야 합니다")
        Double deltaKm,
        
        @Schema(description = "이벤트 고유 식별자 (중복 방지용)", example = "run-20250909-user1001-001")
        @NotBlank(message = "이벤트 ID는 필수입니다")
        String eventId,
        
        @Schema(description = "적용할 리더보드 범위 목록", 
                example = "[\"all\", \"weekly\", \"daily\"]",
                allowableValues = {"all", "weekly", "daily"})
        @NotEmpty(message = "최소 하나의 스코프는 필요합니다")
        List<String> scopes
) {
}