package com.hotelbooking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelbooking.dto.request.CreateBookingRequest;
import com.hotelbooking.dto.response.BookingResponse;
import com.hotelbooking.exception.InvalidBookingDateException;
import com.hotelbooking.exception.RoomNotAvailableException;
import com.hotelbooking.exception.RoomNotFoundException;
import com.hotelbooking.exception.UserNotFoundException;
import com.hotelbooking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice test for {@link BookingController}.
 * <p>
 * {@code @WebMvcTest} loads ONLY the web layer (controller, Jackson, the
 * {@code @RestControllerAdvice} exception handler) — not the service or database.
 * The service is mocked so we verify HTTP concerns in isolation: {@code @Valid}
 * field validation (400), the success status + {@code Location} header (201), and
 * that each service exception maps to the right status code.
 */
@WebMvcTest(BookingController.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private BookingService bookingService;

    private static final LocalDate CHECK_IN = LocalDate.now().plusDays(1);
    private static final LocalDate CHECK_OUT = LocalDate.now().plusDays(3);

    private CreateBookingRequest validRequest() {
        return new CreateBookingRequest(1L, 10L, CHECK_IN, CHECK_OUT);
    }

    private BookingResponse sampleResponse() {
        return new BookingResponse(
                500L,
                "BK-ABC1234567890DEF",
                1L,
                10L,
                "The Taj Seaside",
                CHECK_IN,
                CHECK_OUT,
                2,
                new BigDecimal("5000.00"),
                new BigDecimal("10000.00"),
                "PENDING",
                Instant.now());
    }

    @Test
    void createBooking_returns201WithLocationAndBody() throws Exception {
        when(bookingService.createBooking(any(CreateBookingRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/bookings/500"))
                .andExpect(jsonPath("$.bookingId").value(500))
                .andExpect(jsonPath("$.bookingReference").value("BK-ABC1234567890DEF"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.nights").value(2))
                .andExpect(jsonPath("$.totalAmount").value(10000.00))
                .andExpect(jsonPath("$.hotelName").value("The Taj Seaside"));
    }

    @Test
    void createBooking_returns400_whenRequiredFieldMissing() throws Exception {
        // userId is null -> @NotNull violation -> MethodArgumentNotValidException -> 400
        String body = objectMapper.writeValueAsString(
                new CreateBookingRequest(null, 10L, CHECK_IN, CHECK_OUT));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("userId")));
    }

    @Test
    void createBooking_returns400_whenDateInPast() throws Exception {
        // @FutureOrPresent violation on checkInDate
        String body = objectMapper.writeValueAsString(new CreateBookingRequest(
                1L, 10L, LocalDate.now().minusDays(1), CHECK_OUT));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createBooking_returns400_whenServiceThrowsInvalidDate() throws Exception {
        when(bookingService.createBooking(any(CreateBookingRequest.class)))
                .thenThrow(new InvalidBookingDateException("checkOutDate must be after checkInDate"));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("checkOutDate must be after checkInDate"));
    }

    @Test
    void createBooking_returns404_whenUserNotFound() throws Exception {
        when(bookingService.createBooking(any(CreateBookingRequest.class)))
                .thenThrow(new UserNotFoundException(1L));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    void createBooking_returns404_whenRoomNotFound() throws Exception {
        when(bookingService.createBooking(any(CreateBookingRequest.class)))
                .thenThrow(new RoomNotFoundException(10L));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void createBooking_returns409_whenRoomNotAvailable() throws Exception {
        when(bookingService.createBooking(any(CreateBookingRequest.class)))
                .thenThrow(new RoomNotAvailableException(10L, CHECK_IN, CHECK_OUT));

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"));
    }
}
