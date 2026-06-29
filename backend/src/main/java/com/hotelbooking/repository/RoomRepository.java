package com.hotelbooking.repository;

import com.hotelbooking.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link Room}.
 * Standard CRUD from {@link JpaRepository} is enough for the booking flow:
 * we look a room up by id and check whether it is active.
 */
public interface RoomRepository extends JpaRepository<Room, Long> {
}
