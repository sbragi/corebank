package com.corebank.card;

import com.corebank.account.Account;
import com.corebank.account.AccountRepository;
import com.corebank.exception.InsufficientBalanceException;
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
class CardAuthorizationServiceTest {
    @Mock AccountRepository accountRepository;
    @Mock BankTransactionRepository transactionRepository;
    @InjectMocks CardAuthorizationService service;

    @Test
    void shouldAuthorizeAgainstCurrentBalance() {
        UUID id = UUID.randomUUID();
        Account account = new Account();
        account.setId(id); account.setBalance(new BigDecimal("100")); account.setVersion(0L);
        when(transactionRepository.findByIdempotencyKey("card-1")).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(id)).thenReturn(Optional.of(account));
        when(transactionRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        var response = service.authorize(new CardAuthorizationRequest(id, new BigDecimal("25")), "card-1");

        assertEquals(new BigDecimal("75"), response.balanceAfter());
        verify(accountRepository).save(account);
    }

    @Test
    void shouldRejectWhenBalanceIsInsufficient() {
        UUID id = UUID.randomUUID();
        Account account = new Account(); account.setId(id); account.setBalance(new BigDecimal("5"));
        when(transactionRepository.findByIdempotencyKey("card-1")).thenReturn(Optional.empty());
        when(accountRepository.findByIdForUpdate(id)).thenReturn(Optional.of(account));
        assertThrows(InsufficientBalanceException.class,
                () -> service.authorize(new CardAuthorizationRequest(id, new BigDecimal("6")), "card-1"));
    }
}
