package com.corebank.card;

import com.corebank.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record CardAuthorizationResponse(UUID transactionId, UUID accountId, BigDecimal amount,
                                        TransactionStatus status, BigDecimal balanceAfter) {}
