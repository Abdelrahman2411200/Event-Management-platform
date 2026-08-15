package com.eventplatform.venue.infrastructure.maps;

import com.eventplatform.venue.application.LocationEnrichmentPort;
import com.eventplatform.venue.domain.VenueLocation;
import org.springframework.stereotype.Component;

/** Local adapter that preserves caller-supplied coordinates without contacting an external maps provider. */
@Component
public class LocalLocationEnrichmentAdapter implements LocationEnrichmentPort {

    @Override
    public VenueLocation enrich(VenueLocation suppliedLocation) {
        return suppliedLocation;
    }
}
