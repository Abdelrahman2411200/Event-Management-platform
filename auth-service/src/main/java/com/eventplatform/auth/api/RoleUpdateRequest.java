package com.eventplatform.auth.api;

import com.eventplatform.auth.user.Role;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record RoleUpdateRequest(@NotEmpty Set<@NotNull Role> roles) {
}
