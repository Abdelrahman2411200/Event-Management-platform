package com.eventplatform.payment.domain;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentTicketProjectionRepository extends JpaRepository<PaymentTicketProjection,UUID>{List<PaymentTicketProjection> findAllByBookingId(UUID bookingId);}
