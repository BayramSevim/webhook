package com.github.webhook.service;
import com.github.webhook.config.KafkaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "webhook.dispatch", havingValue = "kafka")
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 10;
    private final DeliveryStateService deliveryStateService;
    private final KafkaTemplate<String,String> kafkaTemplate;

    public OutboxRelay(DeliveryStateService deliveryStateService, KafkaTemplate<String, String> kafkaTemplate) {
        this.deliveryStateService = deliveryStateService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    public void relay() {
        List<DeliveryJob> jobs = deliveryStateService.claimDue(BATCH_SIZE);
        if(jobs.isEmpty())
            return;

        jobs.forEach(job -> {
            try {
                String key = job.subscriptionId().toString();
                String value = job.deliveryId().toString();
                kafkaTemplate.send(KafkaConfig.DELIVERIES_TOPIC,key,value).join();
                log.info("Published delivery {}", job.deliveryId());
            }
            catch (Exception ex){
                log.error("Publish failed for delivery {}", job.deliveryId(), ex);
            }
        });
    }
}