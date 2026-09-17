package com.corebank.card;

import com.corebank.account.Account;
import com.corebank.account.AccountRepository;
import com.corebank.exception.AccountNotFoundException;
import com.corebank.exception.IdempotencyConflictException;
import com.corebank.exception.InsufficientBalanceException;
import com.corebank.transaction.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CardAuthorizationService {
    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;

    @Transactional
    public CardAuthorizationResponse authorize(CardAuthorizationRequest request, String key) {
        if (key == null || key.isBlank() || key.length() > 100) throw new IdempotencyConflictException();
        var existing = transactionRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) return replay(existing.get(), request);

        Account account = accountRepository.findByIdForUpdate(request.accountId())
                .orElseThrow(() -> new AccountNotFoundException(request.accountId()));

        if (account.getBalance().compareTo(request.amount()) < 0) throw new InsufficientBalanceException();

        BigDecimal newBalance = account.getBalance().subtract(request.amount());
        account.setBalance(newBalance);
        accountRepository.save(account);

        BankTransaction tx = BankTransaction.builder()
                .id(UUID.randomUUID()).accountId(request.accountId()).idempotencyKey(key)
                .type(TransactionType.CARD_AUTHORIZATION).amount(request.amount())
                .status(TransactionStatus.COMPLETED).balanceAfter(newBalance).createdAt(Instant.now()).build();
        try {
            transactionRepository.saveAndFlush(tx);
        } catch (DataIntegrityViolationException e) {
            BankTransaction concurrent = transactionRepository.findByIdempotencyKey(key).orElseThrow(() -> e);
            return replay(concurrent, request);
        }
        return toResponse(tx);
    }

    private CardAuthorizationResponse replay(BankTransaction tx, CardAuthorizationRequest request) {
        if (!tx.getAccountId().equals(request.accountId()) || tx.getAmount().compareTo(request.amount()) != 0 || tx.getType() != TransactionType.CARD_AUTHORIZATION) {
            throw new IdempotencyConflictException();
        }
        return toResponse(tx);
    }

    private CardAuthorizationResponse toResponse(BankTransaction tx) {
        return new CardAuthorizationResponse(tx.getId(), tx.getAccountId(), tx.getAmount(), tx.getStatus(), tx.getBalanceAfter());
    }
}
