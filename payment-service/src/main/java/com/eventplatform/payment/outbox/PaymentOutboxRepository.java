package com.eventplatform.payment.outbox;
import jakarta.persistence.LockModeType; import java.time.Instant; import java.util.*; import org.springframework.data.domain.Pageable; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
interface PaymentOutboxRepository extends JpaRepository<PaymentOutboxMessage,UUID>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select m from PaymentOutboxMessage m where m.publishedAt is null and m.nextAttemptAt<=:now order by m.occurredAt") List<PaymentOutboxMessage> findPending(@Param("now") Instant now,Pageable pageable);
}
