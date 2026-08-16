package com.eventplatform.notification.provider;

import com.eventplatform.notification.domain.LocalDelivery;
import com.eventplatform.notification.domain.LocalDeliveryRepository;
import com.eventplatform.notification.domain.NotificationChannel;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "platform.notification.sms-provider", havingValue = "local", matchIfMissing = true)
public class LocalSmsAdapter implements SmsSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalSmsAdapter.class);
    private final LocalDeliveryRepository repository;
    private final Clock clock;

    public LocalSmsAdapter(LocalDeliveryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override public String name() { return "local-sms"; }

    @Override
    public ProviderReceipt send(ProviderMessage message) {
        LocalDelivery existing = repository.findByIdempotencyKey(message.idempotencyKey()).orElse(null);
        if (existing != null) return new ProviderReceipt(existing.getId().toString());
        LocalDelivery delivery = repository.save(new LocalDelivery(
                UUID.randomUUID(), message.idempotencyKey(), NotificationChannel.SMS,
                message.destination(), null, message.body(), clock.instant()));
        LOGGER.info("Local SMS recorded deliveryId={} destination={} idempotencyKey={}",
                delivery.getId(), message.destination(), message.idempotencyKey());
        return new ProviderReceipt(delivery.getId().toString());
    }
}
