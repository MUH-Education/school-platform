## The queries used everywhere

Write each one once, in a service, and use it from every screen. All of them are tested in `db-checks/checks.sql`
with the sample data. `:day` is the school day in Asia/Kolkata, made from the injected `Clock`, never `now()` in SQL.

### 1. Children on route R on day D

Used by the manifest, Bus status, Routes and load.

```sql
select te.student_id, te.stop_id
from transport_enrolment te
join student s on s.id = te.student_id
where te.route_id = :routeId
  and te.uses_bus
  and te.from_date <= :day and (te.to_date is null or te.to_date >= :day)
  and s.status = 'ACTIVE' and s.joined_on <= :day;
```

Example: Route 4 on 7 Oct 2026 → 19 children: Sadhanwas 5, Jakhal 7, Kanheri 4, Tohana town 3.

### 2. People on vehicle V on day D

The temporary row (a replacement) wins over the normal row.

```sql
select distinct on (duty) duty, staff_id, temporary
from vehicle_assignment
where vehicle_id = :vehicleId
  and from_date <= :day and (to_date is null or to_date >= :day)
order by duty, temporary desc, from_date desc;
```

Example: Van 4 on 14 Oct → driver Surender (temporary, Jagdish is on leave). On 17 Oct → Jagdish again. Nobody changed Jagdish's row.

### 3. The attendant's route today

Protects every trip URL: an attendant may only see and tap their own route of today.

```sql
select r.id
from app_user u
join lateral (
    select distinct on (va.vehicle_id) va.vehicle_id, va.staff_id
    from vehicle_assignment va
    where va.duty = 'ATTENDANT'
      and va.from_date <= :day and (va.to_date is null or va.to_date >= :day)
    order by va.vehicle_id, va.temporary desc, va.from_date desc
) today on today.staff_id = u.staff_id
join route r on r.vehicle_id = today.vehicle_id and r.active
where u.id = :userId;
```

No row → `noTripReason: NOT_ASSIGNED`. Example: Balwan (user 14) on 7 Oct → Route 4.

### 4. The family of a child

```sql
select guardian_id as family_id from student_guardian where student_id = :studentId and is_primary;
-- all children of a family:
select sg.student_id from student_guardian sg join student s on s.id = sg.student_id
where sg.guardian_id = :familyId and sg.is_primary and s.status = 'ACTIVE';
```

Example: Aryan (57) and Siya (12) both have Rajesh Punia (210) as primary → family 210.

### 5. The status of a month

`v_fee_month` gives, for each child and month: what is due, what is paid, the balance, the due date and the grace date.
The Java service picks the status with today's date:

| If | Status | Example on 7 Oct (grace until the 10th) |
|---|---|---|
| `balance = 0` | PAID | Aryan, August |
| `paid_school + paid_bus > 0` | PART_PAID | |
| today > `grace_date` | LATE (late fee if paid now) | Aryan, September |
| today >= `due_date` | DUE | Aryan, October |
| otherwise | NOT_DUE | Aryan, November |
| no `fee_due` row | NO_FEE | Riya, before she joined |

Late fee of a LATE month, when `late_fee_kind` is PER_MONTH: `late_fee_per_month` (if no LATE line is paid for it yet).
When PER_DAY: `(today − grace_date) × late_fee_per_day`, at most `late_fee_max_per_month`.

The fee status of a child for Analytics (ON_TIME / DELAYED / DEFAULTED): find the oldest month with a balance.
Days late = today − its due date. Up to the grace days → ON_TIME; up to `fees.defaulted_after_days` (60) → DELAYED; more → DEFAULTED.

### 6. What each family owes today ("Families with dues", "Pending now")

```sql
with fam as (
    select sg.guardian_id as family_id, sg.student_id
    from student_guardian sg join student s on s.id = sg.student_id
    where sg.is_primary and s.status = 'ACTIVE'
), open_month as (
    select f.family_id, v.*, sfs.late_fee_kind, sfs.late_fee_per_month
    from fam f
    join v_fee_month v on v.student_id = f.student_id
    join session_fee_setting sfs on sfs.session_id = v.session_id
    where v.session_id = :sessionId and v.balance > 0
)
select family_id,
       sum(balance) filter (where due_date <= :day)                                as due_now,
       sum(case when late_fee_kind = 'PER_MONTH' and :day > grace_date and paid_late = 0
                then late_fee_per_month else 0 end)                                 as late_fee,
       count(distinct month) filter (where :day > grace_date)                      as late_months
from open_month
group by family_id;
```

Example on 7 Oct: Punia family → due 15,200 + late fee 200 = **15,400**, 1 late month. Chauhan → 21,100, 4 late months.
"Families with dues" shows the families with `late_months > 0`. "From last year" is the same query with the old session.

### 7. Save a payment (POST /fees/payments), in one transaction

1. If `client_request_id` is already in `receipt`: return that receipt and stop (a double click).
2. Lock the `fee_due` rows of the picked months: `select ... for update`. If a month is already paid → 409 MONTH_ALREADY_PAID.
3. Count the late fee (normal), take `lateFeeOverride` if sent (check the role is in `late_fee_change_roles`).
4. `number_counter` 'RECEIPT-2026': `select ... for update`, +1 → `R-2026-1189`.
5. Insert `receipt`.
6. Insert the lines in `fee_payment`: late fee first, then month by month (school, then bus), child by child, until the money ends.
   If the money ends inside a month, that month is PART_PAID.
7. If family credit is used: a minus row in `family_credit_entry`, and `credit_used` on the receipt.
8. If "send on WhatsApp": a QUEUED row in `message_outbox` with `receipt_id`.
9. One `audit_log` row (and one more for a changed late fee, with the reason).

Check: `receipt.amount + receipt.credit_used = sum(fee_payment.amount)` for every receipt.

### 8. Cancel a receipt (POST /receipts/{no}/cancel)

1. `update receipt set status = 'CANCELLED', cancelled_at, cancelled_by, cancel_reason, cancel_note`.
2. For every line of the receipt: insert a copy with `amount = −amount` and `reversal_of_id = line.id`.
   (`reversal_of_id` is unique, so a line can never be reversed twice.)
3. The months are unpaid again on their own, because `v_fee_month` sums the lines.
4. If asked: a RECEIPT_CANCELLED message in `message_outbox`.

### 9. A bus change (PUT /students/{id}/transport)

1. Find the row that covers `fromDate`. Set its `to_date = fromDate − 1 day`.
   If that row starts on `fromDate` itself and has no taps or payments, update it instead.
2. Insert the new row from `fromDate` (with or without a bus). Its id is the `changeId`.
3. Rewrite `fee_due.bus_amount` of the **unpaid** months from that month on.
4. Undo (DELETE .../changes/{changeId}): delete the new row, set the row before it back to its old `to_date`, rewrite the unpaid months again.

### 10. Start the new school year (POST /promotion/start), in one transaction

1. For each active child who joined on or before the last day of the old session:
   - a `promotion_choice` row decides; no row = move up one class, same section. Class 12 with no row → FINISHED.
   - save `from_class` and `from_section` in the choice (make a MOVE_UP row for those who had none).
2. NOT_COMING_BACK children: `status = 'LEFT'`, `left_on` = the last day of the old session, their bus rows end.
3. Make `fee_plan` and `fee_due` rows for the new session from its `class_fee` and `session_fee_setting`.
4. `promotion_run.status = 'STARTED'`, `undo_until` = midnight.
5. Undo: put class and section back from the choices, delete the new session's `fee_plan` and `fee_due` rows (409 if a payment is already saved in the new session), status back to DRAFT.
