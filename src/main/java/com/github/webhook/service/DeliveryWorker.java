package com.github.webhook.service;

import com.github.webhook.DeliverySender;
import com.github.webhook.repository.DeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "webhook.dispatch", havingValue = "db", matchIfMissing = true)
public class DeliveryWorker {
    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);
    private static final int BATCH_SIZE = 10;
    private final DeliveryStateService deliveryStateService;
    private final DeliveryProcessor deliveryProcessor;

    public DeliveryWorker(DeliveryStateService deliveryStateService, DeliveryProcessor deliveryProcessor) {
        this.deliveryStateService = deliveryStateService;
        this.deliveryProcessor = deliveryProcessor;
    }

    @Scheduled(fixedDelay = 5000)
    public void poll() {
        List<DeliveryJob> jobs = deliveryStateService.claimDue(BATCH_SIZE);
        if(jobs.isEmpty())
            return;

        log.info("Claimed {} deliveries", jobs.size());

        jobs.forEach(deliveryProcessor::process);
    }
}
