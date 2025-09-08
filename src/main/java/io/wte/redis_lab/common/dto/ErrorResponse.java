package io.wte.redis_lab.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "에러 응답")
public class ErrorResponse {
    
    @Schema(description = "성공 여부", example = "false")
    private boolean success = false;
    
    @Schema(description = "에러 코드", example = "VALIDATION_ERROR")
    private String errorCode;
    
    @Schema(description = "에러 메시지", example = "입력값이 올바르지 않습니다.")
    private String message;
    
    @Schema(description = "타임스탬프", example = "2025-01-15T10:30:00")
    private String timestamp;

    public ErrorResponse(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
        this.timestamp = java.time.LocalDateTime.now().toString();
    }

    public static ErrorResponse of(String errorCode, String message) {
        return new ErrorResponse(errorCode, message);
    }

    public static ErrorResponse validationError(String message) {
        return new ErrorResponse("VALIDATION_ERROR", message);
    }

    public static ErrorResponse serverError(String message) {
        return new ErrorResponse("INTERNAL_SERVER_ERROR", message);
    }
}