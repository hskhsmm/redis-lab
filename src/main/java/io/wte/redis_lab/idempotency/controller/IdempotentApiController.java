package io.wte.redis_lab.idempotency.controller;

import io.wte.redis_lab.idempotency.dto.OrderRequest;
import io.wte.redis_lab.idempotency.dto.OrderResponse;
import io.wte.redis_lab.idempotency.service.OrderService;
import io.wte.redis_lab.idempotency.service.IdempotencyService;
import io.wte.redis_lab.common.dto.ApiResponse;
import io.wte.redis_lab.common.dto.ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/idem")
@Tag(name = "Idempotency API", description = "멱등성 키를 활용한 중복 방지 API")
public class IdempotentApiController {

    private final IdempotencyService idempotencyService;
    private final OrderService orderService;

    @Operation(
            summary = "멱등성 키를 사용한 주문 생성",
            description = "Idempotency-Key 헤더를 사용하여 중복 요청을 방지하면서 주문을 생성합니다. " +
                         "동일한 키로 요청 시 기존 결과를 반환합니다."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "주문 생성 성공",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", 
                    description = "중복 요청 - 기존 주문 반환",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 (멱등성 키 누락 등)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(
            @Parameter(description = "멱등성 키", example = "user123-order-20250108-001", required = true)
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Validated @RequestBody OrderRequest req
    ) {
        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.validationError("Idempotency-Key header is required"));
        }

        IdempotencyService.IdempotencyResult result = idempotencyService.checkAndMarkFirst(key);

        if (result.isFirstRequest()) {
            Map<String, Object> created = orderService.createNewOrder(key, req.getItemName(), req.getAmount());
            String orderId = (String) created.get("orderId");

            idempotencyService.markCompleted(key, orderId);

            OrderResponse orderResponse = new OrderResponse(false, key, orderId, req.getItemName(), req.getAmount());
            return ResponseEntity.status(201)
                    .body(ApiResponse.success("주문이 성공적으로 생성되었습니다.", orderResponse));
        } else {
            String orderId = result.getExistingResult();
            OrderResponse orderResponse = new OrderResponse(true, key, orderId, req.getItemName(), req.getAmount());
            return ResponseEntity.ok(ApiResponse.success("기존 주문 정보를 반환합니다.", orderResponse));
        }
    }
}
