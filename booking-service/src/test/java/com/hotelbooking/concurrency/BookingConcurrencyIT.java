package com.hotelbooking.concurrency;

import com.hotelbooking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration concurrency test for {@code POST /api/bookings}.
 * <p>
 * Requires Docker infrastructure to be running:
 * {@code docker compose up -d} (Postgres + Redis).
 * <p>
 * Fires many parallel HTTP requests for the same room + dates and asserts:
 * <ul>
 *   <li>exactly one {@code 201 Created}</li>
 *   <li>all others {@code 409 Conflict}</li>
 *   <li>exactly one row in the database for that slot</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BookingConcurrencyIT {

    private static final int CONCURRENT_REQUESTS = 25;
    private static final long ROOM_ID = 20L; // Bayview Suites — no future seed booking on this room

    @LocalServerPort
    private int port;

    @Autowired
    private BookingRepository bookingRepository;

    private RestClient restClient;
    private LocalDate checkIn;
    private LocalDate checkOut;

    @BeforeEach
    void setUp() {
        restClient = RestClient.create("http://localhost:" + port);

        // Unique future dates per test run so re-runs do not collide with prior rows.
        long dayOffset = Math.abs(UUID.randomUUID().getMostSignificantBits() % 200_000);
        checkIn = LocalDate.of(2050, 1, 1).plusDays(dayOffset);
        checkOut = checkIn.plusDays(3);
    }

    @Test
    void onlyOneBookingSucceeds_whenManyUsersBookSameRoomAndDates() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);

        List<Integer> statusCodes = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger unexpectedErrors = new AtomicInteger();

        try {
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                int userId = (i % 10) + 1; // cycle through 10 seeded users
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await(30, TimeUnit.SECONDS);
                        int status = postBooking(userId);
                        statusCodes.add(status);
                        if (status != 201 && status != 409) {
                            unexpectedErrors.incrementAndGet();
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        unexpectedErrors.incrementAndGet();
                    } catch (Exception ex) {
                        unexpectedErrors.incrementAndGet();
                    }
                });
            }

            assertThat(ready.await(30, TimeUnit.SECONDS))
                    .as("all worker threads should reach the start barrier")
                    .isTrue();

            // Release every thread at once — maximum race pressure.
            start.countDown();

            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS))
                    .as("all booking requests should finish within 60s")
                    .isTrue();

        } finally {
            pool.shutdownNow();
        }

        long created = statusCodes.stream().filter(code -> code == 201).count();
        long conflict = statusCodes.stream().filter(code -> code == 409).count();

        assertThat(unexpectedErrors.get())
                .as("no 500s or transport errors expected")
                .isZero();
        assertThat(statusCodes)
                .as("every thread should record an HTTP status")
                .hasSize(CONCURRENT_REQUESTS);
        assertThat(created)
                .as("exactly one concurrent booking should win")
                .isEqualTo(1);
        assertThat(conflict)
                .as("all other concurrent attempts should be rejected")
                .isEqualTo(CONCURRENT_REQUESTS - 1);

        long rowsInDb = bookingRepository.countByRoomIdAndCheckInDateAndCheckOutDate(
                ROOM_ID, checkIn, checkOut);
        assertThat(rowsInDb)
                .as("database must contain exactly one booking for the contested slot")
                .isEqualTo(1);
    }

    private int postBooking(int userId) {
        String body = """
                {
                  "userId": %d,
                  "roomId": %d,
                  "checkInDate": "%s",
                  "checkOutDate": "%s"
                }
                """.formatted(userId, ROOM_ID, checkIn, checkOut);

        return restClient.post()
                .uri("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, response) -> response.getStatusCode().value());
    }
}
