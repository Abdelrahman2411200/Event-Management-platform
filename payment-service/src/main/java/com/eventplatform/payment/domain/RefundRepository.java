package com.eventplatform.payment.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface RefundRepository extends JpaRepository<Refund,UUID>{Optional<Refund> findByRequestedByAndIdempotencyKey(UUID requestedBy,String key);List<Refund> findAllByPaymentIdOrderByCreatedAt(UUID paymentId);}
