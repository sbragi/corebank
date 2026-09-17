package com.corebank.card;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CardAuthorizationRequest(@NotNull UUID accountId, @NotNull @Positive BigDecimal amount) {}
