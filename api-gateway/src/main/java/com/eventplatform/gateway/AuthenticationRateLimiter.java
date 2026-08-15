package com.eventplatform.gateway;

import java.util.Map;
import reactor.core.publisher.Mono;

public interface AuthenticationRateLimiter {

    Mono<Decision> check(Policy policy, String clientKey);

    enum Policy {
        REGISTRATION,
        LOGIN,
        REFRESH
    }

    record Decision(boolean allowed, Map<String, String> headers) {
        public Decision {
            headers = headers == null ? Map.of() : Map.copyOf(headers);
        }
    }
}
