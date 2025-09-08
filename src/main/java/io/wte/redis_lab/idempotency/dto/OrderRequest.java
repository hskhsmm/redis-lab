package io.wte.redis_lab.idempotency.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import io.swagger.v3.oas.annotations.media.Schema;

@Getter
@Setter
@Schema(description = "주문 요청")
public class OrderRequest {

    @NotBlank
    @Schema(description = "상품명", example = "MacBook Pro", required = true)
    private String itemName;

    @Min(1)
    @Schema(description = "수량", example = "2", minimum = "1", required = true)
    private int amount;
}
