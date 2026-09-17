package com.corebank.card;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/cards/authorizations")
@RequiredArgsConstructor
public class CardAuthorizationController {
    private final CardAuthorizationService service;

    @PostMapping
    public CardAuthorizationResponse authorize(@RequestHeader("Idempotency-Key") String key,
                                               @Valid @RequestBody CardAuthorizationRequest request) {
        return service.authorize(request, key);
    }
}
