package com.hotelbooking.repository;

import com.hotelbooking.entity.Hotel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Persistence access for {@link Hotel}.
 * <p>
 * Spring Data JPA generates the implementation at runtime — you only declare
 * the interface. No {@code HotelRepositoryImpl} class is required for standard queries.
 */
public interface HotelRepository extends JpaRepository<Hotel, Long> {

    /**
     * Derived query: Spring parses the method name and generates SQL equivalent to
     * {@code SELECT * FROM hotels WHERE is_active = true}.
     */
    List<Hotel> findByActiveTrue();
}
