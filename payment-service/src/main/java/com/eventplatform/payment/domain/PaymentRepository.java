package com.eventplatform.payment.domain;
import java.time.Instant; import java.util.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import jakarta.persistence.LockModeType;
public interface PaymentRepository extends JpaRepository<Payment,UUID>{
    Optional<Payment> findByBookingId(UUID bookingId); Optional<Payment> findByProviderPaymentId(String providerPaymentId); List<Payment> findAllByAttendeeIdOrderByCreatedAtDesc(UUID attendeeId);
    List<Payment> findAllByStatusAndUpdatedAtBefore(PaymentStatus status,Instant before);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from Payment p where p.id=:id") Optional<Payment> findByIdForUpdate(@Param("id") UUID id);
}
