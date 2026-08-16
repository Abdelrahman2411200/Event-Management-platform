package com.eventplatform.attendee.application;

import com.eventplatform.attendee.api.AttendeeApi;
import com.eventplatform.attendee.domain.AttendeeProfile;
import com.eventplatform.attendee.domain.AttendeeProfileRepository;
import com.eventplatform.attendee.security.AuthenticatedActor;
import java.time.Clock;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendeeProfileService {
    private final AttendeeProfileRepository repository;
    private final Clock clock;

    public AttendeeProfileService(AttendeeProfileRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public AttendeeProfile ensure(AuthenticatedActor actor) {
        AttendeeProfile existing = repository.findById(actor.userId()).orElse(null);
        if (existing != null) {
            existing.synchronizeIdentity(actor.email(), clock.instant());
            return existing;
        }
        try {
            return repository.saveAndFlush(new AttendeeProfile(
                    actor.userId(), actor.email(), null, null, "en", clock.instant()));
        } catch (DataIntegrityViolationException exception) {
            return repository.findById(actor.userId()).orElseThrow(() -> exception);
        }
    }

    @Transactional
    public AttendeeApi.ProfileResponse get(AuthenticatedActor actor) {
        return response(repository.findById(actor.userId()).orElseGet(() -> ensure(actor)));
    }

    @Transactional
    public AttendeeApi.ProfileResponse update(AttendeeApi.ProfileRequest request, AuthenticatedActor actor) {
        AttendeeProfile profile = repository.findById(actor.userId()).orElseGet(() -> ensure(actor));
        profile.update(request.displayName(), request.phoneNumber(), request.locale(), Instant.now(clock));
        return response(profile);
    }

    private AttendeeApi.ProfileResponse response(AttendeeProfile profile) {
        return new AttendeeApi.ProfileResponse(
                profile.getId(), profile.getDisplayName(), profile.getPhoneNumber(), profile.getLocale(),
                profile.getCreatedAt(), profile.getUpdatedAt());
    }
}
