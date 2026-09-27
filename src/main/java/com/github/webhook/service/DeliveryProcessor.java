package com.github.webhook.service;


import com.github.webhook.DeliverySender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DeliveryProcessor {
    private static final Logger log = LoggerFactory.getLogger(DeliveryProcessor.class);
    private final DeliverySender deliverySender;
    private final DeliveryStateService deliveryStateService;

    public DeliveryProcessor(DeliverySender deliverySender, DeliveryStateService deliveryStateService) {
        this.deliverySender = deliverySender;
        this.deliveryStateService = deliveryStateService;
    }

    public void process(DeliveryJob job) {
        try{
            log.info("Sending delivery {}",job.deliveryId());
            SendResult result = deliverySender.send(job);
            deliveryStateService.recordResult(job.deliveryId(),result);
        }
        catch (Exception ex){
            log.error("Delivery {} failed unexpectedly", job.deliveryId(), ex);
        }
    }
}