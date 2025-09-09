package io.wte.redis_lab.idempotency.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {
    
    private final StringRedisTemplate redisTemplate;
    
    private static final String KEY_PREFIX = "idem:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);
    
    public IdempotencyResult checkAndMarkFirst(String idempotencyKey) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        
        Boolean isFirstRequest = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "PENDING", DEFAULT_TTL);
        
        if (Boolean.TRUE.equals(isFirstRequest)) {
            log.debug("첫 번째 요청: {}", idempotencyKey);
            return IdempotencyResult.firstRequest();
        } else {
            String existingValue = redisTemplate.opsForValue().get(redisKey);
            log.debug("중복 요청: {}, 기존 값: {}", idempotencyKey, existingValue);
            return IdempotencyResult.duplicateRequest(existingValue);
        }
    }
    
    public void markCompleted(String idempotencyKey, String result) {
        String redisKey = KEY_PREFIX + idempotencyKey;
        redisTemplate.opsForValue().set(redisKey, result, DEFAULT_TTL);
        log.debug("처리 완료 표시: {} -> {}", idempotencyKey, result);
    }
    
    public static class IdempotencyResult {
        private final boolean isFirstRequest;
        private final String existingResult;
        
        private IdempotencyResult(boolean isFirstRequest, String existingResult) {
            this.isFirstRequest = isFirstRequest;
            this.existingResult = existingResult;
        }
        
        public static IdempotencyResult firstRequest() {
            return new IdempotencyResult(true, null);
        }
        
        public static IdempotencyResult duplicateRequest(String existingResult) {
            return new IdempotencyResult(false, existingResult);
        }
        
        public boolean isFirstRequest() {
            return isFirstRequest;
        }
        
        public String getExistingResult() {
            return existingResult;
        }
    }
}