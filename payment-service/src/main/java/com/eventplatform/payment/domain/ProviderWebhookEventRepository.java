package com.eventplatform.payment.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface ProviderWebhookEventRepository extends JpaRepository<ProviderWebhookEvent,UUID>{Optional<ProviderWebhookEvent> findByProviderAndProviderEventId(String provider,String eventId);}
