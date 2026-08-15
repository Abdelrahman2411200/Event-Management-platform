package com.eventplatform.event.application;

import java.util.UUID;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
public class PublicEventCache {

    public static final String DETAILS = "public-event-details";

    private final CacheManager cacheManager;

    public PublicEventCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evict(UUID eventId) {
        Cache cache = cacheManager.getCache(DETAILS);
        if (cache != null) {
            cache.evict(eventId);
        }
    }
}
