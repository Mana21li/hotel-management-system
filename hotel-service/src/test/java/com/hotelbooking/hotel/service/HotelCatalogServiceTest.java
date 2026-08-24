package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.HotelResponse;
import com.hotelbooking.hotel.entity.Hotel;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.repository.HotelRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelCatalogServiceTest {

    @Mock
    private HotelRepository hotelRepository;

    @InjectMocks
    private HotelCatalogService hotelCatalogService;

    private Hotel activeHotel(Long id) {
        return Hotel.builder()
                .id(id)
                .name("The Taj Seaside")
                .description("Iconic 5-star beachfront luxury.")
                .addressLine("Marine Drive, Nariman Point")
                .cityId(1L)
                .starRating((short) 5)
                .active(true)
                .build();
    }

    @Test
    void listActiveHotels_mapsEntitiesToResponses() {
        when(hotelRepository.findByActiveTrue()).thenReturn(List.of(activeHotel(1L)));

        List<HotelResponse> result = hotelCatalogService.listActiveHotels();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("The Taj Seaside");
        assertThat(result.getFirst().starRating()).isEqualTo((short) 5);
    }

    @Test
    void getActiveHotelById_throws_whenNotFound() {
        when(hotelRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hotelCatalogService.getActiveHotelById(999L))
                .isInstanceOf(HotelNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void getActiveHotelById_throws_whenHotelInactive() {
        Hotel inactive = Hotel.builder()
                .id(2L).name("Closed Hotel").addressLine("X").cityId(1L)
                .starRating((short) 3).active(false).build();
        when(hotelRepository.findById(2L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> hotelCatalogService.getActiveHotelById(2L))
                .isInstanceOf(HotelNotFoundException.class);
    }
}
