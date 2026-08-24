package com.hotelbooking.user.controller;

import com.hotelbooking.user.dto.UserSummaryResponse;
import com.hotelbooking.user.service.UserLookupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service user lookups. Not exposed via the :8080 strangler.
 */
@RestController
@RequestMapping("/internal/users")
public class UserInternalController {

    private final UserLookupService userLookupService;

    public UserInternalController(UserLookupService userLookupService) {
        this.userLookupService = userLookupService;
    }

    @GetMapping("/{userId}")
    public UserSummaryResponse getUser(@PathVariable Long userId) {
        return userLookupService.getActiveUser(userId);
    }
}
