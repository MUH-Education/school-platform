-- =====================================================================
-- V11  Login sessions (see docs/08-decisions.md): one row per login.
--      The server trusts Redis for the hot path (every request); this
--      table is the fallback (Redis restarted or emptied) and the only
--      place "log out everywhere" can find every session of one user.
--      There is no expires_at column: the 15-minute idle timeout lives
--      in Redis only (it slides on every request); this table only
--      knows "not revoked" and "not older than the 12-hour hard cap",
--      which is worked out from created_at at check time.
-- =====================================================================
create table user_session (
    id           bigint generated always as identity primary key,
    user_id      bigint      not null references app_user (id),
    session_hash varchar(64) not null,   -- HMAC-SHA256 of the raw session id. Never store it plain.
    revoked_at   timestamptz,
    ip_address   varchar(45),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);
create unique index user_session_hash_idx on user_session (session_hash);
create index user_session_user_idx on user_session (user_id, revoked_at);
