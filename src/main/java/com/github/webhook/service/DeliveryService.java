package com.github.webhook.service;

import com.github.webhook.dto.response.DeliveryResponse;
import com.github.webhook.entity.Event;
import com.github.webhook.exception.EventNotFoundException;
import com.github.webhook.repository.DeliveryRepository;
import com.github.webhook.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DeliveryService {
    private final DeliveryRepository deliveryRepository;
    private final EventRepository eventRepository;

    public DeliveryService(DeliveryRepository deliveryRepository, EventRepository eventRepository) {
        this.deliveryRepository = deliveryRepository;
        this.eventRepository = eventRepository;
    }


    @Transactional(readOnly = true)
    public List<DeliveryResponse> findByEventId(UUID eventId){
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException(eventId);
        }
        return deliveryRepository.findByEventId(eventId).stream()
                .map(DeliveryResponse::from)
                .toList();
    }
}
