-- =====================================================================
-- Checks for the database design. Run after the migrations and sample-data.sql:
--
--   psql -d school_dev -v ON_ERROR_STOP=1 -f checks.sql
--
-- Part A: the numbers of the designs come out of the tables (the same SQL the services will use).
-- Part B: bad data is refused by the database itself.
-- Part C: cancelling a receipt brings the months back.
-- Every check prints PASS. The first FAIL stops the file. Nothing is saved (rollback at the end).
-- =====================================================================
\set ON_ERROR_STOP on
\set VERBOSITY terse
set client_min_messages = notice;

begin;

create function pg_temp.expect(label text, got numeric, want numeric) returns void language plpgsql as $$
begin
    if got is distinct from want then
        raise exception 'FAIL  %: got %, want %', label, got, want;
    end if;
    raise notice 'PASS  %  (%)', label, got;
end $$;

create function pg_temp.must_fail(label text, stmt text, want_state text) returns void language plpgsql as $$
begin
    begin
        execute stmt;
    exception when others then
        if sqlstate = want_state then
            raise notice 'PASS  refused: %', label;
            return;
        end if;
        raise exception 'FAIL  %: wrong error % (%)', label, sqlstate, sqlerrm;
    end;
    raise exception 'FAIL  %: the database allowed it', label;
end $$;

-- ================================================================ A. numbers
do $$
declare
    d date := '2026-10-07';   -- "today" in the sample data
    n numeric;
begin
    -- Query 1. Children on route R on day D (manifest, bus status, routes and load).
    select count(*) into n
    from transport_enrolment te join student s on s.id = te.student_id
    where te.route_id = 4 and te.uses_bus
      and te.from_date <= d and (te.to_date is null or te.to_date >= d)
      and s.status = 'ACTIVE' and s.joined_on <= d;
    perform pg_temp.expect('Route 4 has 19 children', n, 19);

    select count(*) into n
    from transport_enrolment te join student s on s.id = te.student_id
    where te.stop_id = 12 and te.from_date <= d and (te.to_date is null or te.to_date >= d) and s.status = 'ACTIVE';
    perform pg_temp.expect('Jakhal stop of Route 4 has 7 children', n, 7);

    select count(*) into n
    from transport_enrolment te join student s on s.id = te.student_id
    where te.uses_bus and te.from_date <= d and (te.to_date is null or te.to_date >= d) and s.status = 'ACTIVE' and s.joined_on <= d;
    perform pg_temp.expect('255 children on the buses', n, 255);

    select sum(v.seats) into n from route r join vehicle v on v.id = r.vehicle_id where r.active;
    perform pg_temp.expect('150 seats on the 9 routes', n, 150);

    select count(*) into n from student where status = 'ACTIVE' and joined_on <= d;
    perform pg_temp.expect('290 students today (Kavya Goyal joins next year, not counted)', n, 290);

    -- Query 2. People on vehicle V on day D: the temporary row wins over the normal row.
    select staff_id into n from (
        select distinct on (duty) duty, staff_id
        from vehicle_assignment
        where vehicle_id = 4 and from_date <= date '2026-10-14' and (to_date is null or to_date >= date '2026-10-14')
        order by duty, temporary desc, from_date desc) x
    where duty = 'DRIVER';
    perform pg_temp.expect('Van 4 driver on 14 Oct is Surender (staff 20)', n, 20);

    select staff_id into n from (
        select distinct on (duty) duty, staff_id
        from vehicle_assignment
        where vehicle_id = 4 and from_date <= date '2026-10-17' and (to_date is null or to_date >= date '2026-10-17')
        order by duty, temporary desc, from_date desc) x
    where duty = 'DRIVER';
    perform pg_temp.expect('Van 4 driver on 17 Oct is Jagdish again (staff 14)', n, 14);

    -- Query 3. The attendant's route today: user → staff → vehicle where they are the attendant today → route.
    select r.id into n
    from app_user u
    join lateral (
        select distinct on (va.vehicle_id) va.vehicle_id, va.staff_id
        from vehicle_assignment va
        where va.duty = 'ATTENDANT' and va.from_date <= d and (va.to_date is null or va.to_date >= d)
        order by va.vehicle_id, va.temporary desc, va.from_date desc) today on today.staff_id = u.staff_id
    join route r on r.vehicle_id = today.vehicle_id and r.active
    where u.id = 14;
    perform pg_temp.expect('Balwan''s route today is Route 4', n, 4);

    -- Taps of the morning, Route 4
    select count(*) into n from boarding_event where route_id = 4 and service_date = d and event_type = 'BOARDED_MORNING' and outcome = 'DONE';
    perform pg_temp.expect('Route 4: 11 boarded', n, 11);
    select count(*) into n from boarding_event where route_id = 4 and service_date = d and event_type = 'BOARDED_MORNING' and outcome = 'ABSENT';
    perform pg_temp.expect('Route 4: 1 absent', n, 1);

    -- Bus SMS by the class rule: how many Route 4 children get the "boarded morning" SMS
    select count(*) into n
    from transport_enrolment te
    join student s on s.id = te.student_id
    join bus_sms_rule b on array_position(array['Nursery','LKG','UKG','1','2','3','4','5','6','7','8','9','10','11','12'], s.class_name)
                     between array_position(array['Nursery','LKG','UKG','1','2','3','4','5','6','7','8','9','10','11','12'], b.from_class)
                         and array_position(array['Nursery','LKG','UKG','1','2','3','4','5','6','7','8','9','10','11','12'], b.to_class)
    where te.route_id = 4 and te.to_date is null and b.boarded_morning;
    perform pg_temp.expect('Route 4: 14 children get the "boarded morning" SMS', n, 14);
end $$;

-- Fees: what each family owes today (the "Families with dues" page and "Pending now").
create temp view family_owes as
with fam as (
    select sg.guardian_id as family_id, sg.student_id
    from student_guardian sg join student s on s.id = sg.student_id
    where sg.is_primary and s.status = 'ACTIVE'
), open_month as (
    select f.family_id, v.*, sfs.late_fee_kind, sfs.late_fee_per_month
    from fam f
    join v_fee_month v on v.student_id = f.student_id
    join session_fee_setting sfs on sfs.session_id = v.session_id
    where v.session_id = 3 and v.balance > 0
)
select family_id,
       sum(balance) filter (where due_date <= date '2026-10-07')                                  as due_now,
       sum(case when late_fee_kind = 'PER_MONTH' and date '2026-10-07' > grace_date and paid_late = 0
                then late_fee_per_month else 0 end)                                                as late_fee,
       count(distinct month) filter (where date '2026-10-07' > grace_date)                        as late_months
from open_month
group by family_id;

do $$
declare n numeric;
begin
    select due_now + late_fee into n from family_owes where family_id = 210;
    perform pg_temp.expect('Punia family owes 15,400 (Sep late + Oct due + 200 late fee)', n, 15400);
    select due_now + late_fee into n from family_owes where family_id = 230;
    perform pg_temp.expect('Chauhan family owes 21,100 (4 late months)', n, 21100);
    select late_months into n from family_owes where family_id = 230;
    perform pg_temp.expect('Chauhan family is 4 months late', n, 4);
    select due_now + late_fee into n from family_owes where family_id = 234;
    perform pg_temp.expect('Jakhar family (2 children) owes 14,000', n, 14000);
    select count(*) into n from family_owes where late_months > 0;
    perform pg_temp.expect('9 families are late', n, 9);
    select sum(due_now + late_fee) into n from family_owes where late_months > 0;
    perform pg_temp.expect('The 9 late families owe 1,08,800', n, 108800);
    select sum(late_fee) into n from family_owes where late_months > 0;
    perform pg_temp.expect('Late fees in this: 1,800', n, 1800);

    -- Collected today (the five tiles of "Collect fees", at 11:30 am)
    select sum(amount) into n from receipt where status = 'ACTIVE' and kind = 'PAYMENT' and paid_on = '2026-10-07' and paid_at < '2026-10-07 11:30+05:30';
    perform pg_temp.expect('Collected today by 11:30: 46,000', n, 46000);
    select count(*) into n from receipt where status = 'ACTIVE' and kind = 'PAYMENT' and paid_on = '2026-10-07' and paid_at < '2026-10-07 11:30+05:30';
    perform pg_temp.expect('6 receipts by 11:30', n, 6);
    select sum(amount) into n from receipt where status = 'ACTIVE' and paid_on = '2026-10-07' and mode = 'CASH';
    perform pg_temp.expect('Cash in the drawer: 10,100', n, 10100);
    select sum(amount) into n from receipt where status = 'ACTIVE' and paid_on = '2026-10-07';
    perform pg_temp.expect('Whole day with Kavya Goyal''s admission: 55,100', n, 55100);

    -- Lines must add up to the receipt (money received + credit used = sum of lines)
    select count(*) into n
    from receipt r
    where r.amount + r.credit_used <> (select sum(fp.amount) from fee_payment fp where fp.receipt_id = r.id);
    perform pg_temp.expect('Every receipt equals the sum of its lines', n, 0);

    -- One child, this year (the "Fees this year" box of Ishaan)
    select sum(total) into n from v_fee_month where student_id = 118 and session_id = 3;
    perform pg_temp.expect('Ishaan: school fee for the year 30,000', n, 30000);
    select sum(paid_school + paid_bus) into n from v_fee_month where student_id = 118 and session_id = 3;
    perform pg_temp.expect('Ishaan: paid so far 22,500', n, 22500);
    select sum(balance) into n from v_fee_month where student_id = 118 and session_id = 3;
    perform pg_temp.expect('Ishaan: still to pay 7,500', n, 7500);

    -- The month boxes of Aryan
    select count(*) into n from v_fee_month where student_id = 57 and session_id = 3 and balance = 0;
    perform pg_temp.expect('Aryan: 5 months paid (April to August)', n, 5);
    select bus_amount into n from v_fee_month where student_id = 57 and month = '2026-06-01';
    perform pg_temp.expect('Aryan: no bus fee in June', n, 0);

    -- Kirti: late fee removed on R-2026-1186
    select late_fee_normal - late_fee_final into n from receipt where receipt_no = 'R-2026-1186';
    perform pg_temp.expect('R-2026-1186: late fee of 100 removed', n, 100);

    -- The next receipt number
    select last_no + 1 into n from number_counter where counter_key = 'RECEIPT-2026';
    perform pg_temp.expect('The next receipt is R-2026-1189', n, 1189);
end $$;

-- ================================================================ B. bad data is refused
do $$ begin perform pg_temp.must_fail('the same tap twice',
    $q$insert into boarding_event (student_id, route_id, service_date, event_type, outcome, occurred_at, recorded_by)
       values (57, 4, '2026-10-07', 'BOARDED_MORNING', 'DONE', now(), 14)$q$, '23505'); end $$;

do $$
declare n numeric;
begin
    -- The right way to save a tap that may come twice: upsert, the later tap wins.
    insert into boarding_event (student_id, route_id, service_date, event_type, outcome, occurred_at, recorded_by)
    values (67, 4, '2026-10-07', 'BOARDED_MORNING', 'DONE', '2026-10-07 07:44:00+05:30', 14)
    on conflict (student_id, service_date, event_type) do update
        set outcome = excluded.outcome, occurred_at = excluded.occurred_at, recorded_by = excluded.recorded_by, recorded_at = now()
        where boarding_event.occurred_at <= excluded.occurred_at;
    select count(*) into n from boarding_event where student_id = 67 and service_date = '2026-10-07';
    perform pg_temp.expect('upsert of a tap keeps one row', n, 1);
    select case when outcome = 'DONE' then 1 else 0 end into n from boarding_event where student_id = 67 and service_date = '2026-10-07';
    perform pg_temp.expect('the later tap wins (Pooja: absent → boarded)', n, 1);
end $$;

do $$ begin perform pg_temp.must_fail('"not travelling" in the morning',
    $q$insert into boarding_event (student_id, route_id, service_date, event_type, outcome, occurred_at, recorded_by)
       values (57, 4, '2026-10-08', 'BOARDED_MORNING', 'NOT_TRAVELLING', now(), 14)$q$, '23514'); end $$;
do $$ begin perform pg_temp.must_fail('two bus rows for the same days',
    $q$insert into transport_enrolment (student_id, uses_bus, route_id, stop_id, from_date, bus_fee_per_month, created_by)
       values (57, true, 9, 44, '2026-11-01', 800, 3)$q$, '23P01'); end $$;
do $$ begin perform pg_temp.must_fail('a stop of another route',
    $q$update transport_enrolment set to_date = '2026-10-31' where student_id = 118;
       insert into transport_enrolment (student_id, uses_bus, route_id, stop_id, from_date, bus_fee_per_month, created_by)
       values (118, true, 4, 44, '2026-11-02', 800, 3)$q$, '23503'); end $$;
do $$ begin perform pg_temp.must_fail('bus row without a stop',
    $q$insert into transport_enrolment (student_id, uses_bus, route_id, from_date, bus_fee_per_month, created_by)
       values (3, true, 4, '2027-01-01', 800, 3)$q$, '23514'); end $$;
do $$ begin perform pg_temp.must_fail('two primary parents for one child',
    $q$update student_guardian set is_primary = true where student_id = 57 and guardian_id = 211$q$, '23505'); end $$;
do $$ begin perform pg_temp.must_fail('two normal drivers on one vehicle',
    $q$insert into vehicle_assignment (vehicle_id, staff_id, duty, from_date, temporary, created_by)
       values (4, 20, 'DRIVER', '2026-11-01', false, 3)$q$, '23P01'); end $$;
do $$ begin perform pg_temp.must_fail('a replacement with no last day',
    $q$insert into vehicle_assignment (vehicle_id, staff_id, duty, from_date, temporary, created_by)
       values (5, 20, 'DRIVER', '2026-11-01', true, 3)$q$, '23514'); end $$;
do $$ begin perform pg_temp.must_fail('one vehicle on two routes',
    $q$insert into route (name, vehicle_id) values ('Route 10', 4)$q$, '23505'); end $$;
do $$ begin perform pg_temp.must_fail('changing a payment line',
    $q$update fee_payment set amount = 1 where id = (select min(id) from fee_payment)$q$, 'P0001'); end $$;
do $$ begin perform pg_temp.must_fail('deleting a payment line',
    $q$delete from fee_payment where id = (select min(id) from fee_payment)$q$, 'P0001'); end $$;
do $$ begin perform pg_temp.must_fail('the same payment saved twice (double click)',
    $q$update receipt set client_request_id = 'abc' where receipt_no in ('R-2026-1187', 'R-2026-1188')$q$, '23505'); end $$;
do $$ begin perform pg_temp.must_fail('a late fee changed without a reason',
    $q$update receipt set late_fee_reason = null where receipt_no = 'R-2026-1186'$q$, '23514'); end $$;
do $$ begin perform pg_temp.must_fail('a cancelled receipt without a reason',
    $q$update receipt set status = 'CANCELLED', cancelled_at = now(), cancelled_by = 1 where receipt_no = 'R-2026-1188'$q$, '23514'); end $$;
do $$ begin perform pg_temp.must_fail('a refund number on a payment',
    $q$update receipt set receipt_no = 'RF-2026-0099' where receipt_no = 'R-2026-1188'$q$, '23514'); end $$;
do $$ begin perform pg_temp.must_fail('a month that is not the 1st',
    $q$insert into fee_due (student_id, session_id, month, school_amount) values (57, 3, '2026-10-15', 2500)$q$, '23514'); end $$;
do $$ begin perform pg_temp.must_fail('the same month twice for a child',
    $q$insert into fee_due (student_id, session_id, month, school_amount) values (57, 3, '2026-10-01', 2500)$q$, '23505'); end $$;
do $$ begin perform pg_temp.must_fail('a discount without a reason',
    $q$insert into fee_plan (student_id, session_id, from_month, school_fee_per_month, discount_per_month, created_by)
       values (57, 3, '2027-01-01', 2500, 250, 2)$q$, '23514'); end $$;
do $$ begin perform pg_temp.must_fail('a phone without +91',
    $q$insert into guardian (name, phone) values ('Test', '9812345678')$q$, '23514'); end $$;
do $$ begin perform pg_temp.must_fail('an attendant login with no staff row',
    $q$insert into app_user (phone, role) values ('+919811111111', 'ATTENDANT')$q$, '23514'); end $$;
do $$ begin perform pg_temp.must_fail('a lost enquiry with no reason',
    $q$update enquiry set status = 'LOST' where id = 84$q$, '23514'); end $$;
do $$ begin perform pg_temp.must_fail('two sessions for the same days',
    $q$insert into academic_session (name, starts_on, ends_on) values ('2026-28', '2027-01-01', '2027-12-31')$q$, '23P01'); end $$;
do $$ begin perform pg_temp.must_fail('two bus SMS for one event and one phone',
    $q$insert into message_outbox (purpose, channel, phone, guardian_id, student_id, service_date, event_type, body)
       values ('BOARDING', 'SMS', '+919400000871', 210, 57, '2026-10-07', 'BOARDED_MORNING', 'again')$q$, '23505'); end $$;
do $$ begin perform pg_temp.must_fail('two issued TCs for one child',
    $q$insert into transfer_certificate (tc_no, student_id, issued_on, conduct, made_by) values ('TC-2026-014', 21, '2026-10-31', 'GOOD', 2);
       insert into transfer_certificate (tc_no, student_id, issued_on, conduct, made_by) values ('TC-2026-015', 21, '2026-11-01', 'GOOD', 2)$q$, '23505'); end $$;
do $$ begin perform pg_temp.must_fail('"not coming back" with no reason',
    $q$insert into promotion_run (from_session_id, to_session_id, created_by) values (3, 4, 1);
       insert into promotion_choice (run_id, student_id, choice, marked_by) values ((select max(id) from promotion_run), 9, 'NOT_COMING_BACK', 2)$q$, '23514'); end $$;
do $$ begin perform pg_temp.must_fail('a document of type OTHER with no title',
    $q$insert into student_document (student_id, doc_type, file_key, file_name, content_type, size_bytes, added_by)
       values (118, 'OTHER', 'k', 'f.pdf', 'application/pdf', 10, 2)$q$, '23514'); end $$;
do $$ begin perform pg_temp.must_fail('a file bigger than 5 MB',
    $q$insert into upload (public_id, file_key, file_name, content_type, size_bytes, uploaded_by, expires_at)
       values ('upl_big', 'k', 'f.pdf', 'application/pdf', 6000000, 4, now())$q$, '23514'); end $$;

-- ================================================================ C. cancel a receipt
do $$
declare n numeric; r bigint;
begin
    select id into r from receipt where receipt_no = 'R-2026-1186';
    update receipt set status = 'CANCELLED', cancelled_at = now(), cancelled_by = 1,
                       cancel_reason = 'WRONG_AMOUNT', cancel_note = 'Typed 8,000. The father paid 8,200.'
    where id = r;
    -- one negative line for each line
    insert into fee_payment (receipt_id, student_id, session_id, month, fee_head, amount, reversal_of_id)
    select receipt_id, student_id, session_id, month, fee_head, -amount, id from fee_payment where receipt_id = r;

    select sum(balance) into n from v_fee_month where student_id = 21 and month in ('2026-09-01', '2026-10-01');
    perform pg_temp.expect('after the cancel, Kirti owes Sep and Oct again: 8,000', n, 8000);
    select sum(amount) into n from receipt where status = 'ACTIVE' and paid_on = '2026-10-07' and paid_at < '2026-10-07 11:30+05:30';
    perform pg_temp.expect('after the cancel, collected today: 38,000', n, 38000);
end $$;

do $$ begin perform pg_temp.must_fail('cancelling the same line twice',
    $q$insert into fee_payment (receipt_id, student_id, session_id, month, fee_head, amount, reversal_of_id)
       select receipt_id, student_id, session_id, month, fee_head, amount, reversal_of_id from fee_payment
       where reversal_of_id is not null limit 1$q$, '23505'); end $$;

rollback;
\echo 'All checks passed. Nothing was saved.'
