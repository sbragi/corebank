package com.corebank.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BalanceCacheEntry(UUID accountId, BigDecimal balance, Long version, Instant updatedAt) {}
