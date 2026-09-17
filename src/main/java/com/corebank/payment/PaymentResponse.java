package com.corebank.payment;

import com.corebank.transaction.TransactionStatus;
import com.corebank.transaction.TransactionType;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentResponse(UUID transactionId, UUID accountId, BigDecimal amount,
                              TransactionType type, TransactionStatus status,
                              BigDecimal balanceAfter) {}
