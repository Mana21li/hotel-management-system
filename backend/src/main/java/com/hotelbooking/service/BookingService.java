package com.hotelbooking.service;

import com.hotelbooking.dto.request.CreateBookingRequest;
import com.hotelbooking.dto.response.BookingResponse;
import com.hotelbooking.entity.Booking;
import com.hotelbooking.entity.BookingStatus;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Business logic for creating bookings.
 * Contract: {@code docs/api/bookings.md}
 */
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;

    public BookingService(BookingRepository bookingRepository,
                          RoomRepository roomRepository,
                          UserRepository userRepository,
                          HotelRepository hotelRepository) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
    }

    /**
     * Creates a PENDING booking for a room over a date range.
     * <p>
     * Availability is enforced by the database {@code EXCLUDE} constraint: we attempt
     * the insert and translate an overlap violation into a 409, which is race-safe.
     */
    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        // 1. Cross-field date rule (field-level validation already ran in the controller).
        if (!request.checkOutDate().isAfter(request.checkInDate())) {
            throw new InvalidBookingDateException("checkOutDate must be after checkInDate");
        }

        // 2. Validate the user exists and is active (cheap, fail fast).
        User user = userRepository.findById(request.userId())
                .filter(User::isActive)
                .orElseThrow(() -> new UserNotFoundException(request.userId()));

        // 3. Validate the room exists and is active.
        Room room = roomRepository.findById(request.roomId())
                .filter(Room::isActive)
                .orElseThrow(() -> new RoomNotFoundException(request.roomId()));

        // 4. Snapshot the price at booking time (room price may change later).
        long nights = ChronoUnit.DAYS.between(request.checkInDate(), request.checkOutDate());
        BigDecimal pricePerNight = room.getNightlyPrice();
        BigDecimal totalAmount = pricePerNight.multiply(BigDecimal.valueOf(nights));

        // 5. Build the PENDING booking.
        Booking booking = Booking.builder()
                .bookingReference(generateBookingReference())
                .userId(user.getId())
                .roomId(room.getId())
                .checkInDate(request.checkInDate())
                .checkOutDate(request.checkOutDate())
                .pricePerNight(pricePerNight)
                .totalAmount(totalAmount)
                .status(BookingStatus.PENDING)
                .build();

        // 6. Race-safe insert. saveAndFlush forces the INSERT now so an overlap
        //    violation surfaces inside this try block (not later at commit).
        try {
            Booking saved = bookingRepository.saveAndFlush(booking);
            return toResponse(saved, room, (int) nights);
        } catch (DataIntegrityViolationException ex) {
            if (isOverlapViolation(ex)) {
                throw new RoomNotAvailableException(
                        room.getId(), request.checkInDate(), request.checkOutDate());
            }
            throw ex; // some other integrity problem — don't mislabel it
        }
    }

    private boolean isOverlapViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        String message = cause != null ? cause.getMessage() : null;
        return message != null && message.contains("no_overlap_booking");
    }

    private String generateBookingReference() {
        return "BK-" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
    }

    private BookingResponse toResponse(Booking booking, Room room, int nights) {
        String hotelName = hotelRepository.findById(room.getHotelId())
                .map(Hotel::getName)
                .orElse(null);

        return new BookingResponse(
                booking.getId(),
                booking.getBookingReference(),
                booking.getUserId(),
                booking.getRoomId(),
                hotelName,
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                nights,
                booking.getPricePerNight(),
                booking.getTotalAmount(),
                booking.getStatus().name(),
                booking.getCreatedAt()
        );
    }
}
