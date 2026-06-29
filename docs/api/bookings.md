# Bookings API Contract

> **Status:** Step 3 — booking workflow (first write endpoint).
> **Scope:** Create a booking. Cancel/list come in later slices.
> **Base URL:** `http://localhost:8080` (local dev)

---

## Conventions

| Topic | Choice |
|---|---|
| Base path | `/api/bookings` |
| Content-Type | `application/json` |
| Auth | None yet — `userId` is passed in the body for now (replaced by authenticated principal later) |
| Errors | JSON `ErrorResponse` (`timestamp`, `status`, `error`, `message`, `path`) |
| Dates | ISO `yyyy-MM-dd` (a civil calendar day, not a timestamp) |
| Money | Decimal with 2 places, currency assumed INR for now |

---

## Booking lifecycle (status)

```
PENDING ──payment success──▶ CONFIRMED ──stay ends──▶ COMPLETED
   │
   ├─ payment fails / abandoned ──▶ (stays PENDING, later expired)
   └─ user cancels ──▶ CANCELLED
```

`POST /bookings` always creates a booking in **`PENDING`**. A later payment step promotes it to `CONFIRMED`. This separation exists because payment talks to a slow external gateway and must not run inside the booking DB transaction.

---

## 1. Create a booking

```
POST /api/bookings
```

**Description:** Reserve a specific room for a date range. Validates the room and user, checks availability, snapshots the price, and inserts a `PENDING` booking.

### Request body: `CreateBookingRequest`

```json
{
  "userId": 1,
  "roomId": 3,
  "checkInDate": "2026-09-10",
  "checkOutDate": "2026-09-13"
}
```

| Field | Type | Rules |
|---|---|---|
| `userId` | number | required, must reference an existing active user |
| `roomId` | number | required, must reference an existing active room |
| `checkInDate` | string (date) | required, today or later |
| `checkOutDate` | string (date) | required, strictly after `checkInDate` |

**Cross-field rule:** `checkOutDate > checkInDate`. Check-out is exclusive (the checkout day's night is free for the next guest).

### Success response

| Status | Body |
|---|---|
| `201 Created` | `BookingResponse` |

```json
{
  "bookingId": 31,
  "bookingReference": "BK-2026-000031",
  "userId": 1,
  "roomId": 3,
  "hotelName": "The Taj Seaside",
  "checkInDate": "2026-09-10",
  "checkOutDate": "2026-09-13",
  "nights": 3,
  "pricePerNight": 3000.00,
  "totalAmount": 9000.00,
  "status": "PENDING",
  "createdAt": "2026-06-26T16:20:00Z"
}
```

### Error responses

| Status | When | `message` example |
|---|---|---|
| `400 Bad Request` | Missing field, bad date format | "checkOutDate must be after checkInDate" |
| `400 Bad Request` | `checkInDate` in the past | "checkInDate must not be in the past" |
| `404 Not Found` | Room does not exist or inactive | "Room not found with id: 999" |
| `404 Not Found` | User does not exist or inactive | "User not found with id: 999" |
| `409 Conflict` | Room already booked for overlapping dates | "Room 3 is not available for the selected dates" |

**Example — conflict**

```http
HTTP/1.1 409 Conflict
Content-Type: application/json

{
  "timestamp": "2026-06-26T16:20:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Room 3 is not available for the selected dates",
  "path": "/api/bookings"
}
```

---

## Availability rule (how "available" is decided)

A room is available for `[checkIn, checkOut)` if **no existing booking** for that room, with status in (`PENDING`, `CONFIRMED`, `COMPLETED`), overlaps the requested half-open range.

This is enforced at the database level by the `EXCLUDE USING gist` constraint on `bookings`. The service does not rely on a read-then-write check (which is race-prone); it attempts the insert and translates a constraint violation into `409 Conflict`. This guarantees correctness even under concurrent requests.

---

## Out of scope (this contract)

- `POST /api/bookings/{id}/cancel`
- `GET /api/bookings/{id}` and `GET /api/users/{id}/bookings`
- Payment (`POST /api/payments`) and the PENDING → CONFIRMED transition
- Multi-room bookings
- Authentication / deriving `userId` from a token

---

## Manual test commands

```bash
# Happy path (future dates, known room/user)
curl -s -X POST http://localhost:8080/api/bookings \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"roomId":3,"checkInDate":"2026-09-10","checkOutDate":"2026-09-13"}' | jq

# Conflict: book the same room/dates twice → second returns 409
# Bad request: checkOut before checkIn → 400
curl -s -w "\nHTTP %{http_code}\n" -X POST http://localhost:8080/api/bookings \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"roomId":3,"checkInDate":"2026-09-13","checkOutDate":"2026-09-10"}'

# Not found: unknown room → 404
curl -s -w "\nHTTP %{http_code}\n" -X POST http://localhost:8080/api/bookings \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"roomId":99999,"checkInDate":"2026-09-10","checkOutDate":"2026-09-13"}'
```
