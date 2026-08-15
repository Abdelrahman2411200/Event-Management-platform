package com.eventplatform.venue.application;

import com.eventplatform.venue.domain.VenueLocation;

/** Provider-neutral boundary for future geocoding and address normalization adapters. */
public interface LocationEnrichmentPort {

    VenueLocation enrich(VenueLocation suppliedLocation);
}
