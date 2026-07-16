# PDF Batch Studio Server

This bundle runs the versioned server image with PostgreSQL. Docker Compose is
required.

```bash
cp .env.example .env
```

Replace `PDF_BATCH_DATABASE_PASSWORD` in `.env` with a strong random value,
then start the service:

```bash
docker compose up -d
docker compose ps
```

Open <http://127.0.0.1:8080/>. Stop without deleting stored data with:

```bash
docker compose down
```

Use `docker compose down -v` only when the PostgreSQL and document volumes may
be permanently deleted. Review `SECURITY.md` in the source repository before
exposing the anonymous server outside a trusted network.
