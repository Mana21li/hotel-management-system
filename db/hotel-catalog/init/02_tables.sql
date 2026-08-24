-- hotel_catalog — countries → rooms (catalog truth owned by hotel-service).
CREATE TABLE IF NOT EXISTS countries (
    country_id   BIGSERIAL    PRIMARY KEY,
    name         VARCHAR(100) NOT NULL,
    iso_code     CHAR(2)      NOT NULL,
    CONSTRAINT uq_countries_iso_code UNIQUE (iso_code),
    CONSTRAINT uq_countries_name     UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS cities (
    city_id      BIGSERIAL    PRIMARY KEY,
    country_id   BIGINT       NOT NULL,
    name         VARCHAR(120) NOT NULL,
    CONSTRAINT fk_cities_country
        FOREIGN KEY (country_id) REFERENCES countries (country_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uq_cities_country_name UNIQUE (country_id, name)
);

CREATE TABLE IF NOT EXISTS room_types (
    room_type_id    BIGSERIAL      PRIMARY KEY,
    code            VARCHAR(30)    NOT NULL,
    name            VARCHAR(50)    NOT NULL,
    description     TEXT,
    max_occupancy   SMALLINT       NOT NULL,
    base_price      NUMERIC(10, 2) NOT NULL,
    CONSTRAINT uq_room_types_code        UNIQUE (code),
    CONSTRAINT chk_room_types_occupancy  CHECK (max_occupancy BETWEEN 1 AND 10),
    CONSTRAINT chk_room_types_base_price CHECK (base_price >= 0)
);

CREATE TABLE IF NOT EXISTS hotels (
    hotel_id      BIGSERIAL    PRIMARY KEY,
    name          VARCHAR(200) NOT NULL,
    description   TEXT,
    address_line  VARCHAR(255) NOT NULL,
    city_id       BIGINT       NOT NULL,
    star_rating   SMALLINT     NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_hotels_city
        FOREIGN KEY (city_id) REFERENCES cities (city_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT chk_hotels_star_rating CHECK (star_rating BETWEEN 1 AND 5)
);

CREATE TABLE IF NOT EXISTS rooms (
    room_id        BIGSERIAL      PRIMARY KEY,
    hotel_id       BIGINT         NOT NULL,
    room_type_id   BIGINT         NOT NULL,
    room_number    VARCHAR(10)    NOT NULL,
    floor          SMALLINT,
    nightly_price  NUMERIC(10, 2) NOT NULL,
    is_active      BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT fk_rooms_hotel
        FOREIGN KEY (hotel_id) REFERENCES hotels (hotel_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT fk_rooms_type
        FOREIGN KEY (room_type_id) REFERENCES room_types (room_type_id)
        ON UPDATE CASCADE ON DELETE RESTRICT,
    CONSTRAINT uq_rooms_hotel_number   UNIQUE (hotel_id, room_number),
    CONSTRAINT chk_rooms_nightly_price CHECK (nightly_price >= 0)
);
