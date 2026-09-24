package com.github.webhook.service;

import com.github.webhook.dto.request.CreateSubscriptionRequest;
import com.github.webhook.dto.response.SubscriptionResponse;
import com.github.webhook.entity.Subscription;
import com.github.webhook.exception.SubscriptionNotFoundException;
import com.github.webhook.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SubscriptionService {
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public SubscriptionResponse create(CreateSubscriptionRequest request){
        Subscription subscription = new Subscription(
                request.tenantId(),
                request.url(),
                request.secret(),
                request.eventTypes()
        );

       return SubscriptionResponse.from(subscriptionRepository.save(subscription));
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse getById(UUID id){
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(()-> new SubscriptionNotFoundException(id));

        return SubscriptionResponse.from(subscription);
    }


}
