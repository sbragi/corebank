package com.corebank.account;

import com.corebank.exception.BalanceNotAvailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> values;
    BalanceService service;
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        service = new BalanceService(redis, mapper);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "keyPrefix", "balance:");
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void shouldReadFromCacheWithoutDatabaseQuery() throws Exception {
        UUID id = UUID.randomUUID();
        when(values.get("balance:" + id)).thenReturn(
                mapper.writeValueAsString(new BalanceCacheEntry(id, new BigDecimal("10"), 2L, Instant.now())));

        var result = service.getBalance(id);

        assertEquals(new BigDecimal("10"), result.balance());
    }

    @Test
    void shouldNotFallbackToDatabaseWhenCacheMisses() {
        UUID id = UUID.randomUUID();
        when(values.get("balance:" + id)).thenReturn(null);

        assertThrows(BalanceNotAvailableException.class, () -> service.getBalance(id));
    }

    @Test
    void shouldNotFallbackToDatabaseWhenCacheEntryIsInvalid() {
        UUID id = UUID.randomUUID();
        when(values.get("balance:" + id)).thenReturn("invalid-json");

        assertThrows(BalanceNotAvailableException.class, () -> service.getBalance(id));
        verify(redis).delete("balance:" + id);
    }

    @Test
    void shouldUpdateCacheWithoutTtl() throws Exception {
        UUID id = UUID.randomUUID();
        BalanceCacheEntry entry = new BalanceCacheEntry(id, new BigDecimal("20"), 3L, Instant.now());

        when(values.get("balance:" + id)).thenReturn(null);
        service.putCache(entry);

        verify(values).set(eq("balance:" + id), anyString());
    }

    @Test
    void shouldNotOverwriteNewerCacheVersion() throws Exception {
        UUID id = UUID.randomUUID();
        when(values.get("balance:" + id)).thenReturn(
                mapper.writeValueAsString(new BalanceCacheEntry(id, new BigDecimal("30"), 5L, Instant.now())));

        service.putCache(new BalanceCacheEntry(id, new BigDecimal("20"), 4L, Instant.now()));

        verify(values, never()).set(eq("balance:" + id), anyString());
    }
}
