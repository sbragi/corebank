package com.corebank.payment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public PaymentResponse pay(@RequestHeader("Idempotency-Key") String idempotencyKey,
                               @Valid @RequestBody PaymentRequest request) {
        return paymentService.process(request, idempotencyKey);
    }
}
