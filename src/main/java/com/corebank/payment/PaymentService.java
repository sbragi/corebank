package com.corebank.payment;

import com.corebank.account.Account;
import com.corebank.account.AccountRepository;
import com.corebank.exception.AccountNotFoundException;
import com.corebank.exception.IdempotencyConflictException;
import com.corebank.exception.InsufficientBalanceException;
import com.corebank.transaction.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;

    @Transactional
    public PaymentResponse process(PaymentRequest request, String idempotencyKey) {
        log.info("Starting payment accountId={} amount={} idempotencyKey={}",
                request.accountId(), request.amount(), idempotencyKey);
        validateKey(idempotencyKey);
        var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent payment request detected idempotencyKey={}", idempotencyKey);
            return replay(existing.get(), request);
        }

        log.debug("Acquiring account lock accountId={}", request.accountId());
        Account account = accountRepository.findByIdForUpdate(request.accountId())
                .orElseThrow(() -> new AccountNotFoundException(request.accountId()));

        log.debug("Account loaded accountId={} balance={} version={}",
                account.getId(), account.getBalance(), account.getVersion());

        if (account.getBalance().compareTo(request.amount()) < 0) {
            throw new InsufficientBalanceException();
        }

        BigDecimal newBalance = account.getBalance().subtract(request.amount());
        account.setBalance(newBalance);
        accountRepository.save(account);

        BankTransaction tx = BankTransaction.builder()
                .id(UUID.randomUUID())
                .accountId(request.accountId())
                .idempotencyKey(idempotencyKey)
                .type(TransactionType.PIX)
                .amount(request.amount())
                .status(TransactionStatus.COMPLETED)
                .balanceAfter(newBalance)
                .createdAt(Instant.now())
                .build();

        try {
            transactionRepository.saveAndFlush(tx);
        } catch (DataIntegrityViolationException e) {
            BankTransaction concurrent = transactionRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> e);
            log.warn("Concurrent idempotent payment detected idempotencyKey={}", idempotencyKey);
            return replay(concurrent, request);
        }
        log.info("Payment completed accountId={} amount={} newBalance={} version={}",
                account.getId(), request.amount(), account.getBalance(), account.getVersion());
        return toResponse(tx);
    }

    private PaymentResponse replay(BankTransaction tx, PaymentRequest request) {
        log.debug("Replaying payment idempotencyKey={} transactionId={}", tx.getIdempotencyKey(), tx.getId());
        if (!tx.getAccountId().equals(request.accountId()) || tx.getAmount().compareTo(request.amount()) != 0 || tx.getType() != TransactionType.PIX) {
            throw new IdempotencyConflictException();
        }
        return toResponse(tx);
    }

    private PaymentResponse toResponse(BankTransaction tx) {
        return new PaymentResponse(tx.getId(), tx.getAccountId(), tx.getAmount(), tx.getType(), tx.getStatus(), tx.getBalanceAfter());
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 100) throw new IdempotencyConflictException();
    }
}
