-- Test and demo logins. A whitelisted phone always gets the same fixed code instead of a random one,
-- and no WhatsApp/SMS message is sent for it. See docs/08-decisions.md for why this is a table and not Redis.
-- Example: +919999900001 asks for a code -> no message goes out, "123456" always logs that phone in.
create table otp_whitelist (
    phone      varchar(13) not null primary key,
    note       varchar(120),
    created_at timestamptz not null default now(),
    constraint otp_whitelist_phone_ck check (phone ~ '^\+91[6-9][0-9]{9}$')
);

-- A whitelisted login never sends a real message, so otp_code needs one more channel value.
alter table otp_code drop constraint otp_code_channel_ck;
alter table otp_code add constraint otp_code_channel_ck
    check (channel in ('WHATSAPP', 'SMS', 'LOG', 'WHITELIST'));
