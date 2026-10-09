-- Phase 10: `staff` stops meaning "transport people" and starts meaning "every employee of the school".
-- A teacher is a staff row with staff_type = TEACHER, not a new kind of user (decision B22, B23).
-- Details and example rows: docs/03-data-model.md → "Phase 10 tables".

-- TEACHER joins the list. The licence check is not touched: a teacher has no licence, and the old
-- constraint already allows both licence fields to be null for anyone who is not a DRIVER.
alter table staff drop constraint staff_type_ck;
alter table staff add constraint staff_type_ck
    check (staff_type in ('DRIVER', 'ATTENDANT', 'HELPER', 'TEACHER'));

-- The details every employee has. All nullable: the people added in Phase 2 have none of this filled in.
alter table staff add column joined_on       date;
alter table staff add column date_of_birth   date;
alter table staff add column gender          varchar(10);
alter table staff add column address         varchar(300);
alter table staff add column emergency_phone varchar(13);
alter table staff add column id_proof_type   varchar(20);
alter table staff add column id_proof_last4  varchar(4);

alter table staff add constraint staff_gender_ck
    check (gender is null or gender in ('MALE', 'FEMALE', 'OTHER'));
-- Same shape as `phone`. Example: '+919812340010'.
alter table staff add constraint staff_emergency_phone_ck
    check (emergency_phone is null or emergency_phone ~ '^\+91[6-9][0-9]{9}$');
alter table staff add constraint staff_id_proof_type_ck
    check (id_proof_type is null or id_proof_type in ('AADHAAR', 'VOTER_ID', 'PAN', 'DRIVING_LICENCE'));
-- Only the last 4 digits of an ID are ever stored, never a full Aadhaar number (question C11).
alter table staff add constraint staff_id_proof_last4_ck
    check (id_proof_last4 is null or id_proof_last4 ~ '^[0-9]{4}$');
-- A type without digits, or digits without a type, is half a record.
alter table staff add constraint staff_id_proof_ck
    check ((id_proof_type is null) = (id_proof_last4 is null));

-- Only what a teacher has. A driver's licence stays on `staff`. An attendant and a helper have no
-- extra table, because they have no extra information.
-- Example: staff 31, 'B.Ed, M.A. Hindi', 'Hindi, Social Science', class teacher of '3'.
create table teacher_profile (
    staff_id         bigint primary key references staff (id),
    qualification    varchar(120),
    subjects         varchar(200),
    class_teacher_of varchar(20),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);

-- One class has at most one class teacher. A teacher with no class is not counted.
create unique index teacher_profile_class_uk on teacher_profile (class_teacher_of)
    where class_teacher_of is not null;

-- Kept apart from `staff` on purpose: GET /api/v1/staff is open to VEHICLES_VIEW, which the transport
-- in-charge has. A salary column on `staff` would show every teacher's salary on the Vehicles and staff
-- screen. This table has its own permission, STAFF_SALARY_VIEW (decision B24).
-- Only the salary of today is stored. A history of raises is not built.
create table staff_salary (
    staff_id       bigint primary key references staff (id),
    monthly_salary numeric(12,2) not null,
    updated_by     bigint references app_user (id),
    created_at     timestamptz   not null default now(),
    updated_at     timestamptz   not null default now(),
    constraint staff_salary_amount_ck check (monthly_salary >= 0)
);

create index staff_salary_updated_by_ix on staff_salary (updated_by);
