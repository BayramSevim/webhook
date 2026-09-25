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
    private final DeliveryStateService deliveryStateService;

    public DeliveryWorker(DeliveryRepository deliveryRepository, DeliverySender deliverySender, DeliveryStateService deliveryStateService) {
        this.deliveryRepository = deliveryRepository;
        this.deliverySender = deliverySender;
        this.deliveryStateService = deliveryStateService;
    }

    @Scheduled(fixedDelay = 5000)
    public void poll() {
        List<DeliveryJob> jobs = deliveryStateService.claimDue(BATCH_SIZE);
        if(jobs.isEmpty())
            return;

        log.info("Claimed {} deliveries", jobs.size());

        jobs.forEach(job -> {
            log.info("Sending delivery {}", job.deliveryId());
            SendResult result = deliverySender.send(job);
            deliveryStateService.recordResult(job.deliveryId(),result);
        });
    }
}
