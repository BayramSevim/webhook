create table subscriptions (
                               id           uuid         primary key,
                               tenant_id    varchar(100) not null,
                               url          text         not null,
                               secret       text         not null,
                               event_types  text[]       not null,
                               active       boolean      not null default true,
                               created_at   timestamptz  not null default now(),
                               constraint chk_subscriptions_event_types_not_empty check (cardinality(event_types) > 0)
);

create index idx_subscriptions_event_types on subscriptions using gin (event_types);
create index idx_subscriptions_tenant_id   on subscriptions (tenant_id);

create table events (
                        id          uuid         primary key,
                        tenant_id   varchar(100) not null,
                        event_type  varchar(100) not null,
                        payload     jsonb        not null,
                        created_at  timestamptz  not null default now()
);

create table deliveries (
                            id               uuid        primary key,
                            event_id         uuid        not null references events (id),
                            subscription_id  uuid        not null references subscriptions (id),
                            status           varchar(20) not null,
                            attempt_count    integer     not null default 0,
                            next_attempt_at  timestamptz,
                            created_at       timestamptz not null default now(),
                            updated_at       timestamptz not null default now(),
                            constraint uq_deliveries_event_subscription unique (event_id, subscription_id),
                            constraint chk_deliveries_status
                                check (status in ('PENDING', 'SENDING', 'SUCCEEDED', 'FAILED', 'DEAD')),
                            constraint chk_deliveries_attempt_count check (attempt_count >= 0)
);

create index idx_deliveries_due on deliveries (next_attempt_at)
    where status in ('PENDING', 'FAILED');

create index idx_deliveries_subscription_id on deliveries (subscription_id);

create table delivery_attempts (
                                   id               bigint      generated always as identity primary key,
                                   delivery_id      uuid        not null references deliveries (id),
                                   attempt_number   integer     not null,
                                   attempted_at     timestamptz not null,
                                   duration_ms      integer     not null,
                                   response_status  integer,
                                   error_message    text,
                                   constraint uq_delivery_attempts_number unique (delivery_id, attempt_number)
);