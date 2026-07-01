package com.hotelbooking.service;

import com.hotelbooking.exception.RoomNotAvailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BookingLockService}.
 * <p>
 * Redisson is mocked — we verify lock acquire/release behaviour and key design,
 * not a real Redis connection.
 */
@ExtendWith(MockitoExtension.class)
class BookingLockServiceTest {

    private static final Long ROOM_ID = 5L;
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 10, 1);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 10, 4);

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock lock;

    private BookingLockService bookingLockService;

    @BeforeEach
    void setUp() {
        bookingLockService = new BookingLockService(redissonClient, 3, 30);
        lenient().when(redissonClient.getLock(anyString())).thenReturn(lock);
    }

    @Test
    void buildLockKey_includesRoomIdAndDateRange() {
        assertThat(BookingLockService.buildLockKey(ROOM_ID, CHECK_IN, CHECK_OUT))
                .isEqualTo("booking:room:5:2026-10-01:2026-10-04");
    }

    @Test
    void executeWithRoomLock_runsActionAndUnlocksWhenAcquired() throws Exception {
        when(lock.tryLock(3, 30, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        String result = bookingLockService.executeWithRoomLock(
                ROOM_ID, CHECK_IN, CHECK_OUT, () -> "booked");

        assertThat(result).isEqualTo("booked");
        verify(redissonClient).getLock("booking:room:5:2026-10-01:2026-10-04");
        verify(lock).unlock();
    }

    @Test
    void executeWithRoomLock_throws409WhenLockNotAcquiredWithinWaitWindow() throws Exception {
        when(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);

        assertThatThrownBy(() -> bookingLockService.executeWithRoomLock(
                ROOM_ID, CHECK_IN, CHECK_OUT, () -> "should not run"))
                .isInstanceOf(RoomNotAvailableException.class)
                .hasMessageContaining("5");

        verify(lock, never()).unlock();
    }

    @Test
    void executeWithRoomLock_doesNotRunActionWhenLockFails() throws Exception {
        when(lock.tryLock(3, 30, TimeUnit.SECONDS)).thenReturn(false);
        AtomicBoolean actionRan = new AtomicBoolean(false);

        assertThatThrownBy(() -> bookingLockService.executeWithRoomLock(
                ROOM_ID, CHECK_IN, CHECK_OUT, () -> {
                    actionRan.set(true);
                    return null;
                }))
                .isInstanceOf(RoomNotAvailableException.class);

        assertThat(actionRan).isFalse();
    }

    @Test
    void executeWithRoomLock_unlocksEvenWhenActionThrows() throws Exception {
        when(lock.tryLock(3, 30, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        assertThatThrownBy(() -> bookingLockService.executeWithRoomLock(
                ROOM_ID, CHECK_IN, CHECK_OUT, () -> {
                    throw new RuntimeException("db error");
                }))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("db error");

        verify(lock).unlock();
    }
}
