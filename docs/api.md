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
resources, request schemas, pagination parameters, and status codes. The
statuses this API actually returns are:

| Status | Meaning |
|---|---|
| `200` | the request succeeded and the body carries the resource |
| `201` | a resource was created; the body carries it |
| `202` | the work was accepted and runs asynchronously - a regrade |
| `400` | the request was malformed, or the domain refused an argument |
| `401` | the request was not authenticated |
| `403` | the caller is authenticated but not permitted |
| `404` | no such resource, and no hint that it might exist elsewhere |
| `409` | the resource exists but is in a state that refuses this operation |
| `429` | a rate limit was reached - registration, result lookups, regrades |
| `500` | an unexpected failure; the detail is deliberately generic |

`204` is not used: an operation that succeeds returns the resource it acted on.
