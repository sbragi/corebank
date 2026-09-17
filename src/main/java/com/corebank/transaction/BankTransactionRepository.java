package com.corebank.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, UUID> {
    Optional<BankTransaction> findByIdempotencyKey(String idempotencyKey);
}
