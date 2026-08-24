package com.hotelbooking.hotel.service;

import com.hotelbooking.hotel.dto.HotelSearchProjectionResponse;
import com.hotelbooking.hotel.dto.HotelSummaryResponse;
import com.hotelbooking.hotel.dto.RoomCatalogResponse;
import com.hotelbooking.hotel.exception.HotelNotFoundException;
import com.hotelbooking.hotel.exception.RoomNotFoundException;
import com.hotelbooking.hotel.repository.HotelRepository;
import com.hotelbooking.hotel.repository.RoomRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Internal catalog reads for other services after DB-per-service split.
 * hotel_catalog is the only database that owns hotels / rooms / cities.
 */
@Service
@Transactional(readOnly = true)
public class CatalogProjectionService {

    private static final String INDEXING_SQL = """
            SELECT
                h.hotel_id,
                h.name,
                h.description,
                h.address_line,
                h.city_id,
                c.name AS city_name,
                h.star_rating,
                h.is_active,
                h.created_at,
                h.updated_at,
                COALESCE(MIN(r.nightly_price), 0) AS min_nightly_price
            FROM hotels h
            JOIN cities c ON c.city_id = h.city_id
            LEFT JOIN rooms r ON r.hotel_id = h.hotel_id AND r.is_active = TRUE
            GROUP BY
                h.hotel_id, h.name, h.description, h.address_line, h.city_id, c.name,
                h.star_rating, h.is_active, h.created_at, h.updated_at
            ORDER BY h.hotel_id
            """;

    private static final String INDEXING_SQL_BY_ID = """
            SELECT
                h.hotel_id,
                h.name,
                h.description,
                h.address_line,
                h.city_id,
                c.name AS city_name,
                h.star_rating,
                h.is_active,
                h.created_at,
                h.updated_at,
                COALESCE(MIN(r.nightly_price), 0) AS min_nightly_price
            FROM hotels h
            JOIN cities c ON c.city_id = h.city_id
            LEFT JOIN rooms r ON r.hotel_id = h.hotel_id AND r.is_active = TRUE
            WHERE h.hotel_id = ?
            GROUP BY
                h.hotel_id, h.name, h.description, h.address_line, h.city_id, c.name,
                h.star_rating, h.is_active, h.created_at, h.updated_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;

    public CatalogProjectionService(
            JdbcTemplate jdbcTemplate,
            RoomRepository roomRepository,
            HotelRepository hotelRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.roomRepository = roomRepository;
        this.hotelRepository = hotelRepository;
    }

    public RoomCatalogResponse getRoom(Long roomId) {
        return roomRepository.findById(roomId)
                .map(room -> new RoomCatalogResponse(
                        room.getId(),
                        room.getHotelId(),
                        room.getNightlyPrice(),
                        room.isActive()))
                .orElseThrow(() -> new RoomNotFoundException(roomId));
    }

    public HotelSummaryResponse getHotelSummary(Long hotelId) {
        return hotelRepository.findById(hotelId)
                .map(hotel -> new HotelSummaryResponse(hotel.getId(), hotel.getName(), hotel.isActive()))
                .orElseThrow(() -> new HotelNotFoundException(hotelId));
    }

    public boolean hotelExists(Long hotelId) {
        return hotelRepository.existsById(hotelId);
    }

    public List<HotelSearchProjectionResponse> listSearchProjections() {
        return jdbcTemplate.query(INDEXING_SQL, this::mapProjection);
    }

    public HotelSearchProjectionResponse getSearchProjection(Long hotelId) {
        List<HotelSearchProjectionResponse> rows =
                jdbcTemplate.query(INDEXING_SQL_BY_ID, this::mapProjection, hotelId);
        if (rows.isEmpty()) {
            throw new HotelNotFoundException(hotelId);
        }
        return rows.getFirst();
    }

    private HotelSearchProjectionResponse mapProjection(ResultSet rs, int rowNum) throws SQLException {
        return new HotelSearchProjectionResponse(
                rs.getLong("hotel_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("address_line"),
                rs.getLong("city_id"),
                rs.getString("city_name"),
                rs.getInt("star_rating"),
                rs.getDouble("min_nightly_price"),
                rs.getBoolean("is_active"),
                toIsoString(rs.getTimestamp("created_at")),
                toIsoString(rs.getTimestamp("updated_at")));
    }

    private static String toIsoString(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant().toString() : null;
    }
}
