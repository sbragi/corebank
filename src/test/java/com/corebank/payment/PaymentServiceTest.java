package com.corebank.payment;

import com.corebank.account.Account;
import com.corebank.account.AccountRepository;
import com.corebank.exception.InsufficientBalanceException;
import com.corebank.exception.IdempotencyConflictException;
import com.corebank.transaction.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock AccountRepository accountRepository;
    @Mock BankTransactionRepository transactionRepository;
    @InjectMocks PaymentService service;

    @Test
    void shouldProcessPayment() {
        UUID id = UUID.randomUUID();
        Account account = account(100);
        account.setId(id);
        when(transactionRepository.findByIdempotencyKey("k")).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(id)).thenReturn(Optional.of(account));
        when(transactionRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        PaymentResponse response = service.process(new PaymentRequest(id, new BigDecimal("30")), "k");

        assertEquals(new BigDecimal("70"), response.balanceAfter());
        verify(accountRepository).save(account);
    }

    @Test
    void shouldReplaySameIdempotencyKey() {
        UUID id = UUID.randomUUID();
        BankTransaction tx = BankTransaction.builder().id(UUID.randomUUID()).accountId(id)
                .idempotencyKey("k").type(TransactionType.PIX).amount(new BigDecimal("10"))
                .status(TransactionStatus.COMPLETED).balanceAfter(new BigDecimal("90")).build();
        when(transactionRepository.findByIdempotencyKey("k")).thenReturn(Optional.of(tx));

        PaymentResponse response = service.process(new PaymentRequest(id, new BigDecimal("10")), "k");
        assertEquals(tx.getId(), response.transactionId());
        verifyNoInteractions(accountRepository);
    }

    @Test
    void shouldRejectInsufficientBalance() {
        UUID id = UUID.randomUUID();
        when(transactionRepository.findByIdempotencyKey("k")).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(id)).thenReturn(Optional.of(account(5)));
        assertThrows(InsufficientBalanceException.class,
                () -> service.process(new PaymentRequest(id, new BigDecimal("10")), "k"));
    }

    @Test
    void shouldRejectConflictingIdempotency() {
        UUID id = UUID.randomUUID();
        BankTransaction tx = BankTransaction.builder().id(UUID.randomUUID()).accountId(id)
                .type(TransactionType.PIX).amount(new BigDecimal("10")).status(TransactionStatus.COMPLETED).build();
        when(transactionRepository.findByIdempotencyKey("k")).thenReturn(Optional.of(tx));
        assertThrows(IdempotencyConflictException.class,
                () -> service.process(new PaymentRequest(id, new BigDecimal("20")), "k"));
    }

    private Account account(int balance) {
        Account a = new Account();
        a.setBalance(new BigDecimal(balance));
        a.setVersion(0L);
        return a;
    }
}
