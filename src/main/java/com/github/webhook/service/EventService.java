package com.github.webhook.service;

import com.github.webhook.DeliverySender;
import com.github.webhook.dto.request.CreateEventRequest;
import com.github.webhook.dto.response.EventResponse;
import com.github.webhook.entity.Delivery;
import com.github.webhook.entity.Event;
import com.github.webhook.entity.Subscription;
import com.github.webhook.exception.EventNotFoundException;
import com.github.webhook.repository.DeliveryRepository;
import com.github.webhook.repository.EventRepository;
import com.github.webhook.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EventService {
    private final EventRepository eventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliverySender deliverySender;

    public EventService(EventRepository eventRepository, SubscriptionRepository subscriptionRepository, DeliveryRepository deliveryRepository, DeliverySender deliverySender) {
        this.eventRepository = eventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliverySender = deliverySender;
    }

    @Transactional
    public EventResponse create(CreateEventRequest request,String idempotencyKey){
        Optional<Event> existing = eventRepository.findByTenantIdAndIdempotencyKey(request.tenantId(),idempotencyKey);

        if(existing.isPresent()){
            Event event = existing.get();
            long count = deliveryRepository.countByEventId(event.getId());
            return EventResponse.from(event,(int) count);
        }

        Event event = new Event(
                request.tenantId(),
                request.eventType(),
                request.payload().toString(),
                idempotencyKey
        );

        Event savedEvent = eventRepository.save(event);
        List<Delivery> deliveries =  subscriptionRepository
                .findActiveByTenantAndEventType(savedEvent.getTenantId(),savedEvent.getEventType())
                .stream()
                .map(subscription -> new Delivery(savedEvent,subscription))
                .toList();

        deliveryRepository.saveAll(deliveries);
        deliveries.forEach(deliverySender::send);

        return EventResponse.from(savedEvent,deliveries.size());
    }



}
