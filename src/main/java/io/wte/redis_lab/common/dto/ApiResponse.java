package io.wte.redis_lab.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "공통 API 응답")
public class ApiResponse<T> {
    
    @Schema(description = "성공 여부", example = "true")
    private boolean success;
    
    @Schema(description = "응답 메시지", example = "처리가 완료되었습니다.")
    private String message;
    
    @Schema(description = "응답 데이터")
    private T data;
    
    @Schema(description = "타임스탬프", example = "2025-01-15T10:30:00")
    private String timestamp;

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, java.time.LocalDateTime.now().toString());
    }

    public static <T> ApiResponse<T> success(T data) {
        return success("요청이 성공적으로 처리되었습니다.", data);
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(true, message, null, java.time.LocalDateTime.now().toString());
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, java.time.LocalDateTime.now().toString());
    }
}