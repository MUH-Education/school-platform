-- V7's forbid_row_change() raised with errcode 'P0001' (class P0, "plpgsql_error"), which
-- Spring's exception translator does not recognise, so it surfaced as UncategorizedSQLException
-- instead of DataIntegrityViolationException. '23514' (check_violation, class 23) is a class
-- Spring's SQLStateSQLExceptionTranslator maps to DataIntegrityViolationException.
create or replace function forbid_row_change() returns trigger language plpgsql as $$
begin
    raise exception 'Rows of % are never changed or deleted. Add a new row instead.', tg_table_name
        using errcode = '23514';
end;
$$;
