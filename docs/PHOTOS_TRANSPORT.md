# Photos transport destination policy

Reviewed against current official Google documentation on 2026-10-01:

- [Library API REST reference](https://developers.google.com/photos/library/reference/rest)
  lists `https://photoslibrary.googleapis.com` as its service endpoint.
- [Resumable upload guide](https://developers.google.com/photos/library/guides/resumable-uploads)
  starts sessions at `/v1/uploads` and illustrates the returned session URL on that
  same endpoint with an opaque query. Clients must retain that URL for later chunks
  and progress queries. Sessions expire after seven days.
- [Media upload guide](https://developers.google.com/photos/library/guides/upload-media)
  distinguishes byte upload from `mediaItems:batchCreate` and documents retrying
  transient failures.

## Application policy

Every authenticated Photos request must use HTTPS and the exact host
`photoslibrary.googleapis.com` (case-insensitive). Only the implicit HTTPS port or
explicit `:443` is accepted. Userinfo, fragments, alternate hosts, IP addresses,
trailing-dot hosts, and malformed/ambiguous authorities are rejected before the
connection factory is called. This is an application allowlist based on the
published endpoint, not a claim that Google promises its endpoint inventory can
never change.

A resumable URL additionally requires the literal `/v1/uploads` path and a
nonempty query. The query is opaque: its bytes, ordering, escaping, and extra
parameters are preserved; the client does not reconstruct it. Validation occurs
both on the initial `X-Goog-Upload-URL` and before every query/chunk, including URLs
loaded from the saved upload queue. Unexpected destinations fail closed with a
message that omits the full URL and query, which can contain a session capability.

The referenced Photos documentation lists no regional or alternate session hosts.
Do not infer one from a different Google API or authorize all `google.com` or
`googleapis.com` hosts. If Google documents another Photos upload destination,
review and explicitly add that destination with fixtures before supporting it.
An unexpected host will stop that upload; it must never be silently followed.

All authenticated Photos requests disable automatic HTTP redirects. A 3xx
response fails explicitly, even for a same-origin `Location`; no credentials,
upload token, or media body are replayed to a redirected destination. Photos
resumable queries use the documented 200 response and `X-Goog-Upload-*` headers;
a Drive-style 308 resume response is not a Photos session acknowledgement.
Connections are disconnected on successful requests and on transport, response,
header-validation, or JSON parsing failures.

## Regression coverage

`PhotosUploaderTransportTest` exercises malicious initial and persisted session
URLs, valid URLs with opaque queries and explicit default ports, 301/302/303/307/308
responses, a loopback cross-host redirect trap, partial-upload retry and server
confirmed resume offsets, chunk/finalize responses, and failure cleanup.
`PhotosUploaderJsonTransportTest` covers successful batch creation/readback and
redirect/IO/JSON failures. Existing production worker recovery fixtures now use
synthetic URLs on the documented service origin; the injected connections still
prevent external network calls.

Run the project's Play suite with:

```sh
./gradlew :app:testPlayDebugUnitTest
```

All new fixtures use synthetic tokens and bytes. The loopback test maps validated
service URLs to a local HTTP server only inside its injected test factory; it does
not weaken production validation or send requests to Google. This hardening does
not claim any previously reported live-account upload failed and does not change
backup proof, deletion gates, OAuth scopes, app permissions, or release signing.
