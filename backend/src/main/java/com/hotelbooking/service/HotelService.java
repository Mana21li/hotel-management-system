package com.hotelbooking.service;

import com.hotelbooking.exception.HotelNotFoundException;
import com.hotelbooking.dto.response.HotelResponse;
import com.hotelbooking.entity.Hotel;
import com.hotelbooking.repository.HotelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for hotel read operations.
 * <p>
 * Fetches entities from the repository and maps them to API DTOs.
 * No HTTP concerns here — only domain/persistence orchestration.
 */
@Service
@Transactional(readOnly = true)
public class HotelService {

    private final HotelRepository hotelRepository;

    public HotelService(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    /**
     * Returns all active hotels for {@code GET /api/hotels}.
     * Contract: {@code docs/api/hotels.md}
     */
    public List<HotelResponse> listActiveHotels() {
        return hotelRepository.findByActiveTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns one active hotel for {@code GET /api/hotels/{id}}.
     * Throws {@link HotelNotFoundException} when missing or inactive.
     */
    public HotelResponse getActiveHotelById(Long id) {
        Hotel hotel = hotelRepository.findById(id)
                .filter(Hotel::isActive)
                .orElseThrow(() -> new HotelNotFoundException(id));
        return toResponse(hotel);
    }

    private HotelResponse toResponse(Hotel hotel) {
        return new HotelResponse(
                hotel.getId(),
                hotel.getName(),
                hotel.getDescription(),
                hotel.getAddressLine(),
                hotel.getStarRating()
        );
    }
}
