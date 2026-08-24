# Database initialization

Each service owns a **dedicated Postgres container**:

| Container | Database | Init scripts |
|---|---|---|
| `hms_hotel_db` | `hotel_catalog` | `db/hotel-catalog/init/*.sql` |
| `hms_booking_db` | `hotel_booking` | `db/booking/init/*.sql` |
| `hms_user_db` | `hotel_user` | `db/user/init/*.sql` |

Re-run from scratch:

```bash
docker compose down -v
docker compose up -d
```

Connect:

```bash
docker exec -it hms_hotel_db psql -U "$POSTGRES_USER" -d hotel_catalog
docker exec -it hms_booking_db psql -U "$POSTGRES_USER" -d hotel_booking
docker exec -it hms_user_db psql -U "$POSTGRES_USER" -d hotel_user
```

Legacy single-instance scripts: `db/legacy/monolith-init/` and `db/init/00-create-databases.sh`.
