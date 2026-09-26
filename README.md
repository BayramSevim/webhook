# Webhook Delivery Service

A webhook delivery platform built with Java 21 and Spring Boot. Tenants register endpoints for the event types they care about; when an event happens, the service fans it out to every matching endpoint, delivers it over HTTP in the background, signs every request and keeps a full audit trail of every attempt.

## What problem does it solve?

Imagine a marketplace seller who runs a stock system and an accounting system outside the marketplace. When a customer places an order, both systems need to know about it right away. Instead of polling the marketplace every few minutes, they register a URL once and the marketplace **pushes** the event to them.

This service is that "push" layer:

1. A tenant subscribes: *"Send `order.created` events to `https://my-stock-app.com/hook`."*
2. The marketplace reports an event: *"Order 12345 was created for this tenant."*
3. The service finds every matching subscription and stores one delivery per subscription, then answers immediately.
4. A background worker picks up pending deliveries and sends them. Failed deliveries are retried with growing delays; after 5 failed attempts a delivery is marked `DEAD` and can be retried by hand.
5. Every attempt is logged: when it happened, how long it took, what the receiver answered.

## Status

| Phase | Scope | Status |
|---|---|---|
| 1.1 | Domain model, REST API, PostgreSQL + Flyway, validation, error handling, synchronous delivery | ✅ Done |
| 1.2 | Idempotency: rejecting duplicate requests | ✅ Done |
| 1.3 | Asynchronous delivery with the outbox pattern and a background worker | ✅ Done |
| 1.4 | Retries with exponential backoff + jitter, dead letter, manual retry | ✅ Done |
| 1.5 | HMAC signatures, per-subscriber circuit breaker | ✅ Done |
| 1.6 | Moving the worker to Kafka | ⏳ Next |
| 1.7 | Testcontainers, observability, load test | |

## Architecture (current)

```mermaid
flowchart LR
    P[Event producer] -->|POST /events| API[REST API]
    T[Tenant] -->|POST /subscriptions| API
    API -->|event + deliveries<br/>in one transaction| DB[(PostgreSQL<br/>deliveries = outbox)]
    W[Delivery worker<br/>every 5 s] -->|claim due rows<br/>FOR UPDATE SKIP LOCKED| DB
    W -->|HTTP POST + Webhook-Id| S[Subscriber endpoint]
    W -->|record result| DB
```

`POST /events` never talks to subscribers. It writes the event and its deliveries in a single transaction and returns `202 Accepted`. Sending is done by a scheduled worker, so a slow or broken subscriber can no longer slow down or break the API.

### How the worker sends a delivery

Each run of the worker handles up to 10 due deliveries in three steps. Only the first and the last step touch the database, and each one is a short transaction of its own:

```mermaid
sequenceDiagram
    participant W as Worker
    participant DB as PostgreSQL
    participant S as Subscriber
    W->>DB: ① claim: SELECT ... FOR UPDATE OF d SKIP LOCKED<br/>(skips subscribers whose circuit is open)<br/>status = SENDING, next_attempt_at = now + 2 min
    Note over W,DB: COMMIT, row lock released
    W->>S: ② signed HTTP POST (no transaction, no lock)
    S-->>W: 2xx / 4xx / 5xx / timeout
    W->>DB: ③ record: SUCCEEDED, FAILED (retry later) or DEAD + attempt row<br/>+ update the subscriber's circuit breaker
    Note over W,DB: COMMIT
```

Several instances of the service can run side by side: `SKIP LOCKED` makes each worker skip rows another worker is claiming, and the `SENDING` status keeps claimed rows out of everyone else's query after the lock is released.

## Data model

| Table | Holds | Example |
|---|---|---|
| `subscriptions` | Who wants which event types, at which URL, with which signing secret; also the subscriber's circuit breaker state | *Tenant `acme` wants `order.created` at `https://.../hook`* |
| `events` | Every event reported to the system, with its JSON payload | *Order 12345 was created* |
| `deliveries` | One row per (event, subscription) pair, its current state and when it is due. Also serves as the outbox. | *Order 12345 → stock app: `SUCCEEDED`* |
| `delivery_attempts` | One row per HTTP attempt: time, duration, status code, error | *Attempt 1: HTTP 500 after 175 ms* |

### Delivery states

```mermaid
stateDiagram-v2
    [*] --> PENDING: event reported
    PENDING --> SENDING: worker claims it
    SENDING --> SUCCEEDED: 2xx
    SENDING --> FAILED: error, attempts left
    SENDING --> DEAD: error, 5th attempt
    SENDING --> SENDING: lease expired,<br/>another worker reclaims it
    FAILED --> SENDING: retry time reached
    DEAD --> PENDING: POST /deliveries/{id}/retry
    SUCCEEDED --> [*]
```

`next_attempt_at` means "when the worker should look at this row next":

| State | `next_attempt_at` |
|---|---|
| `PENDING` | Creation time: due immediately |
| `SENDING` | End of the 2-minute lease. If the worker dies before recording a result, the row becomes due again after that. |
| `SUCCEEDED` | `NULL`: there is no next attempt |
| `FAILED` | Time of the next retry (see the retry schedule below) |
| `DEAD` | `NULL`: automatic retries are over |

### Retry schedule

A failed delivery is retried with exponential backoff: 10 s × 2^(attempt − 1), multiplied by a random factor between 0.8 and 1.2 (jitter). After the 5th failed attempt the delivery becomes `DEAD`.

| Failed attempt | Next retry after |
|---|---|
| 1 | ~10 s (8–12 s) |
| 2 | ~20 s (16–24 s) |
| 3 | ~40 s (32–48 s) |
| 4 | ~80 s (64–96 s) |
| 5 | `DEAD`, no more automatic retries |

The intervals are kept short so the whole chain can be watched in a few minutes; a production setup would spread them over hours.

### Circuit breaker

Each subscription has its own circuit breaker, stored in the database so that every worker instance sees the same state.

| Column | Meaning |
|---|---|
| `consecutive_failures` | Failed attempts in a row for this subscriber; reset to 0 by any success |
| `circuit_open_until` | `NULL` = closed (normal). Otherwise no delivery to this subscriber is claimed until this time |

After 5 failures in a row the circuit opens for 1 minute. While it is open, the claim query skips that subscriber's deliveries, so no requests are sent and **no retry attempts are used up**. When the minute is over, the next claimed delivery acts as a probe: a success closes the circuit, a failure opens it for another minute.

The schema is owned by Flyway migrations (`src/main/resources/db/migration`). Hibernate only validates it (`ddl-auto: validate`).

## API

| Method | Path | Description | Success |
|---|---|---|---|
| `POST` | `/subscriptions` | Register an endpoint for one or more event types | `201` + `Location` |
| `GET` | `/subscriptions/{id}` | Read a subscription (the secret is never returned) | `200` |
| `POST` | `/events` | Report an event. Deliveries are stored for every matching subscription and sent asynchronously by the worker. Requires an `Idempotency-Key` header. | `202` |
| `GET` | `/deliveries?eventId=...` | List the deliveries of an event and their states | `200` |
| `POST` | `/deliveries/{id}/retry` | Put a `DEAD` delivery back in the queue with a fresh set of 5 attempts. `404` if it does not exist, `409` if it is not `DEAD`. | `202` |

Errors follow [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457). Validation errors include a per-field `errors` list.

### Idempotent event reporting

`POST /events` requires an `Idempotency-Key` header (for example a UUID generated by the caller). Keys are scoped per tenant.

| Situation | Response |
|---|---|
| New key | The event is stored and `202` is returned. |
| Key already used, first request finished | The original response is returned again (`202`); no new event or deliveries are created. |
| Key already used, first request still in progress | `409 Conflict`. The caller should retry later with the same key and the same body. |

### Example

```bash
# 1. Subscribe
curl -i -X POST http://localhost:8080/subscriptions \
  -H "Content-Type: application/json" \
  -d '{"tenantId":"acme","url":"https://httpbin.org/post","secret":"super-secret-key-123","eventTypes":["order.created"]}'

# 2. Report an event (returns immediately with 202)
curl -i -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 7f1c2b9e-order-12345" \
  -d '{"tenantId":"acme","eventType":"order.created","payload":{"orderId":12345}}'

# 3. Check its deliveries a few seconds later (use the id returned in step 2)
curl -i "http://localhost:8080/deliveries?eventId=<EVENT_ID>"

# 4. If a delivery ended up DEAD (e.g. the receiver was down), send it again once the receiver is fixed
curl -i -X POST "http://localhost:8080/deliveries/<DELIVERY_ID>/retry"
```

Every webhook is sent with these headers:

| Header | Value |
|---|---|
| `Content-Type` | `application/json` |
| `Webhook-Id` | The delivery id. Identical across all attempts of the same delivery, so receivers can drop duplicates. |
| `Webhook-Event` | The event type, e.g. `order.created` |
| `Webhook-Timestamp` | Unix time in seconds when this attempt was sent. New on every attempt. |
| `Webhook-Signature` | `v1,<base64 HMAC-SHA256>`. New on every attempt. |

### Verifying a webhook

Requests are signed following the [Standard Webhooks](https://www.standardwebhooks.com/) layout, using the `secret` given when the subscription was created:

```
signed content = <Webhook-Id> + "." + <Webhook-Timestamp> + "." + <raw request body>
signature      = "v1," + base64( HMAC-SHA256(secret, signed content) )
```

A receiver should:

1. Compute the signature over the **raw** body bytes, before parsing the JSON. Re-serialising the JSON can change whitespace or key order and break the signature (the stored payload is `jsonb`, so it is sent as `{"orderId": 12345}`, with a space).
2. Compare it with `Webhook-Signature` using a constant-time comparison.
3. Reject requests whose `Webhook-Timestamp` is more than a few minutes old, to stop replayed requests.
4. Drop duplicates by `Webhook-Id`.

## Running locally

Requirements: Java 21, Docker.

```bash
docker compose up -d        # PostgreSQL 17 on localhost:5433
./mvnw spring-boot:run      # app on localhost:8080, Flyway migrates on startup
```

Health check: `GET http://localhost:8080/actuator/health`

To see several workers sharing the same queue, start a second instance on another port:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

## Design decisions

<!--
  BU BÖLÜMÜ SEN YAZACAKSIN.
  Her başlığın altına 2-4 cümle. Kendi kelimelerinle, mülakatta nasıl anlatacaksan öyle.
  Türkçe yazıp sonra birlikte İngilizceye çevirebiliriz.
  Her başlıktaki yorum satırı sana ne yazman gerektiğini hatırlatıyor; bitince yorumları sil.
-->

### Why separate `events`, `deliveries` and `delivery_attempts` tables?

<!-- Bir olay birden fazla aboneye gidiyor ve her birinin sonucu farklı olabiliyor. Sayaç yerine deneme tablosu tutmak neyi kazandırıyor? (Ayşe "neden gelmedi?" diye sorunca ne gösteriyorsun?) -->

### Why UUIDs for deliveries but `bigint` for attempts?

<!-- Karar kriteri: ID dışarı çıkıyor mu? Delivery ID'si nerelerde görünüyor, attempt ID'si nerede? -->

### Delivery guarantee: at-least-once, not exactly-once

<!-- Timeout aldığında karşı taraf mesajı aldı mı bilemezsin. Bu yüzden ne seçtin? Webhook-Id neden her denemede aynı? -->

### Why `202 Accepted` for events but `201 Created` for subscriptions?

<!-- İş bitti mi, bitmedi mi? (Artık 202 daha da anlamlı: teslimat gerçekten sonra yapılıyor.) -->

### Fixing an N+1 query on the delivery list

<!-- SQL logunda ne gördün (kaç sorgu)? Sebebi neydi (LAZY)? Neyle çözdün, sonuç ne oldu? Alternatifler neydi? -->

### Idempotency: an application check plus a unique constraint

The service first looks up the `Idempotency-Key`; if it was already used, the stored response is returned. That check alone is not enough: when I sent the same key from two Postman tabs at the same time, both requests saw "not found" and both tried to insert. The `unique (tenant_id, idempotency_key)` constraint is the last line of defence and rejects the second insert.

When the constraint fires the API answers `409 Conflict`, not `500`. Nothing unexpected went wrong on the server; the same request is simply still being processed. The client should wait briefly and retry with the same key and the same body.

### Outbox: using the `deliveries` table instead of a separate outbox table

A database write can be rolled back, but an HTTP request that has already been sent cannot. If a webhook goes out and the transaction then rolls back, the subscriber has acted on an event that no longer exists on our side (the seller's stock went down, but we have no record of the order). So `POST /events` only commits the event and its deliveries, and a background worker sends them afterwards.

I did not add a separate outbox table: the delivery rows are written in the same transaction as the event, so either both exist or neither does. The `deliveries` table already gives the outbox guarantee; a second table would only duplicate the same information.

### `FOR UPDATE SKIP LOCKED`: several workers, no double sends

Running two instances showed the problem: the second worker could not see the first worker's uncommitted update, picked the same delivery 2 ms later and sent it again, so the subscriber received it twice. The unique constraint on `delivery_attempts` stopped the duplicate attempt row, but the HTTP request had already gone out.

With `FOR UPDATE SKIP LOCKED` each worker locks the rows it claims and other workers skip locked rows. `FOR UPDATE` alone would also prevent the double send, but the other workers would wait for the lock; `SKIP LOCKED` lets them move on to other rows instead.

### Short transactions: claim → send → record

Keeping the transaction open during the HTTP call would hold a database connection and a row lock for as long as the subscriber takes to answer; slow subscribers could exhaust the connection pool. So a delivery is handled in three steps: a short transaction to claim it (`SENDING`), the HTTP call with no transaction, and a short transaction to record the result. After the claim commits, the lock is gone and the `SENDING` status keeps the row out of other workers' queries.

The claim and record methods live in a separate `DeliveryStateService`, because `@Transactional` works through a Spring proxy and would be skipped on a call from within the same class. `poll()` itself is deliberately not `@Transactional`: otherwise the inner methods would join its transaction and the commit, and the lock release with it, would move to the very end.

### Lease: recovering deliveries stuck in `SENDING`

When a worker claims a delivery it sets the status to `SENDING` and writes the end of a 2-minute lease into `next_attempt_at`. If the worker dies before recording a result, the lease expires, the claim query picks the row up again and another worker sends it.

The lease is sized for the whole batch, not a single request: deliveries in a batch are sent one after another, so the worst case is 10 × 7 s (2 s connect + 5 s read timeout) = 70 s. A shorter lease would let another worker reclaim deliveries that are still waiting their turn and send them twice.

### Retries: exponential backoff with jitter

A failed delivery is not retried at a fixed interval but with growing delays (10 s, 20 s, 40 s, 80 s). If a subscriber is down, hammering it with requests makes it harder for it to recover; short outages are still caught by the first retries, while long ones get more and more breathing room.

Each delay is multiplied by a random factor between 0.8 and 1.2 (jitter). Without it, if 1,000 deliveries failed at the same moment because a subscriber went down, all 1,000 would be retried in the same second and could knock the recovering server over again (thundering herd).

I tested it with a subscriber that always answers `500`: the delivery was attempted 5 times, 17 s, 21 s, 48 s and 72 s apart, and then became `DEAD`. All 5 attempts are visible in `delivery_attempts`.

### Dead letter and manual retry

Retrying forever makes no sense when the problem is permanent, for example a wrong URL. After the 5th failed attempt the delivery becomes `DEAD`, which means "automatic retries are over, a human has to look at this". The row and all its attempts stay in the database, so the reason is easy to find.

Once the tenant has fixed the problem, `POST /deliveries/{id}/retry` puts the delivery back in the queue with a fresh set of 5 attempts. Only `DEAD` deliveries are accepted (`409 Conflict` otherwise): requeueing a `SUCCEEDED` delivery would make the receiver get the same event twice, and requeueing a `SENDING` one could let two workers send it at the same time. The endpoint answers `202 Accepted`, not `200`, because the delivery has only been queued; the worker sends it a few seconds later.

### Retry budget vs. attempt number

At first `attempt_count` did two jobs: it was the retry budget ("how many attempts are left?") and it was also used as the attempt number written to `delivery_attempts`. Adding manual retry exposed the problem. `requeue()` resets the counter to 0, so the next failed attempt would have been recorded as attempt number 1 again, which already existed. The `unique (delivery_id, attempt_number)` constraint would reject it, the result would never be recorded, the delivery would stay in `SENDING`, and every time the lease expired it would be sent again: an endless loop hitting the subscriber every 2 minutes.

The fix was to separate the two meanings. `attempt_count` is now only the retry budget and may be reset; the attempt number comes from the number of rows already in `delivery_attempts` for that delivery, plus one. After a manual retry the history simply continues with attempts 6, 7, 8…, which is exactly what the experiment showed.

### Signing webhooks with HMAC

<!-- (Faz 1.5 provasında dolduracağız) İmza olmasaydı saldırgan ne yapabilirdi? Neden Webhook-Id ve zaman damgası da imzalanıyor?
     Retry'da hangi header'lar değişiyor, neden? Alıcı tarafında doğrulama deneyi ve jsonb boşluk tuzağı. -->

### Circuit breaker per subscriber, stored in the database

<!-- (Faz 1.5 provasında dolduracağız) Neden abone başına? Neden hafızada değil veritabanında (iki instance, Resilience4j farkı)?
     Sigorta atıkken deneme hakları neden yanmıyor? Deney: 36 sn boyunca FAILED:3 FAILED:3 FAILED:2 değişmedi.
     FOR UPDATE OF d neden? -->

## Known limitations (to be addressed in the next phases)

- **Every error is retried, even ones that cannot fix themselves.** A `404` (wrong URL) or `400` (payload rejected) is retried 5 times just like a `500`. Only `5xx`, `429` and timeouts should be retried; other `4xx` responses should go straight to `DEAD`.
- **Nobody is told when a delivery dies.** `DEAD` deliveries can be found by querying and retried by hand, but there is no alert or listing endpoint for the tenant.
- **A delivery can be sent twice after a lease expires.** If a send takes longer than the 2-minute lease, another worker may reclaim and resend it; the late result is then recorded as an extra attempt. This is accepted under at-least-once delivery (receivers deduplicate by `Webhook-Id`); a fencing token on the claim would prevent the stale write.
- **N+1 when claiming.** The claim query is native (it needs `FOR UPDATE SKIP LOCKED`), so `@EntityGraph` cannot be used and each claimed delivery loads its subscription separately. Bounded by the batch size of 10; `hibernate.default_batch_fetch_size` would fold these into one `IN (...)` query.
- **Up to 5 seconds of delivery latency.** The worker polls on a fixed delay instead of being notified of new rows. → Phase 1.6 (Kafka).
- **Duplicate subscriptions are still possible.** Events are protected by the `Idempotency-Key` header, but creating the same subscription twice still creates two rows, so the receiver gets every notification twice.
- **A reused key with a different body is not detected.** The stored response is returned without comparing request bodies; this should become `422 Unprocessable Entity`.
- **Signing secrets are stored in plain text.** Anyone with read access to the database could sign fake webhooks. They should be encrypted at rest (or kept in a secret manager), and secret rotation is not supported yet.
- **The half-open probe is a whole batch.** When a circuit's minute is over, all due deliveries of that subscriber that fit in the batch are sent at once instead of a single probe request.
- **The failure counter can miss an increment.** Two workers recording failures for the same subscriber at the same time can both write the same value (lost update). The circuit then opens one failure later; an atomic `UPDATE ... SET consecutive_failures = consecutive_failures + 1` or `@Version` would make it exact.