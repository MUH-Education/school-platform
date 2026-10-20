-- Phase 4 follow-up: the time a route is due at school. Shown on Bus status as "school.due".
-- One time for the morning run (arriving at school) and one for the evening run (leaving school
-- to start the drop-off). Null until the office fills it in, like route_stop's own times.
alter table route add column school_due_morning time;
alter table route add column school_due_evening time;
