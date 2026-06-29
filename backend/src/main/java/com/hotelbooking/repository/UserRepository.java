package com.hotelbooking.repository;

import com.hotelbooking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for {@link User}.
 * The booking flow only needs to load a user by id and check whether it is active.
 */
public interface UserRepository extends JpaRepository<User, Long> {
}
