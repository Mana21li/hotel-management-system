TRUNCATE TABLE users RESTART IDENTITY CASCADE;

INSERT INTO users (email, password_hash, full_name, phone) VALUES
    ('aarav.sharma@example.com',   '$2b$12$placeholderhashvalue000001', 'Aarav Sharma',   '+919800000001'),
    ('diya.patel@example.com',     '$2b$12$placeholderhashvalue000002', 'Diya Patel',     '+919800000002'),
    ('rohan.verma@example.com',    '$2b$12$placeholderhashvalue000003', 'Rohan Verma',    '+919800000003'),
    ('ananya.iyer@example.com',    '$2b$12$placeholderhashvalue000004', 'Ananya Iyer',    '+919800000004'),
    ('kabir.nair@example.com',     '$2b$12$placeholderhashvalue000005', 'Kabir Nair',     '+919800000005'),
    ('isha.reddy@example.com',     '$2b$12$placeholderhashvalue000006', 'Isha Reddy',     '+919800000006'),
    ('vivaan.singh@example.com',   '$2b$12$placeholderhashvalue000007', 'Vivaan Singh',   '+919800000007'),
    ('myra.gupta@example.com',     '$2b$12$placeholderhashvalue000008', 'Myra Gupta',     '+919800000008'),
    ('arjun.mehta@example.com',    '$2b$12$placeholderhashvalue000009', 'Arjun Mehta',    '+919800000009'),
    ('saanvi.joshi@example.com',   '$2b$12$placeholderhashvalue000010', 'Saanvi Joshi',   '+919800000010');
