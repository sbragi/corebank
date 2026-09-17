package com.corebank.account;

import com.corebank.exception.BalanceNotAvailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BalanceService {
    private static final Logger log = LoggerFactory.getLogger(BalanceService.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    @Value("${corebank.cache.balance.key-prefix:balance:}")
    private String keyPrefix;

    public BalanceResponse getBalance(UUID accountId) {
        String key = keyPrefix + accountId;
        log.info("Getting balance from Redis accountId={}", accountId);
        String json = redis.opsForValue().get(key);

        if (json == null) {
            log.warn("Balance cache MISS accountId={}; database fallback is disabled", accountId);
            throw new BalanceNotAvailableException(accountId);
        }

        try {
            BalanceCacheEntry cached = objectMapper.readValue(json, BalanceCacheEntry.class);
            log.debug("Balance cache HIT accountId={} version={}", accountId, cached.version());
            return new BalanceResponse(cached.accountId(), cached.balance(), cached.version());
        } catch (Exception e) {
            log.error("Invalid balance cache entry accountId={}; database fallback is disabled", accountId, e);
            redis.delete(key);
            throw new BalanceNotAvailableException(accountId);
        }
    }

    public void putCache(BalanceCacheEntry entry) {
        try {
            String key = keyPrefix + entry.accountId();
            log.debug("Updating balance cache accountId={} version={}", entry.accountId(), entry.version());
            String currentJson = redis.opsForValue().get(key);
            if (currentJson != null) {
                BalanceCacheEntry current = objectMapper.readValue(currentJson, BalanceCacheEntry.class);
                if (current.version() != null && entry.version() != null && current.version() > entry.version()) {
                    log.debug("Ignoring stale cache update accountId={} currentVersion={} incomingVersion={}",
                            entry.accountId(), current.version(), entry.version());
                    return;
                }
            }
            // Balance cache is maintained by CDC and intentionally has no TTL.
            // A read must never fall back to PostgreSQL once CDC is enabled.
            redis.opsForValue().set(key, objectMapper.writeValueAsString(entry));
        } catch (Exception e) {
            log.error("Unable to update balance cache accountId={}", entry.accountId(), e);
            throw new IllegalStateException("Unable to update balance cache", e);
        }
    }
}
