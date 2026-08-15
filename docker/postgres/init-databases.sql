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

-- Remove PostgreSQL's default database-wide CONNECT grant. Each service login
-- can connect only to its own database; the local platform administrator keeps
-- access for maintenance and migration troubleshooting.
REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;
GRANT CONNECT ON DATABASE postgres TO platform_admin;

REVOKE CONNECT ON DATABASE auth_service FROM PUBLIC;
GRANT CONNECT ON DATABASE auth_service TO auth_service;

REVOKE CONNECT ON DATABASE event_service FROM PUBLIC;
GRANT CONNECT ON DATABASE event_service TO event_service;

REVOKE CONNECT ON DATABASE venue_service FROM PUBLIC;
GRANT CONNECT ON DATABASE venue_service TO venue_service;

REVOKE CONNECT ON DATABASE attendee_service FROM PUBLIC;
GRANT CONNECT ON DATABASE attendee_service TO attendee_service;

REVOKE CONNECT ON DATABASE payment_service FROM PUBLIC;
GRANT CONNECT ON DATABASE payment_service TO payment_service;

REVOKE CONNECT ON DATABASE notification_service FROM PUBLIC;
GRANT CONNECT ON DATABASE notification_service TO notification_service;
