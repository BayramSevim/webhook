package com.github.webhook.controller;

import com.github.webhook.dto.response.DeliveryResponse;
import com.github.webhook.service.DeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/deliveries")
public class DeliveryController {
    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping
    public ResponseEntity<List<DeliveryResponse>> list(@RequestParam UUID eventId){
        return ResponseEntity.ok(deliveryService.findByEventId(eventId));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<DeliveryResponse> retry(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(deliveryService.retry(id));
    }
}
