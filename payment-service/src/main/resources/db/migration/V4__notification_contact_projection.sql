ALTER TABLE booking_payment_orders ADD COLUMN event_title VARCHAR(240);
ALTER TABLE booking_payment_orders ADD COLUMN attendee_email VARCHAR(320);
ALTER TABLE booking_payment_orders ADD COLUMN attendee_phone VARCHAR(32);
ALTER TABLE booking_payment_orders ADD COLUMN attendee_locale VARCHAR(16);
