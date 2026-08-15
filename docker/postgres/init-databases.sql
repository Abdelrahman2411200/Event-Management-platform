CREATE USER auth_service WITH PASSWORD 'local_dev_only';
CREATE DATABASE auth_service OWNER auth_service;

CREATE USER event_service WITH PASSWORD 'local_dev_only';
CREATE DATABASE event_service OWNER event_service;

CREATE USER venue_service WITH PASSWORD 'local_dev_only';
CREATE DATABASE venue_service OWNER venue_service;

CREATE USER attendee_service WITH PASSWORD 'local_dev_only';
CREATE DATABASE attendee_service OWNER attendee_service;

CREATE USER payment_service WITH PASSWORD 'local_dev_only';
CREATE DATABASE payment_service OWNER payment_service;

CREATE USER notification_service WITH PASSWORD 'local_dev_only';
CREATE DATABASE notification_service OWNER notification_service;
