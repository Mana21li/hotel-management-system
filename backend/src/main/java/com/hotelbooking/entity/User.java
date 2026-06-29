package com.hotelbooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA mapping for the {@code users} table.
 * <p>
 * This is a <strong>partial mapping</strong>: the booking domain only needs to
 * validate that a user exists and is active, so we map just those columns. The
 * {@code email} column is PostgreSQL {@code CITEXT} (a custom type) and {@code
 * password_hash} is sensitive — both are intentionally omitted here. A dedicated
 * auth/user module would map them with a proper {@code citext} type.
 * <p>
 * A JPA entity is not required to map every column of its table; unmapped columns
 * are simply ignored on read. (We never insert users from this application.)
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
