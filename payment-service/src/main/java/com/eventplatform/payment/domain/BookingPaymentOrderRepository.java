package com.eventplatform.payment.domain;
import jakarta.persistence.LockModeType; import java.util.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
public interface BookingPaymentOrderRepository extends JpaRepository<BookingPaymentOrder,UUID>{@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from BookingPaymentOrder o where o.bookingId=:id") Optional<BookingPaymentOrder> findByIdForUpdate(@Param("id") UUID id);}
