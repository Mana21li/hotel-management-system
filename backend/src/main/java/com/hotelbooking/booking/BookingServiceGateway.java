package com.hotelbooking.booking;

import com.hotelbooking.dto.request.CreateBookingRequest;
import com.hotelbooking.dto.response.BookingResponse;
import com.hotelbooking.exception.BookingServiceUnavailableException;
import com.hotelbooking.exception.InvalidBookingDateException;
import com.hotelbooking.exception.RoomNotAvailableException;
import com.hotelbooking.exception.RoomNotFoundException;
import com.hotelbooking.exception.UserNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strangler-fig client: {@code POST /api/bookings} stays on :8080
 * while lock + EXCLUDE + outbox live in {@code booking-service}.
 */
@Component
public class BookingServiceGateway {

    private static final Pattern ROOM_UNAVAILABLE = Pattern.compile(
            "Room (\\d+) is not available for the selected dates \\((\\d{4}-\\d{2}-\\d{2}) to (\\d{4}-\\d{2}-\\d{2})\\)");
    private static final Pattern ID_PATTERN = Pattern.compile("id: (\\d+)");

    private final RestClient restClient;

    public BookingServiceGateway(
            @Value("${booking.service.base-url:http://127.0.0.1:8087}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    public BookingResponse createBooking(CreateBookingRequest request) {
        try {
            return restClient.post()
                    .uri("/api/bookings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(BookingResponse.class);
        } catch (RestClientResponseException | ResourceAccessException ex) {
            throw translate(ex);
        }
    }

    private RuntimeException translate(RuntimeException ex) {
        if (ex instanceof ResourceAccessException access) {
            return BookingServiceUnavailableException.from(access);
        }
        if (ex instanceof RestClientResponseException http) {
            int status = http.getStatusCode().value();
            String body = http.getResponseBodyAsString();
            if (status == 404) {
                long id = extractId(body);
                if (body.contains("User not found")) {
                    return new UserNotFoundException(id);
                }
                return new RoomNotFoundException(id);
            }
            if (status == 409) {
                Matcher matcher = ROOM_UNAVAILABLE.matcher(body);
                if (matcher.find()) {
                    return new RoomNotAvailableException(
                            Long.parseLong(matcher.group(1)),
                            LocalDate.parse(matcher.group(2)),
                            LocalDate.parse(matcher.group(3)));
                }
                return new RoomNotAvailableException(-1L, LocalDate.now(), LocalDate.now().plusDays(1));
            }
            if (status == 400) {
                return new InvalidBookingDateException(extractJsonMessage(body, http.getStatusText()));
            }
            HttpStatusCode code = http.getStatusCode();
            if (code.value() == 503) {
                return BookingServiceUnavailableException.from(http);
            }
        }
        return BookingServiceUnavailableException.from(ex);
    }

    private static long extractId(String body) {
        Matcher matcher = ID_PATTERN.matcher(body);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : -1L;
    }

    private static String extractJsonMessage(String body, String fallback) {
        Matcher matcher = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        return matcher.find() ? matcher.group(1) : fallback;
    }
}
