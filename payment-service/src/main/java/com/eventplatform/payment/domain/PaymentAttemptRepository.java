package com.eventplatform.payment.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt,UUID>{Optional<PaymentAttempt> findByAttendeeIdAndIdempotencyKey(UUID attendeeId,String key); List<PaymentAttempt> findAllByPaymentIdOrderByCreatedAt(UUID paymentId);}
