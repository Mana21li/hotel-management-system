package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.config.RedisCacheConfig;
import com.hotelbooking.hotel.dto.HotelResponse;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.repository.HotelRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class HotelCatalogService {

    private final HotelRepository hotelRepository;

    public HotelCatalogService(HotelRepository hotelRepository) {
        this.hotelRepository = hotelRepository;
    }

    @Cacheable(cacheNames = RedisCacheConfig.HOTEL_LIST, key = "'all'")
    public List<HotelResponse> listActiveHotels() {
        return hotelRepository.findByActiveTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toCollection(ArrayList::new));
    }

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
