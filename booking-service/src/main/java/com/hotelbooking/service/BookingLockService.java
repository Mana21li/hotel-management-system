package com.hotelbooking.service;

import com.hotelbooking.exception.RoomNotAvailableException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Distributed lock around the booking critical section using Redisson.
 * <p>
 * Only one request at a time may attempt to book the same room for the same date
 * range across all app instances. This is an <em>advisory</em> optimization layer:
 * the database {@code EXCLUDE} constraint remains the final correctness guarantee.
 */
@Service
public class BookingLockService {

  /** Redis key prefix — namespaces booking locks away from cache keys. */
  private static final String LOCK_PREFIX = "booking:room:";

  private final RedissonClient redissonClient;
  private final long waitSeconds;
  private final long leaseSeconds;

  public BookingLockService(
      RedissonClient redissonClient,
      @Value("${booking.lock.wait-seconds:3}") long waitSeconds,
      @Value("${booking.lock.lease-seconds:30}") long leaseSeconds) {
    this.redissonClient = redissonClient;
    this.waitSeconds = waitSeconds;
    this.leaseSeconds = leaseSeconds;
  }

  /**
   * Runs {@code action} while holding a distributed lock for the given room + dates.
   * <p>
   * Lock key example: {@code booking:room:3:2026-07-10:2026-07-13}
   *
   * @throws RoomNotAvailableException if the lock cannot be acquired within the wait
   *     window (another booking attempt is in progress for this room/dates)
   */
  public <T> T executeWithRoomLock(
      Long roomId, LocalDate checkIn, LocalDate checkOut, Supplier<T> action) {

    String lockKey = buildLockKey(roomId, checkIn, checkOut);
    RLock lock = redissonClient.getLock(lockKey);
    boolean acquired = false;

    try {
      // tryLock(wait, lease, unit):
      //   wait  = how long THIS thread will wait for the lock (seconds)
      //   lease = auto-expire if holder crashes (TTL safety net)
      // Returns false if wait time expires — we map that to 409 Conflict.
      acquired = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);

      if (!acquired) {
        throw new RoomNotAvailableException(roomId, checkIn, checkOut);
      }

      return action.get();

    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for booking lock: " + lockKey, ex);

    } finally {
      // Only the thread that acquired the lock may release it (Redisson enforces ownership).
      if (acquired && lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
  }

  /** Visible for tests and logging — encodes room + date range into a stable key. */
  static String buildLockKey(Long roomId, LocalDate checkIn, LocalDate checkOut) {
    return LOCK_PREFIX + roomId + ":" + checkIn + ":" + checkOut;
  }
}
