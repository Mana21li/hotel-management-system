TRUNCATE TABLE
    rooms, hotels, room_types, cities, countries
    RESTART IDENTITY CASCADE;

-- ----------------------------------------------------------------------------
-- countries (2)
-- ----------------------------------------------------------------------------
INSERT INTO countries (name, iso_code) VALUES
    ('India', 'IN'),          -- country_id = 1
    ('United States', 'US');  -- country_id = 2

-- ----------------------------------------------------------------------------
-- cities (5)
-- ----------------------------------------------------------------------------
INSERT INTO cities (country_id, name) VALUES
    (1, 'Mumbai'),         -- city_id = 1
    (1, 'Delhi'),          -- city_id = 2
    (1, 'Bangalore'),      -- city_id = 3
    (2, 'New York'),       -- city_id = 4
    (2, 'San Francisco');  -- city_id = 5

-- ----------------------------------------------------------------------------
-- room_types (3)
-- ----------------------------------------------------------------------------
INSERT INTO room_types (code, name, description, max_occupancy, base_price) VALUES
    ('STANDARD', 'Standard', 'Comfortable room with the essentials.',        2, 2500.00),  -- 1
    ('DELUXE',   'Deluxe',   'Spacious room with premium amenities.',        3, 4500.00),  -- 2
    ('SUITE',    'Suite',    'Luxury suite with separate living area.',      4, 8000.00);  -- 3

-- ----------------------------------------------------------------------------
-- hotels (5)
-- ----------------------------------------------------------------------------
INSERT INTO hotels (name, description, address_line, city_id, star_rating) VALUES
    ('The Taj Seaside',    'Iconic 5-star beachfront luxury.',        'Marine Drive, Nariman Point', 1, 5),  -- 1 Mumbai
    ('Capital Grand',      'Business hotel in the heart of Delhi.',   '12 Connaught Place',          2, 4),  -- 2 Delhi
    ('Garden City Inn',    'Cozy stay amid Bangalore greenery.',      '88 MG Road',                  3, 4),  -- 3 Bangalore
    ('Manhattan Plaza',    'Upscale tower with skyline views.',       '500 5th Avenue',              4, 5),  -- 4 New York
    ('Bayview Suites',     'Modern suites near the waterfront.',      '21 Embarcadero',              5, 4); -- 5 San Francisco

-- ----------------------------------------------------------------------------
-- rooms (20) — 4 per hotel
-- ----------------------------------------------------------------------------
INSERT INTO rooms (hotel_id, room_type_id, room_number, floor, nightly_price) VALUES
    -- Hotel 1 — The Taj Seaside (Mumbai)
    (1, 3, '101', 1, 8000.00),   -- room 1  Suite
    (1, 2, '102', 1, 5000.00),   -- room 2  Deluxe
    (1, 1, '201', 2, 3000.00),   -- room 3  Standard
    (1, 1, '202', 2, 3000.00),   -- room 4  Standard
    -- Hotel 2 — Capital Grand (Delhi)
    (2, 2, '301', 3, 4500.00),   -- room 5  Deluxe
    (2, 2, '302', 3, 4500.00),   -- room 6  Deluxe
    (2, 1, '303', 3, 2500.00),   -- room 7  Standard
    (2, 3, '401', 4, 7000.00),   -- room 8  Suite
    -- Hotel 3 — Garden City Inn (Bangalore)
    (3, 1, '11',  1, 2200.00),   -- room 9  Standard
    (3, 2, '12',  1, 4000.00),   -- room 10 Deluxe
    (3, 1, '13',  1, 2200.00),   -- room 11 Standard
    (3, 3, '21',  2, 6500.00),   -- room 12 Suite
    -- Hotel 4 — Manhattan Plaza (New York)
    (4, 2, '501', 5, 12000.00),  -- room 13 Deluxe
    (4, 3, '502', 5, 20000.00),  -- room 14 Suite
    (4, 1, '503', 5, 8000.00),   -- room 15 Standard
    (4, 1, '504', 5, 8000.00),   -- room 16 Standard
    -- Hotel 5 — Bayview Suites (San Francisco)
    (5, 3, '601', 6, 15000.00),  -- room 17 Suite
    (5, 2, '602', 6, 10000.00),  -- room 18 Deluxe
    (5, 1, '603', 6, 7000.00),   -- room 19 Standard
    (5, 1, '604', 6, 7000.00);   -- room 20 Standard
