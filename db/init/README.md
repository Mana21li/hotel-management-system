# Database initialization scripts

Any `.sql` or `.sh` files placed in this folder are executed **automatically**
by the PostgreSQL container — but **only the first time** the database is
created (i.e. when the data volume is empty).

- Files run in **alphabetical order**, so name them with numeric prefixes:
  - `01_extensions_and_enums.sql`
  - `02_tables.sql`
  - `03_indexes.sql`
  - `04_triggers.sql`
  - `05_seed_data.sql`
- To re-run them from scratch, wipe the volume and start fresh:

```bash
docker compose down -v
docker compose up -d
```

> Note: this folder is mounted read-only into the container at
> `/docker-entrypoint-initdb.d`. We'll generate the actual `.sql` files here in
> a later step, converting the DDL from `docs/database-schema-postgres.md` into
> runnable migration scripts.
