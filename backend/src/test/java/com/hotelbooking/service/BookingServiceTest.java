package com.hotelbooking.service;

import com.hotelbooking.dto.request.CreateBookingRequest;
import com.hotelbooking.dto.response.BookingResponse;
import com.hotelbooking.entity.Booking;
import com.hotelbooking.entity.Hotel;
import com.hotelbooking.entity.Room;
import com.hotelbooking.entity.User;
import com.hotelbooking.exception.InvalidBookingDateException;
import com.hotelbooking.exception.RoomNotAvailableException;
import com.hotelbooking.exception.RoomNotFoundException;
import com.hotelbooking.exception.UserNotFoundException;
import com.hotelbooking.repository.BookingRepository;
import com.hotelbooking.repository.HotelRepository;
import com.hotelbooking.repository.RoomRepository;
import com.hotelbooking.repository.UserRepository;
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
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit test for {@link BookingService}.
 * <p>
 * No Spring context, no database — every repository is mocked. We test ONLY the
 * service's own logic: the cross-field date rule, fail-fast user/room validation,
 * price snapshotting, and translating a DB overlap violation into a 409.
 * <p>
 * The actual no-double-booking guarantee lives in the database {@code EXCLUDE}
 * constraint and is exercised later in the concurrency-testing step against real
 * Postgres; here we only verify that <em>if</em> the DB rejects the insert with an
 * overlap, the service raises {@link RoomNotAvailableException}.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HotelRepository hotelRepository;
    @Mock
    private BookingLockService bookingLockService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private BookingService bookingService;

    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 10L;
    private static final Long HOTEL_ID = 100L;
    private static final LocalDate CHECK_IN = LocalDate.now().plusDays(1);
    private static final LocalDate CHECK_OUT = LocalDate.now().plusDays(3); // 2 nights

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        lenient().when(bookingLockService.executeWithRoomLock(any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(3, Supplier.class).get());

        bookingService = new BookingService(
                bookingRepository,
                roomRepository,
                userRepository,
                hotelRepository,
                bookingLockService,
                transactionManager);
    }

    private CreateBookingRequest validRequest() {
        return new CreateBookingRequest(USER_ID, ROOM_ID, CHECK_IN, CHECK_OUT);
    }

    private User activeUser() {
        return User.builder().id(USER_ID).fullName("Manali").active(true).build();
    }

    private Room activeRoom() {
        return Room.builder()
                .id(ROOM_ID)
                .hotelId(HOTEL_ID)
                .roomTypeId(1L)
                .roomNumber("101")
                .nightlyPrice(new BigDecimal("5000.00"))
                .active(true)
                .build();
    }

    @Test
    void createBooking_happyPath_snapshotsPriceAndReturnsPendingResponse() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(activeRoom()));
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(hotelRepository.findById(HOTEL_ID))
                .thenReturn(Optional.of(Hotel.builder().id(HOTEL_ID).name("The Taj Seaside").build()));

        BookingResponse response = bookingService.createBooking(validRequest());

        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.roomId()).isEqualTo(ROOM_ID);
        assertThat(response.hotelName()).isEqualTo("The Taj Seaside");
        assertThat(response.nights()).isEqualTo(2);
        assertThat(response.pricePerNight()).isEqualByComparingTo("5000.00");
        // total = nights (2) * pricePerNight (5000) = 10000
        assertThat(response.totalAmount()).isEqualByComparingTo("10000.00");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.bookingReference()).startsWith("BK-");
        assertThat(response.checkInDate()).isEqualTo(CHECK_IN);
        assertThat(response.checkOutDate()).isEqualTo(CHECK_OUT);
    }

    @Test
    void createBooking_throwsInvalidDate_whenCheckOutNotAfterCheckIn() {
        CreateBookingRequest sameDay =
                new CreateBookingRequest(USER_ID, ROOM_ID, CHECK_IN, CHECK_IN);

        assertThatThrownBy(() -> bookingService.createBooking(sameDay))
                .isInstanceOf(InvalidBookingDateException.class);

        verify(bookingLockService, never()).executeWithRoomLock(any(), any(), any(), any());
        verify(userRepository, never()).findById(any());
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void createBooking_throwsUserNotFound_whenUserMissing() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(validRequest()))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(USER_ID.toString());

        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void createBooking_throwsUserNotFound_whenUserInactive() {
        User inactive = User.builder().id(USER_ID).fullName("Inactive").active(false).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> bookingService.createBooking(validRequest()))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void createBooking_throwsRoomNotFound_whenRoomMissing() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(validRequest()))
                .isInstanceOf(RoomNotFoundException.class)
                .hasMessageContaining(ROOM_ID.toString());

        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void createBooking_throwsRoomNotFound_whenRoomInactive() {
        Room inactive = Room.builder()
                .id(ROOM_ID).hotelId(HOTEL_ID).roomTypeId(1L).roomNumber("101")
                .nightlyPrice(new BigDecimal("5000.00")).active(false).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> bookingService.createBooking(validRequest()))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void createBooking_throwsRoomNotAvailable_whenOverlapConstraintViolated() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(activeRoom()));
        // Simulate the Postgres EXCLUDE constraint rejecting an overlapping insert.
        DataIntegrityViolationException overlap = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: conflicting key value violates exclusion "
                        + "constraint \"no_overlap_booking\""));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenThrow(overlap);

        assertThatThrownBy(() -> bookingService.createBooking(validRequest()))
                .isInstanceOf(RoomNotAvailableException.class)
                .hasMessageContaining(ROOM_ID.toString());
    }

    @Test
    void createBooking_rethrowsOriginal_whenIntegrityViolationIsNotOverlap() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(activeRoom()));
        // A different integrity problem must NOT be mislabeled as a 409 availability conflict.
        DataIntegrityViolationException other = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: null value in column \"price_per_night\""));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenThrow(other);

        assertThatThrownBy(() -> bookingService.createBooking(validRequest()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(RoomNotAvailableException.class);
    }
}
