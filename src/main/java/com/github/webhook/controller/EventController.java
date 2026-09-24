package com.github.webhook.controller;

import com.github.webhook.dto.request.CreateEventRequest;
import com.github.webhook.dto.response.EventResponse;
import com.github.webhook.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/events")
public class EventController {
    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@RequestBody @Valid CreateEventRequest request){
        EventResponse response = eventService.create(request);
        return ResponseEntity.accepted().body(response);
    }
}
