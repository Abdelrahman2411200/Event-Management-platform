CREATE TABLE venues (
    id UUID PRIMARY KEY,
    organizer_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(4000),
    address_line_1 VARCHAR(250) NOT NULL,
    address_line_2 VARCHAR(250),
    city VARCHAR(120) NOT NULL,
    region VARCHAR(120),
    postal_code VARCHAR(32),
    country_code VARCHAR(2) NOT NULL,
    latitude NUMERIC(9, 6),
    longitude NUMERIC(9, 6),
    timezone VARCHAR(64) NOT NULL,
    total_capacity INTEGER NOT NULL CHECK (total_capacity > 0),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_venues_organizer_status ON venues (organizer_id, status);
CREATE INDEX idx_venues_city_status ON venues (city, status);

CREATE TABLE venue_amenities (
    venue_id UUID NOT NULL,
    amenity VARCHAR(100) NOT NULL,
    PRIMARY KEY (venue_id, amenity),
    CONSTRAINT fk_venue_amenities_venue FOREIGN KEY (venue_id) REFERENCES venues (id) ON DELETE CASCADE
);

CREATE TABLE venue_metadata (
    venue_id UUID NOT NULL,
    metadata_key VARCHAR(100) NOT NULL,
    metadata_value VARCHAR(500) NOT NULL,
    PRIMARY KEY (venue_id, metadata_key),
    CONSTRAINT fk_venue_metadata_venue FOREIGN KEY (venue_id) REFERENCES venues (id) ON DELETE CASCADE
);

CREATE TABLE venue_spaces (
    id UUID PRIMARY KEY,
    venue_id UUID NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    capacity INTEGER NOT NULL CHECK (capacity > 0),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    archived_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_venue_spaces_venue FOREIGN KEY (venue_id) REFERENCES venues (id) ON DELETE CASCADE,
    CONSTRAINT uq_venue_space_name UNIQUE (venue_id, name)
);

CREATE INDEX idx_venue_spaces_venue_status ON venue_spaces (venue_id, status);

CREATE TABLE venue_space_amenities (
    venue_space_id UUID NOT NULL,
    amenity VARCHAR(100) NOT NULL,
    PRIMARY KEY (venue_space_id, amenity),
    CONSTRAINT fk_space_amenities_space FOREIGN KEY (venue_space_id) REFERENCES venue_spaces (id) ON DELETE CASCADE
);

CREATE TABLE venue_availability_entries (
    id UUID PRIMARY KEY,
    venue_id UUID NOT NULL,
    venue_space_id UUID,
    kind VARCHAR(32) NOT NULL,
    owner_reference VARCHAR(120),
    reason VARCHAR(500),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    required_capacity INTEGER NOT NULL CHECK (required_capacity > 0),
    status VARCHAR(32) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    released_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_availability_venue FOREIGN KEY (venue_id) REFERENCES venues (id) ON DELETE CASCADE,
    CONSTRAINT fk_availability_space FOREIGN KEY (venue_space_id) REFERENCES venue_spaces (id) ON DELETE CASCADE,
    CONSTRAINT chk_availability_window CHECK (ends_at > starts_at),
    CONSTRAINT uq_availability_owner_reference UNIQUE (owner_reference)
);

CREATE INDEX idx_availability_venue_window
    ON venue_availability_entries (venue_id, starts_at, ends_at, status);
CREATE INDEX idx_availability_space_window
    ON venue_availability_entries (venue_space_id, starts_at, ends_at, status);
