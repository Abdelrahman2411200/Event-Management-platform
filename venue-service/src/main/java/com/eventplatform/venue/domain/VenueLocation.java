package com.eventplatform.venue.domain;

import java.math.BigDecimal;

public record VenueLocation(
        String addressLine1,
        String addressLine2,
        String city,
        String region,
        String postalCode,
        String countryCode,
        BigDecimal latitude,
        BigDecimal longitude) {
}
