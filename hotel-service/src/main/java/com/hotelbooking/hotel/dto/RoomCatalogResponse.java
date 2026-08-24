package com.hotelbooking.hotel.dto;

import java.math.BigDecimal;

/** Room definition returned to booking-service for validation + price snapshot. */
public record RoomCatalogResponse(
        Long roomId,
        Long hotelId,
        BigDecimal nightlyPrice,
        boolean active
) {
}
