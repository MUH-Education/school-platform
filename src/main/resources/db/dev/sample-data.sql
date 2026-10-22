-- =====================================================================
-- Sample data for your own laptop. The same people, numbers and
-- receipts as the designs and the API examples.
--
-- "Today" in this data is Wednesday 7 October 2026, about 1 pm.
--
--   psql -h localhost -U school -d school_dev -v dev=1 -f sample-data.sql
--
-- It DELETES the data first. Never run it on the real database.
-- =====================================================================
\if :{?dev}
\else
  \echo 'Stopped. This file deletes all data. Run it only on your own dev database, with -v dev=1'
  \quit
\endif
\set ON_ERROR_STOP on

begin;

-- Keep the start values of the migrations (settings, message texts, bus SMS rules), empty everything, put them back.
create temp table keep_setting  as select key, value from app_setting;
create temp table keep_rule     as select seq_no, name, from_class, to_class, boarded_morning, reached_school, boarded_evening, reached_home from bus_sms_rule;
create temp table keep_template as select kind, code, gender, name, channel, language, body, approved_body, provider_template_id, approval, active from message_template where kind = 'SYSTEM';
-- The automatic messages of V15 (Phase 12). They point at message_template, so the truncate below empties them too.
create temp table keep_notification_rule as select id, code, name, kind, audience, trigger_type, cron, event, fixed_values, delay_minutes from notification_rule;

truncate app_setting, bus_sms_rule, message_template, academic_session, app_user, user_session, audit_log, vehicle, vehicle_document, staff,
         vehicle_assignment, route, route_stop, number_counter, student, guardian, student_guardian,
         transport_enrolment, upload, student_document, student_document_note, admission_draft,
         transfer_certificate, boarding_event, holiday, message_outbox, enquiry, enquiry_stage,
         enquiry_follow_up, session_fee_setting, class_fee, fee_plan, fee_due, receipt, fee_payment,
         family_credit_entry, promotion_run, promotion_choice
    restart identity cascade;

insert into app_setting (key, value) select key, value from keep_setting;
insert into bus_sms_rule (seq_no, name, from_class, to_class, boarded_morning, reached_school, boarded_evening, reached_home)
    select * from keep_rule order by seq_no;
insert into message_template (kind, code, gender, name, channel, language, body, approved_body, provider_template_id, approval, active)
    select * from keep_template;
insert into notification_rule (code, name, kind, audience, trigger_type, cron, event, fixed_values, delay_minutes)
    select code, name, kind, audience, trigger_type, cron, event, fixed_values, delay_minutes from keep_notification_rule order by id;
-- In this sample, the bus texts are approved and the school details are filled.
update message_template set approval = 'APPROVED', provider_template_id = '11071612345000' || id where channel = 'SMS';
update app_setting set value = 'Mrs. Kiran Jain' where key = 'school.principal_name';
update app_setting set value = '531234' where key = 'school.affiliation_no';
update app_setting set value = '41234'  where key = 'school.school_code';

-- ---------------------------------------------------------------- sessions
insert into academic_session (id, name, starts_on, ends_on) overriding system value values
    (2, '2025-26', '2025-04-01', '2026-03-31'),
    (3, '2026-27', '2026-04-01', '2027-03-31'),
    (4, '2027-28', '2027-04-01', '2028-03-31');

-- ---------------------------------------------------------------- office logins
insert into app_user (id, phone, name, role, active, last_login_at, last_login_client) overriding system value values
    (1, '+919800000501', 'Sourabh', 'OWNER',              true, '2026-10-07 08:55+05:30', 'WEB'),
    (2, '+919800000502', 'Neelam',  'OFFICE_ADMIN',       true, '2026-10-07 09:02+05:30', 'WEB'),
    (3, '+919800000504', 'Jaswant', 'TRANSPORT_INCHARGE', true, '2026-10-07 06:40+05:30', 'WEB'),
    (4, '+919800000503', 'Priya',   'ADMISSIONS_DESK',    true, '2026-10-07 09:10+05:30', 'WEB');
update app_user set created_by = 1 where id > 1;

-- ---------------------------------------------------------------- staff
insert into staff (id, name, phone, staff_type, licence_no, licence_valid_till, active) overriding system value values
    (11, 'Rajpal',     '+919800000101', 'DRIVER', 'HR23 20140001412', '2029-03-14', true),
    (12, 'Mahavir',    '+919800000102', 'DRIVER', 'HR23 20120003301', '2028-11-02', true),
    (13, 'Om Prakash', '+919800000103', 'DRIVER', 'HR23 20100007710', '2027-06-30', true),
    (14, 'Jagdish',    '+919800000104', 'DRIVER', 'HR23 20110000087', '2028-07-08', true),
    (15, 'Sukhdev',    '+919800000105', 'DRIVER', 'HR23 20150002190', '2029-01-20', true),
    (16, 'Baljeet',    '+919800000106', 'DRIVER', 'HR23 20130004418', '2028-05-15', true),
    (17, 'Krishan',    '+919400000107', 'DRIVER', 'HR23 20160004530', '2026-11-02', true),
    (18, 'Ranbir',     '+919800000108', 'DRIVER', 'HR23 20090001276', '2027-09-09', true),
    (19, 'Dilbag',     '+919800000109', 'DRIVER', 'HR23 20170003355', '2030-02-28', true),
    (20, 'Surender',   '+919700000110', 'DRIVER', 'HR23 20180002265', '2030-01-21', true),
    (21, 'Ramesh',     '+919800000201', 'ATTENDANT', null, null, true),
    (22, 'Suresh',     '+919800000202', 'ATTENDANT', null, null, true),
    (23, 'Mahender',   '+919800000203', 'ATTENDANT', null, null, true),
    (24, 'Balwan',     '+919700000204', 'ATTENDANT', null, null, true),
    (25, 'Sunita',     '+919000000205', 'ATTENDANT', null, null, true),
    (26, 'Rajbir',     '+919800000206', 'ATTENDANT', null, null, true),
    (27, 'Kamla',      '+919900000207', 'ATTENDANT', null, null, true),
    (28, 'Satpal',     '+919800000208', 'ATTENDANT', null, null, true),
    (29, 'Kuldeep',    '+919800000219', 'ATTENDANT', null, null, false),
    (30, 'Dharampal',  '+919800000209', 'ATTENDANT', null, null, true);

-- Attendants with a phone app login. Kamla, Rajbir and Satpal have none yet (the "Add a user" design).
insert into app_user (id, phone, name, role, staff_id, active, last_login_at, last_login_client, created_by) overriding system value values
    (9,  '+919800000219', 'Kuldeep',   'ATTENDANT', 29, false, '2026-03-30 07:01+05:30', 'ATTENDANT_APP', 1),
    (10, '+919800000201', 'Ramesh',    'ATTENDANT', 21, true,  '2026-10-07 06:50+05:30', 'ATTENDANT_APP', 1),
    (11, '+919800000203', 'Mahender',  'ATTENDANT', 23, true,  '2026-10-06 06:55+05:30', 'ATTENDANT_APP', 1),
    (12, '+919800000202', 'Suresh',    'ATTENDANT', 22, true,  '2026-10-07 06:45+05:30', 'ATTENDANT_APP', 1),
    (14, '+919700000204', 'Balwan',    'ATTENDANT', 24, true,  '2026-10-07 06:58+05:30', 'ATTENDANT_APP', 1),
    (15, '+919000000205', 'Sunita',    'ATTENDANT', 25, true,  '2026-10-07 07:05+05:30', 'ATTENDANT_APP', 1),
    (16, '+919800000209', 'Dharampal', 'ATTENDANT', 30, true,  '2026-10-07 07:30+05:30', 'ATTENDANT_APP', 1);

-- ---------------------------------------------------------------- vehicles and papers
insert into vehicle (id, name, registration_no, vehicle_type, seats, monthly_cost, owned_by) overriding system value
select n, case when n <= 7 then 'Van ' || n else 'Bus ' || n end,
       case when n <= 7 then 'HR 23 AB 110' || n else 'HR 23 AB 210' || n end,
       case when n <= 7 then 'SMALL_VAN' else 'MID_BUS' end,
       case when n <= 7 then 14 else 26 end,
       30300.00, 'CONTRACTOR'
from generate_series(1, 9) n;

insert into vehicle_document (vehicle_id, doc_type, valid_till)
select v.id, d.doc_type,
       case
           when v.id = 9 and d.doc_type = 'FITNESS'   then date '2026-09-30'   -- Bus 9: fitness ended
           when v.id = 6 and d.doc_type = 'INSURANCE' then date '2026-10-28'   -- Van 6: ends in 21 days
           when d.doc_type = 'FITNESS'   then date '2027-03-31'
           when d.doc_type = 'INSURANCE' then date '2027-02-14'
           when d.doc_type = 'PERMIT'    then date '2028-03-31'
           else date '2026-12-20'
       end
from vehicle v cross join (values ('FITNESS'), ('INSURANCE'), ('PERMIT'), ('PUC')) d(doc_type);

-- ---------------------------------------------------------------- who works on which vehicle
-- Drivers 11..19 on vehicles 1..9, attendants 21..28 on 1..8 and Dharampal (30) on Bus 9, all from 1 April 2026.
insert into vehicle_assignment (vehicle_id, staff_id, duty, from_date, to_date, temporary, reason, created_by)
select n, 10 + n, 'DRIVER', date '2026-04-01', null::date, false, null, 3 from generate_series(1, 9) n
union all
select n, case when n = 9 then 30 else 20 + n end, 'ATTENDANT', date '2026-04-01', null, false, null, 3 from generate_series(1, 9) n;
-- Van 4 last year, and Surender as the driver while Jagdish is on leave (12 to 16 October).
insert into vehicle_assignment (vehicle_id, staff_id, duty, from_date, to_date, temporary, reason, created_by) values
    (4, 13, 'DRIVER',    '2025-07-01', '2026-03-31', false, 'MOVED',       3),
    (4, 29, 'ATTENDANT', '2025-07-01', '2026-03-31', false, 'LEFT_SCHOOL', 3),
    (4, 20, 'DRIVER',    '2026-10-12', '2026-10-16', true,  'ON_LEAVE',    3);

-- ---------------------------------------------------------------- routes and stops
insert into route (id, name, vehicle_id, school_arrival_time, school_leave_time) overriding system value
select n, 'Route ' || n, n, case when n = 9 then time '08:20' else time '08:10' end, time '14:40'
from generate_series(1, 9) n;

insert into route_stop (id, route_id, seq_no, name, name_hindi, morning_time, evening_time, active) overriding system value values
    -- Route 4: Sadhanwas 5 children, Jakhal 7, Kanheri 4, Tohana town 3
    (11, 4, 1, 'Sadhanwas',      'साधनवास',    '07:25', '15:31', true),
    (12, 4, 2, 'Jakhal',         'जाखल',       '07:40', '15:22', true),
    (13, 4, 3, 'Kanheri',        'कन्हेड़ी',     '07:55', '15:14', true),
    (14, 4, 4, 'Tohana town',    'टोहाना शहर', '08:02', '15:05', true),
    -- Route 9 (stop 43 was removed, so it is kept but not active)
    (41, 9, 1, 'Bus Stand',       'बस स्टैंड',   '07:50', '15:30', true),
    (42, 9, 2, 'Railway Road',    'रेलवे रोड',   '07:56', '15:22', true),
    (43, 9, 3, 'Old Bus Stand',   null,         '08:00', null,    false),
    (44, 9, 3, 'Model Town',      'मॉडल टाउन',   '08:02', '15:14', true),
    (45, 9, 4, 'Chandigarh Road', 'चंडीगढ़ रोड', '08:08', '15:07', true),
    (46, 9, 5, 'Kanheri',         'कन्हेड़ी',     '08:14', '15:00', true),
    -- The other routes, as on the Bus status design
    (1, 1, 1, 'Lalauda', null, '07:21', null, true), (2, 1, 2, 'Dangra', null, '07:30', null, true),
    (3, 1, 3, 'Dharsul', null, '07:44', null, true), (4, 1, 4, 'Kulan', null, '07:52', null, true),
    (5, 1, 5, 'Tohana town', null, '08:00', null, true),
    (21, 2, 1, 'Chander Kalan', null, '07:06', null, true), (22, 2, 2, 'Nangla', null, '07:15', null, true),
    (23, 2, 3, 'Indachhoi', null, '07:25', null, true), (24, 2, 4, 'Kanheri', null, '07:37', null, true),
    (31, 3, 1, 'Pirthala', null, '07:15', null, true), (32, 3, 2, 'Diwana', null, '07:24', null, true),
    (33, 3, 3, 'Gajuwala', null, '07:33', null, true), (34, 3, 4, 'Balianwala', null, '07:44', null, true),
    (35, 3, 5, 'Tohana town', null, '07:55', null, true),
    (51, 5, 1, 'Akanwali', null, '07:10', null, true), (52, 5, 2, 'Samain', null, '07:24', null, true),
    (53, 5, 3, 'Jamalpur', null, '07:34', null, true), (54, 5, 4, 'Rattakhera', null, '07:42', null, true),
    (55, 5, 5, 'Hindalwala', null, '07:50', null, true), (56, 5, 6, 'Kanheri', null, '08:00', null, true),
    (65, 6, 1, 'Dulat', null, '07:22', null, true), (66, 6, 2, 'Thuian', null, '07:33', null, true),
    (67, 6, 3, 'Hansawala', null, '07:45', null, true), (68, 6, 4, 'Tohana town', null, '07:56', null, true),
    (71, 7, 1, 'Bhimewala', null, '06:56', null, true), (72, 7, 2, 'Karandi', null, '07:05', null, true),
    (73, 7, 3, 'Meond', null, '07:13', null, true), (74, 7, 4, 'Shakkarpura', null, '07:22', null, true),
    (75, 7, 5, 'Kanheri', null, '07:31', null, true),
    (81, 8, 1, 'Kulan', null, '07:16', null, true), (82, 8, 2, 'Nathuwal', null, '07:27', null, true),
    (83, 8, 3, 'Laluwal', null, '07:43', null, true), (84, 8, 4, 'Chandpura', null, '07:52', null, true),
    (85, 8, 5, 'Tohana town', null, '08:00', null, true), (86, 8, 6, 'Kanheri', null, '08:05', null, true);

-- ---------------------------------------------------------------- fee settings
insert into session_fee_setting (session_id, bus_fee_per_month, bus_months, due_day, grace_until, late_fee_kind,
                                 late_fee_per_month, late_fee_per_day, late_fee_max_per_month, late_fee_change_roles,
                                 fees_set_by_owner, updated_by, updated_at)
select id, 800.00, '{4,5,7,8,9,10,11,12,1,2,3}', 1, 10, 'PER_MONTH', 100.00, 10.00, 300.00, '{OWNER,OFFICE_ADMIN}',
       id <> 4, 1, '2026-03-28 16:10+05:30'
from academic_session;

insert into class_fee (session_id, class_name, per_month)
select s.id, c.class_name, c.per_month
from academic_session s
cross join (values ('Nursery', 2200.00), ('LKG', 2300.00), ('UKG', 2500.00), ('1', 2500.00), ('2', 2500.00),
                   ('3', 2500.00), ('4', 2500.00), ('5', 2500.00), ('6', 2800.00), ('7', 2800.00), ('8', 2800.00),
                   ('9', 3200.00), ('10', 3200.00), ('11', 3500.00), ('12', 3500.00)) c(class_name, per_month);

-- ---------------------------------------------------------------- students with names (the designs)
insert into student (id, admission_no, name, name_hindi, dob, gender, class_name, section, village, address,
                     father_occupation, admission_session_id, joined_on, created_by) overriding system value values
    (3,   'A-2018-003', 'Aman Chauhan',    'अमन',     '2009-01-15', 'M', '12',      'A', 'Tohana town', null,         'FARMER_SMALL',         null, '2018-04-02', 4),
    (4,   'A-2018-004', 'Deepak Sihag',    'दीपक',    '2010-06-11', 'M', '11',      'A', 'Sadhanwas',   null,         'EX_SERVICEMAN',        null, '2018-04-02', 4),
    (9,   'A-2021-009', 'Rohit Kumar',     'रोहित',   '2011-02-20', 'M', '10',      'A', 'Kanheri',     null,         'LABOUR',               null, '2021-04-05', 4),
    (12,  'A-2019-012', 'Siya Punia',      'सिया',    '2010-09-03', 'F', '11',      'B', 'Jakhal',      null,         'FARMER_LARGE',         null, '2019-04-03', 4),
    (21,  'A-2020-021', 'Kirti Bansal',    'कीर्ति',   '2012-05-14', 'F', '9',       'A', 'Sadhanwas',   null,         'SHOPKEEPER',           null, '2020-04-02', 4),
    (34,  'A-2022-034', 'Mohit Nain',      'मोहित',   '2016-07-21', 'M', '5',       'A', 'Sadhanwas',   null,         'FARMER_SMALL',         null, '2022-04-04', 4),
    (45,  'A-2024-071', 'Anjali Saini',    'अंजलि',   '2019-03-08', 'F', '2',       'A', 'Sadhanwas',   null,         'PRIVATE_JOB',          null, '2024-04-02', 4),
    (57,  'A-2024-057', 'Aryan Punia',     'आर्यन',   '2018-02-10', 'M', '3',       'B', 'Jakhal',      null,         'FARMER_LARGE',         null, '2024-04-01', 4),
    (61,  'A-2020-061', 'Sahil Sangwan',   'साहिल',   '2014-11-30', 'M', '7',       'B', 'Sadhanwas',   null,         'FARMER_SMALL',         null, '2020-04-06', 4),
    (66,  'A-2021-066', 'Manpreet Jakhar', 'मनप्रीत',  '2015-04-12', 'M', '6',       'A', 'Jakhal',      null,         'FARMER_SMALL',         null, '2021-04-05', 4),
    (67,  'A-2025-067', 'Pooja Jakhar',    'पूजा',    '2020-01-25', 'F', '1',       'A', 'Jakhal',      null,         'FARMER_SMALL',         2,    '2025-04-02', 4),
    (71,  'A-2023-071', 'Harsh Sihag',     'हर्ष',     '2017-08-19', 'M', '4',       'A', 'Jakhal',      null,         'PRIVATE_JOB',          null, '2023-04-03', 4),
    (73,  'A-2019-073', 'Nikhil Dhaka',    'निखिल',   '2013-12-01', 'M', '8',       'B', 'Jakhal',      null,         'FARMER_SMALL',         null, '2019-04-03', 4),
    (76,  'A-2022-076', 'Simran Mehra',    'सिमरन',   '2016-05-05', 'F', '5',       'B', 'Kanheri',     null,         'SHOPKEEPER',           null, '2022-04-04', 4),
    (79,  'A-2023-044', 'Neha Rana',       'नेहा',    '2018-10-10', 'F', '3',       'A', 'Kanheri',     null,         'FARMER_SMALL',         null, '2023-04-03', 4),
    (81,  'A-2020-081', 'Vivek Godara',    'विवेक',   '2014-09-09', 'M', '7',       'A', 'Tohana town', null,         'GOVT_EMPLOYEE',        null, '2020-04-06', 4),
    (88,  'A-2025-088', 'Kavya Saini',     'काव्या',   '2019-11-11', 'F', '2',       'A', 'Kanheri',     null,         'TEACHER_PROFESSIONAL', 2,    '2025-04-02', 4),
    (90,  'A-2025-091', 'Yash Beniwal',    'यश',      '2021-07-07', 'M', 'LKG',     'A', 'Kanheri',     null,         'FARMER_LARGE',         2,    '2025-04-02', 4),
    (95,  'A-2022-095', 'Ravi Kumar',      'रवि',     '2016-04-04', 'M', '5',       'B', 'Kanheri',     null,         'LABOUR',               null, '2022-04-04', 4),
    (102, 'A-2025-102', 'Tanvi Mehta',     'तन्वी',    '2021-02-14', 'F', 'UKG',     'A', 'Jakhal',      null,         'PRIVATE_JOB',          2,    '2025-04-02', 4),
    (118, 'A-2026-118', 'Ishaan Sharma',   'ईशान',    '2017-03-12', 'M', '4',       'A', 'Tohana town', 'Model Town', 'GOVT_EMPLOYEE',        3,    '2026-04-01', 4),
    (129, 'A-2026-129', 'Harshit Malik',   'हर्षित',   '2013-03-03', 'M', '8',       'A', 'Kanheri',     null,         'PRIVATE_JOB',          3,    '2026-08-12', 4),
    (131, 'A-2026-131', 'Riya Jangra',     'रिया',    '2022-06-01', 'F', 'Nursery', 'A', 'Tohana town', null,         'GOVT_EMPLOYEE',        3,    '2026-10-06', 4),
    (140, 'A-2027-004', 'Kavya Goyal',     'काव्या',   '2022-08-19', 'F', 'UKG',     'A', 'Tohana town', 'Model Town', 'SHOPKEEPER',           4,    '2027-04-01', 4),
    (150, 'A-2025-090', 'Kunal Jangra',    'कुणाल',   '2021-05-05', 'M', 'LKG',     'A', 'Tohana town', null,         'OTHER',                2,    '2025-04-02', 4),
    (152, 'A-2022-061', 'Sahil Mann',      'साहिल',   '2016-01-01', 'M', '5',       'B', 'Jakhal',      null,         'FARMER_SMALL',         null, '2022-04-04', 4);

-- ---------------------------------------------------------------- parents
insert into guardian (id, name, phone) overriding system value values
    (210, 'Rajesh Punia',    '+919400000871'), (211, 'Sunita Punia',   '+919800000512'),
    (230, 'Mahender Chauhan','+919400000219'), (231, 'Suresh Kumar',   '+919000000413'),
    (232, 'Balraj Dhaka',    '+919800000652'), (233, 'Naveen Mehra',   '+919700000318'),
    (234, 'Satish Jakhar',   '+919900000740'), (235, 'Ashok Godara',   '+919800000127'),
    (236, 'Vinod Malik',     '+919700000330'), (237, 'Mukesh Beniwal', '+919400000586'),
    (240, 'Sandeep Nain',    '+919700000209'), (241, 'Pawan Bansal',   '+919800000665'),
    (242, 'Ajay Mehta',      '+919900000052'), (243, 'Ramesh Sihag',   '+919400000338'),
    (244, 'Ramesh Sihag',    '+919800000417'), (245, 'Mahesh Saini',   '+919000000455'),
    (246, 'Dinesh Sangwan',  '+919800000611'), (247, 'Ravinder Rana',  '+919700000479'),
    (248, 'Sunil Jangra',    '+919800000790'), (249, 'Sunita Bansal',  null),
    (260, 'Rajiv Saini',     '+919700000126'),
    (301, 'Sanjay Sharma',   '+919800000340'), (302, 'Pooja Sharma',   '+919700000615'),
    (412, 'Rakesh Goyal',    '+919800000771'), (413, 'Anita Goyal',    '+919700000118'),
    (420, 'Mahavir Jangra',  '+919800000890'), (421, 'Jagbir Mann',    '+919800000622'),
    (422, 'Om Kumar',        '+919800000633');

insert into student_guardian (student_id, guardian_id, relation, sms_enabled, is_primary) values
    (57, 210, 'FATHER', true, true), (57, 211, 'MOTHER', true, false),
    (12, 210, 'FATHER', true, true), (12, 211, 'MOTHER', true, false),
    (3, 230, 'FATHER', true, true), (9, 231, 'FATHER', true, true), (73, 232, 'FATHER', true, true),
    (76, 233, 'FATHER', true, true), (66, 234, 'FATHER', true, true), (67, 234, 'FATHER', true, true),
    (81, 235, 'FATHER', true, true), (129, 236, 'FATHER', true, true), (90, 237, 'FATHER', true, true),
    (34, 240, 'FATHER', true, true), (21, 241, 'FATHER', true, true), (21, 249, 'MOTHER', false, false),
    (102, 242, 'FATHER', true, true), (4, 243, 'FATHER', true, true), (71, 244, 'FATHER', true, true),
    (45, 245, 'FATHER', true, true), (61, 246, 'FATHER', true, true), (79, 247, 'FATHER', true, true),
    (131, 248, 'FATHER', true, true), (88, 260, 'FATHER', true, true),
    (118, 301, 'FATHER', true, true), (118, 302, 'MOTHER', true, false),
    (140, 412, 'FATHER', true, true), (140, 413, 'MOTHER', true, false),
    (150, 420, 'FATHER', true, true), (152, 421, 'FATHER', true, true), (95, 422, 'FATHER', true, true);

-- ---------------------------------------------------------------- bus rows of the named children
insert into transport_enrolment (student_id, uses_bus, route_id, stop_id, from_date, to_date, bus_fee_per_month, reason, created_by)
select s.id, true, 4, x.stop_id, greatest(s.joined_on, date '2026-04-01'), null::date, 800.00,
       case when s.joined_on >= date '2026-04-01' then 'ADMISSION' else 'IMPORT' end, 3
from student s
join (values (34, 11), (45, 11), (61, 11), (21, 11), (4, 11),
             (57, 12), (12, 12), (66, 12), (102, 12), (71, 12), (73, 12), (67, 12),
             (90, 13), (76, 13), (9, 13), (79, 13),
             (81, 14), (131, 14), (3, 14)) x(student_id, stop_id) on x.student_id = s.id;
insert into transport_enrolment (student_id, uses_bus, route_id, stop_id, from_date, to_date, bus_fee_per_month, reason, created_by) values
    (129, true,  7,    75,   '2026-08-12', null, 800.00, 'ADMISSION', 4),     -- Harshit, Route 7, Kanheri
    (140, true,  9,    44,   '2027-04-01', null, 800.00, 'ADMISSION', 4),     -- Kavya Goyal, from next year
    (118, false, null, null, '2026-04-01', null, null,   'ADMISSION', 4),     -- Ishaan, no bus
    (88,  false, null, null, '2026-04-01', null, null,   'IMPORT',    4),
    (150, false, null, null, '2026-04-01', null, null,   'IMPORT',    4),
    (152, false, null, null, '2026-04-01', null, null,   'IMPORT',    4),
    (95,  false, null, null, '2026-04-01', null, null,   'IMPORT',    4);

-- ---------------------------------------------------------------- the other children (no names in the designs)
-- So that the counts match the designs: 290 students, 255 on the bus, and the children per route
-- of the "Routes and load" page (Route 1: 24, Route 2: 27, ...). They have no fees in this data.
create temp table filler (route_id int, kids int);
insert into filler values (1, 24), (2, 27), (3, 22), (5, 26), (6, 21), (7, 24), (8, 46), (9, 45), (0, 30);  -- 0 = no bus

insert into student (id, admission_no, name, dob, gender, class_name, section, village, father_occupation, joined_on, created_by) overriding system value
select 1000 + row_number() over (),
       'A-2023-' || (500 + row_number() over ()),
       'Child ' || f.route_id || '-' || lpad(k::text, 2, '0'),
       date '2012-01-01' + (k * 97) % 3000,
       case when k % 2 = 0 then 'F' else 'M' end,
       (array['Nursery', 'LKG', 'UKG', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12'])[1 + (k + f.route_id) % 15],
       case when k % 3 = 0 then 'B' else 'A' end,
       'Village ' || f.route_id,
       (array['FARMER_SMALL', 'FARMER_LARGE', 'GOVT_EMPLOYEE', 'SHOPKEEPER', 'PRIVATE_JOB', 'LABOUR'])[1 + k % 6],
       date '2023-04-03', 4
from filler f, generate_series(1, f.kids) k;

insert into guardian (id, name, phone) overriding system value
select s.id + 1000, 'Parent of ' || s.name, '+9196' || lpad((s.id)::text, 8, '0')
from student s where s.id > 1000;
insert into student_guardian (student_id, guardian_id, relation, sms_enabled, is_primary)
select id, id + 1000, 'FATHER', true, true from student where id > 1000;

-- Route 9: 12, 9, 11, 8 and 5 children at its stops. The other routes: round robin over their stops.
insert into transport_enrolment (student_id, uses_bus, route_id, stop_id, from_date, bus_fee_per_month, reason, created_by)
select s.id, true, r.route_id,
       case when r.route_id = 9 then
                case when r.k <= 12 then 41 when r.k <= 21 then 42 when r.k <= 32 then 44 when r.k <= 40 then 45 else 46 end
            else (select st.id from route_stop st where st.route_id = r.route_id and st.active
                  order by st.seq_no offset (r.k - 1) % (select count(*) from route_stop x where x.route_id = r.route_id and x.active) limit 1)
       end,
       date '2026-04-01', 800.00, 'IMPORT', 3
from (select id, split_part(split_part(name, ' ', 2), '-', 1)::int as route_id,
             split_part(name, '-', 2)::int as k
      from student where id > 1000) r
join student s on s.id = r.id
where r.route_id <> 0;
insert into transport_enrolment (student_id, uses_bus, from_date, reason, created_by)
select id, false, date '2026-04-01', 'IMPORT', 3 from student
where id > 1000 and split_part(split_part(name, ' ', 2), '-', 1) = '0';

-- ---------------------------------------------------------------- fee plans and monthly dues (named children)
insert into fee_plan (student_id, session_id, from_month, school_fee_per_month, pay_frequency, created_by)
select s.id, 3, date_trunc('month', greatest(s.joined_on, date '2026-04-01'))::date, cf.per_month,
       case when s.id = 118 then 'QUARTERLY' else 'MONTHLY' end, 4
from student s join class_fee cf on cf.session_id = 3 and cf.class_name = s.class_name
where s.id < 1000 and s.joined_on < date '2027-04-01';
insert into fee_plan (student_id, session_id, from_month, school_fee_per_month, pay_frequency, created_by)
values (140, 4, '2027-04-01', 2500.00, 'QUARTERLY', 4);                       -- Kavya Goyal, next year

-- One row per child per month. Bus fee when the child is on the bus in that month and the month has a bus fee.
insert into fee_due (student_id, session_id, month, school_amount, discount_amount, bus_amount)
select p.student_id, p.session_id, m.month::date,
       p.school_fee_per_month - p.discount_per_month, p.discount_per_month,
       coalesce((select te.bus_fee_per_month
                 from transport_enrolment te
                 where te.student_id = p.student_id and te.uses_bus
                   and te.from_date <= (m.month + interval '1 month - 1 day')::date
                   and (te.to_date is null or te.to_date >= m.month::date)
                   and extract(month from m.month)::int = any (sfs.bus_months)
                 order by te.from_date desc limit 1), 0)
from fee_plan p
join academic_session a on a.id = p.session_id
join session_fee_setting sfs on sfs.session_id = p.session_id
cross join lateral generate_series(p.from_month, a.ends_on, interval '1 month') m(month);

-- ---------------------------------------------------------------- receipts
-- A small helper for this file only: pays the months from_month..to_month of some children of a family.
create function pg_temp.pay(p_no text, p_family bigint, p_session bigint, p_at timestamptz, p_mode text, p_ref text,
                            p_by bigint, p_students bigint[], p_from date, p_to date,
                            p_source text default 'COLLECT', p_late_normal numeric default 0, p_late_final numeric default 0,
                            p_late_reason text default null, p_late_months date[] default '{}')
returns void language plpgsql as $$
declare
    v_receipt bigint;
    v_fees    numeric;
begin
    select sum(d.school_amount + d.bus_amount) into v_fees
    from fee_due d where d.student_id = any (p_students) and d.month between p_from and p_to;

    insert into receipt (receipt_no, kind, source, session_id, family_guardian_id, paid_on, paid_at, mode, reference,
                         amount, late_fee_normal, late_fee_final, late_fee_reason, late_fee_changed_by, taken_by, created_at)
    values (p_no, 'PAYMENT', p_source, p_session, p_family, (p_at at time zone 'Asia/Kolkata')::date, p_at, p_mode, p_ref,
            v_fees + p_late_final, p_late_normal, p_late_final, p_late_reason,
            case when p_late_final <> p_late_normal then p_by end, p_by, p_at)
    returning id into v_receipt;

    insert into fee_payment (receipt_id, student_id, session_id, month, fee_head, amount, created_at)
    select v_receipt, d.student_id, d.session_id, d.month, h.head, h.amount, p_at
    from fee_due d
    cross join lateral (values ('SCHOOL', d.school_amount), ('BUS', d.bus_amount)) h(head, amount)
    where d.student_id = any (p_students) and d.month between p_from and p_to and h.amount > 0;

    if p_late_final > 0 then
        insert into fee_payment (receipt_id, student_id, session_id, month, fee_head, amount, created_at)
        select v_receipt, st, p_session, lm, 'LATE', p_late_final / (cardinality(p_students) * cardinality(p_late_months)), p_at
        from unnest(p_students) st, unnest(p_late_months) lm;
    end if;
end;
$$;

-- Earlier this year (Neelam took them all, except Ishaan's admission by Priya)
do $$ begin
    perform pg_temp.pay('R-2026-0005', 301, 3, '2026-04-01 10:30+05:30', 'CASH', null, 4, '{118}', '2026-04-01', '2026-06-01', 'ADMISSION');
    perform pg_temp.pay('R-2026-0098', 210, 3, '2026-04-03 11:05+05:30', 'CASH', null, 2, '{57,12}', '2026-04-01', '2026-06-01');
    perform pg_temp.pay('R-2026-0150', 230, 3, '2026-05-02 10:10+05:30', 'CASH', null, 2, '{3}', '2026-04-01', '2026-05-01');
    perform pg_temp.pay('R-2026-0233', 231, 3, '2026-06-04 12:00+05:30', 'CASH', null, 2, '{9}', '2026-04-01', '2026-06-01');
    perform pg_temp.pay('R-2026-0398', 301, 3, '2026-07-01 09:40+05:30', 'UPI', 'UTR 6201 1188 0451', 2, '{118}', '2026-07-01', '2026-09-01');
    perform pg_temp.pay('R-2026-0401', 233, 3, '2026-07-02 10:20+05:30', 'CASH', null, 2, '{76}', '2026-04-01', '2026-07-01');
    perform pg_temp.pay('R-2026-0405', 232, 3, '2026-07-03 11:15+05:30', 'UPI', 'UTR 6201 3320 7764', 2, '{73}', '2026-04-01', '2026-07-01');
    perform pg_temp.pay('R-2026-0412', 210, 3, '2026-07-06 10:45+05:30', 'UPI', 'UTR 6201 5521 0098', 2, '{57,12}', '2026-07-01', '2026-08-01');
    perform pg_temp.pay('R-2026-0560', 234, 3, '2026-08-05 09:50+05:30', 'CASH', null, 2, '{66,67}', '2026-04-01', '2026-08-01');
    perform pg_temp.pay('R-2026-0575', 235, 3, '2026-08-08 10:05+05:30', 'UPI', 'UTR 6202 0417 8812', 2, '{81}', '2026-04-01', '2026-08-01');
    perform pg_temp.pay('R-2026-0590', 237, 3, '2026-08-09 11:30+05:30', 'CASH', null, 2, '{90}', '2026-04-01', '2026-08-01');
    perform pg_temp.pay('R-2026-0600', 236, 3, '2026-08-12 12:40+05:30', 'CASH', null, 4, '{129}', '2026-08-01', '2026-08-01', 'ADMISSION');
    perform pg_temp.pay('R-2026-0610', 241, 3, '2026-08-14 10:00+05:30', 'UPI', 'UTR 6202 1190 3301', 2, '{21}', '2026-04-01', '2026-08-01');
    perform pg_temp.pay('R-2026-0700', 240, 3, '2026-09-05 10:10+05:30', 'CASH', null, 2, '{34}', '2026-04-01', '2026-09-01');
    perform pg_temp.pay('R-2026-0705', 242, 3, '2026-09-06 10:30+05:30', 'UPI', 'UTR 6202 5560 1177', 2, '{102}', '2026-04-01', '2026-09-01');
    perform pg_temp.pay('R-2026-0710', 243, 3, '2026-09-07 11:00+05:30', 'CASH', null, 2, '{4}', '2026-04-01', '2026-09-01');
    perform pg_temp.pay('R-2026-0715', 260, 3, '2026-09-08 09:30+05:30', 'CASH', null, 2, '{88}', '2026-04-01', '2026-09-01');
    perform pg_temp.pay('R-2026-0720', 245, 3, '2026-09-09 09:10+05:30', 'CASH', null, 2, '{45}', '2026-04-01', '2026-09-01');
    perform pg_temp.pay('R-2026-0721', 246, 3, '2026-09-09 09:20+05:30', 'CASH', null, 2, '{61}', '2026-04-01', '2026-09-01');
    perform pg_temp.pay('R-2026-0722', 244, 3, '2026-09-09 09:30+05:30', 'CASH', null, 2, '{71}', '2026-04-01', '2026-09-01');
    perform pg_temp.pay('R-2026-0723', 247, 3, '2026-09-09 09:40+05:30', 'CASH', null, 2, '{79}', '2026-04-01', '2026-09-01');
    perform pg_temp.pay('R-2026-0730', 420, 3, '2026-09-10 10:00+05:30', 'CASH', null, 2, '{150}', '2026-04-01', '2026-09-01');
    perform pg_temp.pay('R-2026-0731', 421, 3, '2026-09-10 10:10+05:30', 'CASH', null, 2, '{152}', '2026-04-01', '2026-09-01');
    perform pg_temp.pay('R-2026-0732', 422, 3, '2026-09-10 10:20+05:30', 'CASH', null, 2, '{95}', '2026-04-01', '2026-09-01');
    perform pg_temp.pay('R-2026-1102', 301, 3, '2026-10-01 09:15+05:30', 'UPI', 'UTR 6280 0012 4410', 2, '{118}', '2026-10-01', '2026-12-01');
end $$;

-- Today, Wednesday 7 October 2026: the six receipts of the "Collect fees" design (₹46,000)
do $$ begin
    perform pg_temp.pay('R-2026-1183', 248, 3, '2026-10-07 09:05+05:30', 'BANK_TRANSFER', 'NEFT N280 2611 7730', 2, '{131}', '2026-10-01', '2027-03-01');
    perform pg_temp.pay('R-2026-1184', 243, 3, '2026-10-07 09:31+05:30', 'CASH', null, 2, '{4}', '2026-10-01', '2026-10-01');
    perform pg_temp.pay('R-2026-1185', 242, 3, '2026-10-07 09:58+05:30', 'UPI', 'UTR 6280 7714 2209', 2, '{102}', '2026-10-01', '2026-12-01');
    perform pg_temp.pay('R-2026-1186', 241, 3, '2026-10-07 10:15+05:30', 'UPI', 'UTR 6280 9921 0458', 2, '{21}', '2026-09-01', '2026-10-01',
                       'COLLECT', 100.00, 0.00, 'CROP_MONEY_LATE');                                  -- "Late fee removed"
    perform pg_temp.pay('R-2026-1187', 240, 3, '2026-10-07 10:42+05:30', 'CASH', null, 2, '{34}', '2026-10-01', '2026-10-01');
    perform pg_temp.pay('R-2026-1188', 260, 3, '2026-10-07 11:20+05:30', 'CASH', null, 2, '{88}', '2026-10-01', '2026-10-01');
end $$;
-- Kavya Goyal's admission at 12:52, for April to June 2027 (session 2027-28)
do $$ begin
    perform pg_temp.pay('R-2027-0003', 412, 4, '2026-10-07 12:52+05:30', 'UPI', 'UTR 6281 1150 0042', 4, '{140}', '2027-04-01', '2027-06-01', 'ADMISSION');
end $$;

insert into number_counter (counter_key, last_no) values
    ('ADMISSION-2026', 131), ('ADMISSION-2027', 4), ('RECEIPT-2026', 1188), ('RECEIPT-2027', 3),
    ('TC-2026', 13), ('REFUND-2026', 2);

-- ---------------------------------------------------------------- today's taps (Route 4, 7:48 am, as on "One bus today")
insert into boarding_event (student_id, route_id, service_date, event_type, outcome, occurred_at, recorded_by) values
    (34, 4, '2026-10-07', 'BOARDED_MORNING', 'DONE',   '2026-10-07 07:26:10+05:30', 14),
    (45, 4, '2026-10-07', 'BOARDED_MORNING', 'DONE',   '2026-10-07 07:26:20+05:30', 14),
    (61, 4, '2026-10-07', 'BOARDED_MORNING', 'DONE',   '2026-10-07 07:26:31+05:30', 14),
    (21, 4, '2026-10-07', 'BOARDED_MORNING', 'DONE',   '2026-10-07 07:27:02+05:30', 14),
    (4,  4, '2026-10-07', 'BOARDED_MORNING', 'DONE',   '2026-10-07 07:27:15+05:30', 14),
    (57, 4, '2026-10-07', 'BOARDED_MORNING', 'DONE',   '2026-10-07 07:42:10+05:30', 14),
    (12, 4, '2026-10-07', 'BOARDED_MORNING', 'DONE',   '2026-10-07 07:42:15+05:30', 14),
    (66, 4, '2026-10-07', 'BOARDED_MORNING', 'DONE',   '2026-10-07 07:42:30+05:30', 14),
    (102,4, '2026-10-07', 'BOARDED_MORNING', 'DONE',   '2026-10-07 07:43:01+05:30', 14),
    (71, 4, '2026-10-07', 'BOARDED_MORNING', 'DONE',   '2026-10-07 07:43:10+05:30', 14),
    (73, 4, '2026-10-07', 'BOARDED_MORNING', 'DONE',   '2026-10-07 07:43:20+05:30', 14),
    (67, 4, '2026-10-07', 'BOARDED_MORNING', 'ABSENT', '2026-10-07 07:43:40+05:30', 14);

-- ---------------------------------------------------------------- messages
insert into message_outbox (purpose, channel, phone, guardian_id, student_id, service_date, event_type, template_code, body,
                            status, attempts, provider_ref, problem, error, sent_at, delivered_at, created_at) values
    ('BOARDING', 'SMS', '+919700000209', 240, 34, '2026-10-07', 'BOARDED_MORNING', 'BOARDED_MORNING', 'मोहित सुबह 7:26 बजे बस में चढ़ गया। – MUH Jain School',
     'FAILED', 3, 'msg-8f2c-7101', 'PHONE_OFF', 'Phone switched off. Tried 3 times.', null, null, '2026-10-07 07:26:12+05:30'),
    ('BOARDING', 'SMS', '+919000000455', 245, 45, '2026-10-07', 'BOARDED_MORNING', 'BOARDED_MORNING', 'अंजलि सुबह 7:26 बजे बस में चढ़ गई। – MUH Jain School',
     'DELIVERED', 1, 'msg-8f2c-7102', null, null, '2026-10-07 07:26:22+05:30', '2026-10-07 07:26:40+05:30', '2026-10-07 07:26:21+05:30'),
    ('BOARDING', 'SMS', '+919400000871', 210, 57, '2026-10-07', 'BOARDED_MORNING', 'BOARDED_MORNING', 'आर्यन सुबह 7:42 बजे बस में चढ़ गया। – MUH Jain School',
     'DELIVERED', 1, 'msg-8f2c-7741', null, null, '2026-10-07 07:42:12+05:30', '2026-10-07 07:42:30+05:30', '2026-10-07 07:42:11+05:30'),
    ('BOARDING', 'SMS', '+919800000512', 211, 57, '2026-10-07', 'BOARDED_MORNING', 'BOARDED_MORNING', 'आर्यन सुबह 7:42 बजे बस में चढ़ गया। – MUH Jain School',
     'DELIVERED', 1, 'msg-8f2c-7742', null, null, '2026-10-07 07:42:12+05:30', '2026-10-07 07:42:33+05:30', '2026-10-07 07:42:11+05:30'),
    ('BOARDING', 'SMS', '+919900000052', 242, 102, '2026-10-07', 'BOARDED_MORNING', 'BOARDED_MORNING', 'तन्वी सुबह 7:43 बजे बस में चढ़ गई। – MUH Jain School',
     'DELIVERED', 1, 'msg-8f2c-7750', null, null, '2026-10-07 07:43:03+05:30', '2026-10-07 07:43:20+05:30', '2026-10-07 07:43:02+05:30'),
    ('BOARDING', 'SMS', '+919800000417', 244, 71, '2026-10-07', 'BOARDED_MORNING', 'BOARDED_MORNING', 'हर्ष सुबह 7:43 बजे बस में चढ़ गया। – MUH Jain School',
     'SENT', 1, 'msg-8f2c-7752', null, null, '2026-10-07 07:43:12+05:30', null, '2026-10-07 07:43:11+05:30');

insert into message_outbox (purpose, channel, phone, guardian_id, receipt_id, template_code, body, status, attempts, sent_at, delivered_at, created_by, created_at)
select 'FEE_RECEIPT', 'WHATSAPP', g.phone, g.id, r.id, 'FEE_RECEIPT',
       'फ़ीस ₹' || r.amount::bigint || ' मिल गई है। रसीद नंबर ' || r.receipt_no || ' साथ में भेजी है। धन्यवाद — MUH Jain Global School',
       'DELIVERED', 1, r.paid_at + interval '1 minute', r.paid_at + interval '90 seconds', r.taken_by, r.paid_at + interval '30 seconds'
from receipt r join guardian g on g.id = r.family_guardian_id
where r.receipt_no in ('R-2026-1183', 'R-2026-1185', 'R-2026-1186', 'R-2026-1187');

-- ---------------------------------------------------------------- enquiries (session 2027-28)
insert into enquiry (id, session_id, parent_name, phone, relation, village, child_name, class_sought, child_age, current_school,
                     source, referred_by, referred_by_guardian_id, needs_bus, needs_bus_from, status, status_before_lost,
                     lost_reason, lost_note, next_follow_up_on, next_follow_up_type, note, admitted_student_id, created_by, created_at)
overriding system value values
    (77, 4, 'Anita Goyal', '+919800000771', 'MOTHER', 'Tohana town', 'Kavya', 'UKG', '4 years', 'Little Stars Play School',
     'WALK_IN', null, null, 'YES', 'Model Town', 'ADMITTED', null, null, null, null, null,
     'Father Rakesh Goyal is a shopkeeper. Elder cousin studies here in Class 6.', 140, 4, '2026-09-29 11:00+05:30'),
    (74, 4, 'Meena Kumari', '+919700000649', 'MOTHER', 'Jakhal', null, 'Nursery', null, null,
     'FACEBOOK', null, null, 'UNKNOWN', null, 'LOST', 'CONTACTED', 'FEE_TOO_HIGH', null, null, null, null, null, 4, '2026-09-25 12:00+05:30'),
    (80, 4, 'Gurpreet Singh', '+919800000265', 'FATHER', 'Dharsul', null, 'LKG', null, null,
     'HOARDING', null, null, 'UNKNOWN', null, 'CONTACTED', null, null, null, '2026-10-05', 'CALL', null, null, 4, '2026-10-03 10:30+05:30'),
    (82, 4, 'Manoj Sharma', '+919700000530', 'FATHER', 'Tohana town', 'Daksh', '6', '11 years', null,
     'FACEBOOK', null, null, 'NO', null, 'CONTACTED', null, null, null, '2026-10-09', 'VISIT', null, null, 4, '2026-10-05 16:00+05:30'),
    (83, 4, 'Sunita Devi', '+919400000087', 'MOTHER', 'Kanheri', null, 'Nursery', null, null,
     'REFERRAL', 'Rajesh Punia', 210, 'UNKNOWN', null, 'NEW', null, null, null, '2026-10-07', 'CALL', null, null, 4, '2026-10-06 12:10+05:30'),
    (84, 4, 'Rajesh Kumar', '+919800000412', 'FATHER', 'Jakhal', null, '1', null, null,
     'WALK_IN', null, null, 'UNKNOWN', null, 'NEW', null, null, null, '2026-10-07', 'CALL', null, null, 4, '2026-10-06 15:40+05:30');

insert into enquiry_stage (enquiry_id, status, reached_on, created_by) values
    (77, 'NEW', '2026-09-29', 4), (77, 'CONTACTED', '2026-10-02', 4), (77, 'VISITED', '2026-10-06', 2),
    (77, 'APPLIED', '2026-10-06', 2), (77, 'ADMITTED', '2026-10-07', 4),
    (74, 'NEW', '2026-09-25', 4), (74, 'CONTACTED', '2026-09-27', 4), (74, 'LOST', '2026-10-01', 4),
    (80, 'NEW', '2026-10-03', 4), (80, 'CONTACTED', '2026-10-03', 4),
    (82, 'NEW', '2026-10-05', 4), (82, 'CONTACTED', '2026-10-05', 4),
    (83, 'NEW', '2026-10-06', 4), (84, 'NEW', '2026-10-06', 4);

insert into enquiry_follow_up (enquiry_id, happened_on, follow_type, note, next_type, next_on, created_by) values
    (77, '2026-09-29', 'WALK_IN', 'Wants UKG for Kavya next year. Gave her the fee sheet.', 'CALL', '2026-10-02', 4),
    (77, '2026-10-02', 'CALL', 'Asked about the bus from Model Town. Told her Route 9 is full now and we will check a seat.', 'VISIT', '2026-10-06', 4),
    (77, '2026-10-06', 'VISIT', 'Came with Kavya and saw the classroom. Filled the form. Will bring the documents on 10 October.', 'DOCUMENTS', '2026-10-10', 2);

-- ---------------------------------------------------------------- documents, drafts, holidays, history
insert into student_document (student_id, doc_type, file_key, file_name, content_type, size_bytes, added_by, created_at) values
    (118, 'BIRTH_CERTIFICATE', 'students/118/birth-certificate.jpg', 'birth-certificate.jpg', 'image/jpeg', 1258291, 4, '2026-04-01 10:24+05:30'),
    (118, 'PARENT_ID',         'students/118/voter-card.pdf',        'voter-card-sanjay-sharma.pdf', 'application/pdf', 389120, 4, '2026-04-01 10:25+05:30'),
    (118, 'ADDRESS_PROOF',     'students/118/electricity-bill.jpg',  'electricity-bill.jpg', 'image/jpeg', 2202009, 4, '2026-04-01 10:26+05:30'),
    (118, 'LAST_REPORT_CARD',  'students/118/report-card.pdf',       'report-card-class-3.pdf', 'application/pdf', 512000, 2, '2026-04-03 12:00+05:30'),
    (140, 'BIRTH_CERTIFICATE', 'students/140/birth-certificate.jpg', 'birth-certificate.jpg', 'image/jpeg', 1258291, 4, '2026-10-07 12:52+05:30'),
    (140, 'PARENT_ID',         'students/140/voter-card.pdf',        'voter-card-rakesh-goyal.pdf', 'application/pdf', 389120, 4, '2026-10-07 12:52+05:30');
insert into student_document_note (student_id, doc_type, note) values (118, 'TRANSFER_CERTIFICATE', 'The old school has not given it yet');

insert into admission_draft (id, title, session_id, form, created_by, updated_by) overriding system value values
    (12, 'Nitin Bishnoi, Class 1', 4, '{"student": {"name": "Nitin Bishnoi", "className": "1"}, "family": {"father": {"name": "Mahesh Bishnoi", "phone": "99000 00281"}}}', 4, 4);

insert into holiday (holiday_date, name, created_by) values
    ('2026-10-20', 'Dussehra', 1), ('2026-11-08', 'Diwali', 1), ('2026-11-09', 'Day after Diwali', 1);

insert into audit_log (entity_type, entity_id, action, summary, changed_by, changed_at) values
    ('STUDENT', 118, 'CREATED', 'Admitted to Class 4, no bus', 4, '2026-04-01 10:20+05:30'),
    ('STUDENT', 118, 'UPDATED', 'Mother''s phone number added', 4, '2026-06-03 11:40+05:30'),
    ('STUDENT', 118, 'UPDATED', 'Section changed from B to A', 2, '2026-08-12 10:05+05:30');

-- ---------------------------------------------------------------- next ids after the fixed ids above
do $$
declare t record;
begin
    for t in select table_name from information_schema.columns
             where table_schema = 'public' and column_name = 'id' and is_identity = 'YES'
    loop
        execute format('select setval(pg_get_serial_sequence(%L, ''id''), greatest((select coalesce(max(id), 0) from %I), 1))',
                       t.table_name, t.table_name);
    end loop;
end $$;

commit;

\echo 'Sample data loaded: today is Wednesday 7 October 2026.'
