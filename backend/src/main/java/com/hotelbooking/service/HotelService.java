package com.hotelbooking.service;

import com.hotelbooking.config.RedisCacheConfig;
import com.hotelbooking.exception.HotelNotFoundException;
import com.hotelbooking.dto.response.HotelResponse;
import com.hotelbooking.entity.Hotel;
import com.hotelbooking.repository.HotelRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
     * <p>
     * Cached under a single fixed key {@code hotelList::all}. There are no input
     * arguments, so we pin the key with a SpEL literal. NOTE: when a hotel
     * write/update path is added, this cache must be evicted there.
     */
    @Cacheable(cacheNames = RedisCacheConfig.HOTEL_LIST, key = "'all'")
    public List<HotelResponse> listActiveHotels() {
        // Collect into a mutable ArrayList (not Stream#toList's immutable type): the
        // JSON cache serializer can record/restore ArrayList's concrete type, but not
        // the JDK's hidden immutable list class, which would fail on cache read.
        return hotelRepository.findByActiveTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Returns one active hotel for {@code GET /api/hotels/{id}}.
     * Throws {@link HotelNotFoundException} when missing or inactive.
     * <p>
     * Cached under {@code hotelById::<id>}. The thrown exception is NOT cached
     * (only successful returns are), so a 404 won't poison the cache. NOTE: add a
     * matching {@code @CacheEvict} when a hotel update endpoint exists.
     */
    @Cacheable(cacheNames = RedisCacheConfig.HOTEL_BY_ID, key = "#id")
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
