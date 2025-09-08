package io.wte.redis_lab.idempotency.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OrderService {
    private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    /** 실제로는 DB 저장 자리 – 데모에선 메모리 보관 */
    public Map<String, Object> createNewOrder(String idempotencyKey, String itemName, int amount) {
        String orderId = UUID.randomUUID().toString();
        Map<String, Object> result = Map.of(
                "orderId", orderId,
                "itemName", itemName,
                "amount", amount
        );
        store.put(orderId, result);
        log.info("[CREATE] orderId={}, item={}, amount={}, ikey={}", orderId, itemName, amount, idempotencyKey);
        return result;
    }
}
