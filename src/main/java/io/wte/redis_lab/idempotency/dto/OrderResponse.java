package io.wte.redis_lab.idempotency.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "주문 응답")
public class OrderResponse {

    @Schema(description = "중복 요청 여부", example = "false")
    private boolean duplicated;
    
    @Schema(description = "멱등성 키", example = "user123-order-20250108-001")
    private String idempotencyKey;
    
    @Schema(description = "주문 ID", example = "order_1736123456789")
    private String orderId;
    
    @Schema(description = "상품명", example = "MacBook Pro")
    private String itemName;
    
    @Schema(description = "수량", example = "2")
    private int amount;
}