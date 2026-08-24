package com.hotelbooking.client;

import java.math.BigDecimal;

/** JSON shape from hotel-service {@code GET /internal/catalog/rooms/{id}}. */
public record RoomCatalogDto(
        Long roomId,
        Long hotelId,
        BigDecimal nightlyPrice,
        boolean active
) {
}
