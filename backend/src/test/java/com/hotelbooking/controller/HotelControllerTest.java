package com.hotelbooking.controller;

import com.hotelbooking.dto.response.HotelResponse;
import com.hotelbooking.exception.HotelNotFoundException;
import com.hotelbooking.service.HotelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice test for {@link HotelController}.
 * <p>
 * {@code @WebMvcTest} loads ONLY the web layer (controllers, JSON, exception handlers),
 * not the full application or the database. The service is mocked, so we verify
 * routing, HTTP status codes, and JSON output independently of business logic.
 */
@WebMvcTest(HotelController.class)
class HotelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HotelService hotelService;

    @Test
    void listHotels_returns200AndJsonArray() throws Exception {
        when(hotelService.listActiveHotels()).thenReturn(List.of(
                new HotelResponse(1L, "The Taj Seaside", "Luxury", "Marine Drive", (short) 5)
        ));

        mockMvc.perform(get("/api/hotels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("The Taj Seaside"))
                .andExpect(jsonPath("$[0].starRating").value(5));
    }

    @Test
    void getHotel_returns200_whenFound() throws Exception {
        when(hotelService.getActiveHotelById(1L)).thenReturn(
                new HotelResponse(1L, "The Taj Seaside", "Luxury", "Marine Drive", (short) 5));

        mockMvc.perform(get("/api/hotels/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("The Taj Seaside"));
    }

    @Test
    void getHotel_returns404_whenMissing() throws Exception {
        when(hotelService.getActiveHotelById(999L))
                .thenThrow(new HotelNotFoundException(999L));

        mockMvc.perform(get("/api/hotels/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Hotel not found with id: 999"))
                .andExpect(jsonPath("$.path").value("/api/hotels/999"));
    }
}
