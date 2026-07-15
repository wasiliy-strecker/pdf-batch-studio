# Upload and job lifecycle

1. `POST /api/v1/jobs` accepts multipart parts `template` and `data`.
2. The service assigns a random UUID and stores fixed internal filenames in an
   isolated directory. Original filenames are discarded.
3. PDF and CSV are inspected once. A valid upload becomes a `DRAFT` resource.
4. `PUT /api/v1/jobs/{id}/configuration` stores mappings and moves it to
   `READY`.
5. `POST /api/v1/jobs/{id}/preview` reuses the stored files and first CSV row.
6. `POST /api/v1/jobs/{id}/process` queues the stored job; clients poll
   `GET /api/v1/jobs/{id}`.
7. `GET /api/v1/jobs/{id}/result` streams the completed ZIP.
8. `DELETE /api/v1/jobs/{id}` removes metadata and files for a non-active job.
9. Scheduled cleanup removes non-active jobs after the configured retention.

Files are never required to be uploaded again for preview or processing.
Cancellation is requested through `POST /api/v1/jobs/{id}/cancel` and checked
between rows and before packaging.

The generated OpenAPI document is available at `/v3/api-docs` while the service
is running. Problem responses use RFC 9457-style `ProblemDetail` JSON with a
stable `code` property.
