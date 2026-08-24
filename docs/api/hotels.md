# Hotels API Contract

> **Status:** Step 2 — first vertical slice (read-only).  
> **Scope:** List and fetch hotels. No create/update/delete yet.  
> **Base URL:** `http://localhost:8080` (local dev)
> **Owner:** hotel-service. The `:8080` strangler proxies hotel reads.

---

## Conventions

| Topic | Choice |
|---|---|
| Base path | `/api/hotels` |
| Content-Type | `application/json` |
| Auth | None for now (added in a later step) |
| Errors | JSON body with `timestamp`, `status`, `error`, `message` (via global handler) |
| IDs | `Long` (maps to `hotels.hotel_id`) |

---

## Response shape: `HotelResponse`

Used by all hotel read endpoints.

```json
{
  "id": 1,
  "name": "The Taj Seaside",
  "description": "Iconic 5-star beachfront luxury.",
  "addressLine": "Marine Drive, Nariman Point",
  "starRating": 5
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| `id` | number | yes | Primary key |
| `name` | string | yes | Max 200 chars in DB |
| `description` | string | no | Nullable in DB |
| `addressLine` | string | yes | Street address |
| `starRating` | number | yes | Integer 1–5 |

**Intentionally omitted** (internal / future): `cityId`, `cityName`, `isActive`, `createdAt`, `updatedAt`.

---

## Endpoints

### 1. List active hotels

```
GET /api/hotels
```

**Description:** Returns all hotels where `is_active = true`, ordered by name ascending.

**Request:** No body. No query parameters (filters added later).

**Success response**

| Status | Body |
|---|---|
| `200 OK` | `HotelResponse[]` |

**Example**

```http
GET /api/hotels HTTP/1.1
Host: localhost:8080
```

```json
[
  {
    "id": 1,
    "name": "The Taj Seaside",
    "description": "Iconic 5-star beachfront luxury.",
    "addressLine": "Marine Drive, Nariman Point",
    "starRating": 5
  },
  {
    "id": 2,
    "name": "Capital Grand",
    "description": "Business hotel in the heart of Delhi.",
    "addressLine": "12 Connaught Place",
    "starRating": 4
  }
]
```

**Empty list:** `200 OK` with `[]` (not 404).

---

### 2. Get hotel by ID

```
GET /api/hotels/{id}
```

**Description:** Returns a single active hotel. Planned for the slice immediately after list (Step 2.11+).

**Path parameters**

| Name | Type | Description |
|---|---|---|
| `id` | number | `hotel_id` |

**Success response**

| Status | Body |
|---|---|
| `200 OK` | `HotelResponse` |

**Error responses**

| Status | When |
|---|---|
| `404 Not Found` | Hotel does not exist, or `is_active = false` |

**Example (success)**

```http
GET /api/hotels/1 HTTP/1.1
```

```json
{
  "id": 1,
  "name": "The Taj Seaside",
  "description": "Iconic 5-star beachfront luxury.",
  "addressLine": "Marine Drive, Nariman Point",
  "starRating": 5
}
```

**Example (not found)**

```http
HTTP/1.1 404 Not Found
Content-Type: application/json

{
  "timestamp": "2026-06-25T12:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Hotel not found with id: 999"
}
```

---

## Out of scope (this contract)

- `POST /api/hotels` (admin create)
- `PUT` / `PATCH` / `DELETE`
- Search by city, star rating, or text
- Pagination (`page`, `size`)
- `cityName` in response (requires join with `cities` — later enhancement)

---

## Implementation checklist

Public URLs stay on `:8080`. Catalog reads live in `hotel-service`.

| Layer | Class | Where | Responsibility |
|---|---|---|---|
| Entity | `Hotel` | hotel-service | Maps `hotels` table |
| Repository | `HotelRepository` | hotel-service | `findByActiveTrue()`, `findById()` |
| DTO | `HotelResponse` | both (`backend` copies the JSON shape) | API response shape |
| Service | `HotelService` | hotel-service | Fetch + map entity → DTO; Redis cache |
| Gateway | `HotelServiceGateway` | backend (`:8080`) | RestClient proxy |
| Controller | `HotelController` | both (same path) | HTTP mapping |
| Exception | `GlobalExceptionHandler` | both | 404 for missing hotel |

---

## Manual test commands

```bash
# List hotels (after controller is wired)
curl -s http://localhost:8080/api/hotels | jq

# Get one hotel
curl -s http://localhost:8080/api/hotels/1 | jq

# Not found
curl -s -w "\nHTTP %{http_code}\n" http://localhost:8080/api/hotels/999
```
