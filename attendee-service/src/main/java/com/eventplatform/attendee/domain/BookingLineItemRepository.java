package com.eventplatform.attendee.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingLineItemRepository extends JpaRepository<BookingLineItem, UUID> {
    List<BookingLineItem> findAllByBookingId(UUID bookingId);
}
