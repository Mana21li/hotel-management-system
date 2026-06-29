package com.hotelbooking.service;

import com.hotelbooking.dto.response.HotelResponse;
import com.hotelbooking.entity.Hotel;
import com.hotelbooking.exception.HotelNotFoundException;
import com.hotelbooking.repository.HotelRepository;
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

/**
 * Pure unit test for {@link HotelService}.
 * <p>
 * No Spring context, no database. The repository is mocked, so we test ONLY the
 * service's logic: mapping entity -> DTO and the active/not-found rules.
 */
@ExtendWith(MockitoExtension.class)
class HotelServiceTest {

    @Mock
    private HotelRepository hotelRepository;

    @InjectMocks
    private HotelService hotelService;

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

        List<HotelResponse> result = hotelService.listActiveHotels();

        assertThat(result).hasSize(1);
        HotelResponse first = result.get(0);
        assertThat(first.id()).isEqualTo(1L);
        assertThat(first.name()).isEqualTo("The Taj Seaside");
        assertThat(first.addressLine()).isEqualTo("Marine Drive, Nariman Point");
        assertThat(first.starRating()).isEqualTo((short) 5);
    }

    @Test
    void listActiveHotels_returnsEmptyList_whenNoHotels() {
        when(hotelRepository.findByActiveTrue()).thenReturn(List.of());

        assertThat(hotelService.listActiveHotels()).isEmpty();
    }

    @Test
    void getActiveHotelById_returnsHotel_whenFoundAndActive() {
        when(hotelRepository.findById(1L)).thenReturn(Optional.of(activeHotel(1L)));

        HotelResponse result = hotelService.getActiveHotelById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("The Taj Seaside");
    }

    @Test
    void getActiveHotelById_throws_whenNotFound() {
        when(hotelRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hotelService.getActiveHotelById(999L))
                .isInstanceOf(HotelNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    void getActiveHotelById_throws_whenHotelInactive() {
        Hotel inactive = Hotel.builder()
                .id(2L).name("Closed Hotel").addressLine("X").cityId(1L)
                .starRating((short) 3).active(false).build();
        when(hotelRepository.findById(2L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> hotelService.getActiveHotelById(2L))
                .isInstanceOf(HotelNotFoundException.class);
    }
}
