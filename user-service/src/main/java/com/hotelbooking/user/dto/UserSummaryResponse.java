package com.hotelbooking.user.dto;

public record UserSummaryResponse(
        Long userId,
        String fullName,
        boolean active
) {
}
