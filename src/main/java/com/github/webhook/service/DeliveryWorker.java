package com.github.webhook.service;

import com.github.webhook.DeliverySender;
import com.github.webhook.entity.Delivery;
import com.github.webhook.repository.DeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DeliveryWorker {
    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);
    private static final int BATCH_SIZE = 10;
    private final DeliveryRepository deliveryRepository;
    private final DeliverySender deliverySender;

    public DeliveryWorker(DeliveryRepository deliveryRepository, DeliverySender deliverySender) {
        this.deliveryRepository = deliveryRepository;
        this.deliverySender = deliverySender;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void poll() {
        List<Delivery> deliveries = deliveryRepository.findDue(BATCH_SIZE);
        if(deliveries.isEmpty())
            return;

        log.info("Found {} due deliveries", deliveries.size());
        deliveries.forEach(delivery -> {
            log.info("Sending delivery {}", delivery.getId());
            deliverySender.send(delivery);
        });
    }
}
