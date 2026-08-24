package com.hotelbooking.service;

import com.hotelbooking.client.HotelCatalogClient;
import com.hotelbooking.client.HotelSummaryDto;
import com.hotelbooking.client.RoomCatalogDto;
import com.hotelbooking.client.UserClient;
import com.hotelbooking.client.UserSummaryDto;
import com.hotelbooking.dto.request.CreateBookingRequest;
import com.hotelbooking.dto.response.BookingResponse;
import com.hotelbooking.entity.Booking;
import com.hotelbooking.entity.BookingStatus;
import com.hotelbooking.exception.InvalidBookingDateException;
import com.hotelbooking.exception.RoomNotAvailableException;
import com.hotelbooking.exception.RoomNotFoundException;
import com.hotelbooking.config.KafkaConfig;
import com.hotelbooking.kafka.event.BookingCreatedEvent;
import com.hotelbooking.kafka.event.HotelUpsertedEvent;
import com.hotelbooking.outbox.OutboxService;
import com.hotelbooking.repository.BookingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserClient userClient;
    private final HotelCatalogClient hotelCatalogClient;
    private final BookingLockService bookingLockService;
    private final TransactionTemplate transactionTemplate;
    private final OutboxService outboxService;

    public BookingService(BookingRepository bookingRepository,
                          UserClient userClient,
                          HotelCatalogClient hotelCatalogClient,
                          BookingLockService bookingLockService,
                          PlatformTransactionManager transactionManager,
                          OutboxService outboxService) {
        this.bookingRepository = bookingRepository;
        this.userClient = userClient;
        this.hotelCatalogClient = hotelCatalogClient;
        this.bookingLockService = bookingLockService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.outboxService = outboxService;
    }

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
        UserSummaryDto user = userClient.getActiveUser(request.userId());

        RoomCatalogDto room = hotelCatalogClient.getRoom(request.roomId());
        if (!room.active()) {
            throw new RoomNotFoundException(request.roomId());
        }

        long nights = ChronoUnit.DAYS.between(request.checkInDate(), request.checkOutDate());
        BigDecimal pricePerNight = room.nightlyPrice();
        BigDecimal totalAmount = pricePerNight.multiply(BigDecimal.valueOf(nights));

        Booking booking = Booking.builder()
                .bookingReference(generateBookingReference())
                .userId(user.userId())
                .roomId(room.roomId())
                .checkInDate(request.checkInDate())
                .checkOutDate(request.checkOutDate())
                .pricePerNight(pricePerNight)
                .totalAmount(totalAmount)
                .status(BookingStatus.PENDING)
                .build();

        try {
            Booking saved = bookingRepository.saveAndFlush(booking);
            HotelSummaryDto hotel = hotelCatalogClient.getHotelSummary(room.hotelId());
            BookingResponse response = toResponse(saved, room, hotel.name(), (int) nights);

            outboxService.enqueue(
                    KafkaConfig.BOOKING_EVENTS_TOPIC,
                    String.valueOf(response.roomId()),
                    BookingCreatedEvent.TYPE,
                    BookingCreatedEvent.from(response));
            outboxService.enqueue(
                    KafkaConfig.HOTEL_EVENTS_TOPIC,
                    String.valueOf(room.hotelId()),
                    HotelUpsertedEvent.TYPE,
                    HotelUpsertedEvent.of(room.hotelId()));

            return response;
        } catch (DataIntegrityViolationException ex) {
            if (isOverlapViolation(ex)) {
                throw new RoomNotAvailableException(
                        room.roomId(), request.checkInDate(), request.checkOutDate());
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

    private BookingResponse toResponse(
            Booking booking, RoomCatalogDto room, String hotelName, int nights) {
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
