# API conventions

The HTTP API is versioned below `/api/v1`. Requests and responses use JSON;
clients should send `Accept: application/json` and `Content-Type: application/json`
where a body is present.

Errors use the RFC 9457 `application/problem+json` representation. Clients
should branch on the HTTP status and problem `type`, not on a localized detail
message. Validation errors may include field-level detail.

List endpoints conventionally use pagination, filtering, and sorting query
parameters. Treat pagination as a bounded view rather than a stable export;
request an explicit export or use an administrative data procedure when a full
record is required. Validate each endpoint’s parameter names and sort fields in
the generated OpenAPI document rather than assuming a generic parameter applies
to every resource.

Authentication and authorization are enforced by the server according to the
configured identity mode and role/course membership. Do not rely on client-side
route visibility for authorization. Use HTTPS in production and do not expose
bearer or result tokens to third-party origins.

The generated API contract is served at `/api/v1/openapi`; Swagger UI is served
at `/api/v1/docs`. Those endpoints are the source of truth for implemented
resources, request schemas, pagination parameters, and status codes. Typical
status semantics are `200`/`201` for success, `204` for an empty successful
operation, `400` for malformed input, `401` for unauthenticated requests,
`403` for denied access, `404` for absent resources, `409` for a state conflict,
and `422` when a valid request cannot be processed under domain rules.
