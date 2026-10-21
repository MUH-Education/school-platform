-- =====================================================================
-- V12  OTP state moves to Redis (see docs/08-decisions.md): the code,
--      its attempts and the rate-limit counters are no longer Postgres
--      rows. token_version is replaced by per-session revocation
--      (user_session, V11): logout, phone change, role change and
--      turning a user off now revoke sessions directly instead of
--      bumping a version number every token had to carry.
-- =====================================================================
drop table otp_code;
alter table app_user drop column token_version;
