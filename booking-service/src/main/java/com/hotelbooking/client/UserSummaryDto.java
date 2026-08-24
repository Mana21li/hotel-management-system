package com.hotelbooking.client;

public record UserSummaryDto(
        Long userId,
        String fullName,
        boolean active
) {
}
