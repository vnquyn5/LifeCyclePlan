package com.lifestyleplanner.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.access.AccessDeniedException;

@RestController
public class ProtectedAuthTestController {
    private final CurrentUserProvider currentUserProvider;

    public ProtectedAuthTestController(CurrentUserProvider currentUserProvider) {
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/api/v1/test/protected-user")
    public String currentUser() {
        return currentUserProvider.getCurrentUserId().toString();
    }

    @GetMapping("/api/v1/test/users/{userId}")
    public String userResource(@PathVariable java.util.UUID userId) {
        if (!userId.equals(currentUserProvider.getCurrentUserId())) {
            throw new AccessDeniedException("User cannot access another user's resource");
        }
        return userId.toString();
    }

    @GetMapping("/api/v1/test/internal-error")
    public String internalError() {
        throw new IllegalStateException("synthetic internal failure");
    }
}
