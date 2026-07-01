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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Business logic for creating bookings.
 * Contract: {@code docs/api/bookings.md}
 * <p>
 * Concurrency strategy (defense in depth):
 * <ol>
 *   <li><strong>Redis distributed lock</strong> — only one booking attempt per
 *       room+dates at a time; fail fast without hammering the DB.</li>
 *   <li><strong>Database {@code EXCLUDE} constraint</strong> — final guarantee even
 *       if the lock is bypassed or Redis restarts.</li>
 * </ol>
 */
@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final HotelRepository hotelRepository;
    private final BookingLockService bookingLockService;
    private final TransactionTemplate transactionTemplate;

    public BookingService(BookingRepository bookingRepository,
                          RoomRepository roomRepository,
                          UserRepository userRepository,
                          HotelRepository hotelRepository,
                          BookingLockService bookingLockService,
                          PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.hotelRepository = hotelRepository;
        this.bookingLockService = bookingLockService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Creates a PENDING booking for a room over a date range.
     * <p>
     * Order matters: acquire the distributed lock <em>before</em> opening a DB
     * transaction. If {@code @Transactional} wrapped the whole method including the
     * lock call, two threads could both start transactions before either got the lock.
     */
    public BookingResponse createBooking(CreateBookingRequest request) {
        if (!request.checkOutDate().isAfter(request.checkInDate())) {
            throw new InvalidBookingDateException("checkOutDate must be after checkInDate");
        }

        return bookingLockService.executeWithRoomLock(
                request.roomId(),
                request.checkInDate(),
                request.checkOutDate(),
                () -> transactionTemplate.execute(status -> createBookingInTransaction(request))
        );
    }

    private BookingResponse createBookingInTransaction(CreateBookingRequest request) {
        User user = userRepository.findById(request.userId())
                .filter(User::isActive)
                .orElseThrow(() -> new UserNotFoundException(request.userId()));

        Room room = roomRepository.findById(request.roomId())
                .filter(Room::isActive)
                .orElseThrow(() -> new RoomNotFoundException(request.roomId()));

        long nights = ChronoUnit.DAYS.between(request.checkInDate(), request.checkOutDate());
        BigDecimal pricePerNight = room.getNightlyPrice();
        BigDecimal totalAmount = pricePerNight.multiply(BigDecimal.valueOf(nights));

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

        try {
            Booking saved = bookingRepository.saveAndFlush(booking);
            return toResponse(saved, room, (int) nights);
        } catch (DataIntegrityViolationException ex) {
            if (isOverlapViolation(ex)) {
                throw new RoomNotAvailableException(
                        room.getId(), request.checkInDate(), request.checkOutDate());
            }
            throw ex;
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
