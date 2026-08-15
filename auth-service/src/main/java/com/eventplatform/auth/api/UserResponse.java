package com.eventplatform.auth.api;

import com.eventplatform.auth.user.Role;
import com.eventplatform.auth.user.UserAccount;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(UUID id, String email, Set<Role> roles, Instant createdAt) {

    public static UserResponse from(UserAccount user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRoles(), user.getCreatedAt());
    }
}
