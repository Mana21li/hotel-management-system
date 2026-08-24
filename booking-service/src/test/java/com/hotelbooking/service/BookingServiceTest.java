package com.hotelbooking.service;

import com.hotelbooking.client.HotelCatalogClient;
import com.hotelbooking.client.HotelSummaryDto;
import com.hotelbooking.client.RoomCatalogDto;
import com.hotelbooking.client.UserClient;
import com.hotelbooking.client.UserSummaryDto;
import com.hotelbooking.dto.request.CreateBookingRequest;
import com.hotelbooking.dto.response.BookingResponse;
import com.hotelbooking.entity.Booking;
import com.hotelbooking.exception.InvalidBookingDateException;
import com.hotelbooking.exception.RoomNotAvailableException;
import com.hotelbooking.exception.RoomNotFoundException;
import com.hotelbooking.exception.UserNotFoundException;
import com.hotelbooking.outbox.OutboxService;
import com.hotelbooking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private UserClient userClient;
    @Mock
    private HotelCatalogClient hotelCatalogClient;
    @Mock
    private BookingLockService bookingLockService;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private OutboxService outboxService;

    private BookingService bookingService;

    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 10L;
    private static final Long HOTEL_ID = 100L;
    private static final LocalDate CHECK_IN = LocalDate.now().plusDays(1);
    private static final LocalDate CHECK_OUT = LocalDate.now().plusDays(3);

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        lenient().when(bookingLockService.executeWithRoomLock(any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(3, Supplier.class).get());

        bookingService = new BookingService(
                bookingRepository,
                userClient,
                hotelCatalogClient,
                bookingLockService,
                transactionManager,
                outboxService);
    }

    private CreateBookingRequest validRequest() {
        return new CreateBookingRequest(USER_ID, ROOM_ID, CHECK_IN, CHECK_OUT);
    }

    private UserSummaryDto activeUser() {
        return new UserSummaryDto(USER_ID, "Manali", true);
    }

    private RoomCatalogDto activeRoom() {
        return new RoomCatalogDto(ROOM_ID, HOTEL_ID, new BigDecimal("5000.00"), true);
    }

    @Test
    void createBooking_happyPath_snapshotsPriceAndReturnsPendingResponse() {
        when(userClient.getActiveUser(USER_ID)).thenReturn(activeUser());
        when(hotelCatalogClient.getRoom(ROOM_ID)).thenReturn(activeRoom());
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(hotelCatalogClient.getHotelSummary(HOTEL_ID))
                .thenReturn(new HotelSummaryDto(HOTEL_ID, "The Taj Seaside", true));

        BookingResponse response = bookingService.createBooking(validRequest());

        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.roomId()).isEqualTo(ROOM_ID);
        assertThat(response.hotelName()).isEqualTo("The Taj Seaside");
        assertThat(response.nights()).isEqualTo(2);
        assertThat(response.pricePerNight()).isEqualByComparingTo("5000.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("10000.00");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.bookingReference()).startsWith("BK-");

        verify(outboxService, org.mockito.Mockito.times(2)).enqueue(any(), any(), any(), any());
    }

    @Test
    void createBooking_throwsInvalidDate_whenCheckOutNotAfterCheckIn() {
        CreateBookingRequest sameDay =
                new CreateBookingRequest(USER_ID, ROOM_ID, CHECK_IN, CHECK_IN);

        assertThatThrownBy(() -> bookingService.createBooking(sameDay))
                .isInstanceOf(InvalidBookingDateException.class);

        verify(bookingLockService, never()).executeWithRoomLock(any(), any(), any(), any());
    }

    @Test
    void createBooking_throwsUserNotFound_whenUserMissing() {
        when(userClient.getActiveUser(USER_ID)).thenThrow(new UserNotFoundException(USER_ID));

        assertThatThrownBy(() -> bookingService.createBooking(validRequest()))
                .isInstanceOf(UserNotFoundException.class);

        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void createBooking_throwsRoomNotFound_whenRoomInactive() {
        when(userClient.getActiveUser(USER_ID)).thenReturn(activeUser());
        when(hotelCatalogClient.getRoom(ROOM_ID))
                .thenReturn(new RoomCatalogDto(ROOM_ID, HOTEL_ID, new BigDecimal("5000.00"), false));

        assertThatThrownBy(() -> bookingService.createBooking(validRequest()))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void createBooking_throwsRoomNotAvailable_whenOverlapConstraintViolated() {
        when(userClient.getActiveUser(USER_ID)).thenReturn(activeUser());
        when(hotelCatalogClient.getRoom(ROOM_ID)).thenReturn(activeRoom());
        DataIntegrityViolationException overlap = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: conflicting key value violates exclusion "
                        + "constraint \"no_overlap_booking\""));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenThrow(overlap);

        assertThatThrownBy(() -> bookingService.createBooking(validRequest()))
                .isInstanceOf(RoomNotAvailableException.class);

        verify(outboxService, never()).enqueue(any(), any(), any(), any());
    }
}
