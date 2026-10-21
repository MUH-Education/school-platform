#!/usr/bin/env python3
"""Write docs/03-data-model.md and the diagrams from a real database.

The database must have every migration, later/marks.sql and the sample data
(tools/make-docs.sh does that). Connection: the usual PGHOST, PGPORT, PGUSER, PGPASSWORD,
and the database name as the first argument.

    python3 tools/make_data_model.py school_doc
"""
import json
import re
import subprocess
import sys
from collections import OrderedDict, defaultdict
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / 'docs'
SQL_DIRS = [ROOT / 'src/main/resources/db/migration', ROOT / 'src/main/resources/db/later']
WHO_COLUMNS = {'created_by', 'updated_by', 'changed_by', 'taken_by', 'cancelled_by', 'added_by', 'removed_by',
               'uploaded_by', 'made_by', 'recorded_by', 'marked_by', 'started_by', 'undone_by', 'entered_by',
               'late_fee_changed_by'}
HIDE_IN_EXAMPLES = {'created_at', 'updated_at'}


def q(db, sql):
    out = subprocess.run(['psql', '-d', db, '-At', '-v', 'ON_ERROR_STOP=1', '-c', sql],
                         check=True, capture_output=True, text=True).stdout.strip()
    return json.loads(out, parse_float=str) if out else []


def type_text(c):
    u = c['udt_name']
    if u == 'varchar':
        return f"varchar({c['character_maximum_length']})" if c['character_maximum_length'] else 'varchar'
    if u == 'numeric':
        return f"numeric({c['numeric_precision']},{c['numeric_scale']})"
    return {'int8': 'bigint', 'int4': 'int', 'int2': 'smallint', 'bool': 'boolean', 'timestamptz': 'timestamptz',
            'date': 'date', 'time': 'time', 'jsonb': 'jsonb', 'text': 'text', '_int4': 'int[]',
            '_varchar': 'varchar[]', 'bpchar': 'char'}.get(u, u)


def mermaid_type(t):
    return re.sub(r'[^a-z_]', '', t.replace('[]', '_list').split('(')[0]) or 'value'


def column_comments():
    """Read the '-- comment' at the end of column lines in the SQL files."""
    out = {}
    for d in SQL_DIRS:
        for f in sorted(d.glob('*.sql')):
            table = None
            for line in f.read_text(encoding='utf-8').splitlines():
                m = re.match(r'\s*create table (\w+)', line)
                if m:
                    table = m.group(1)
                    continue
                if table and line.startswith(');'):
                    table = None
                    continue
                if table:
                    m = re.match(r'\s+(\w+)\s+[a-z].*?--\s*(.+)$', line)
                    if m and m.group(1) not in ('constraint', 'check', 'exclude'):
                        out[(table, m.group(1))] = m.group(2).strip()
    return out


def main():
    db = sys.argv[1] if len(sys.argv) > 1 else 'school_doc'
    notes = yaml.safe_load((Path(__file__).parent / 'tables.yaml').read_text(encoding='utf-8'))
    comments = column_comments()

    cols = q(db, """select coalesce(json_agg(c order by table_name, ordinal_position), '[]') from (
        select table_name, column_name, ordinal_position, is_nullable, column_default, udt_name,
               character_maximum_length, numeric_precision, numeric_scale, is_identity
        from information_schema.columns where table_schema = 'public') c""")
    fks = q(db, """select coalesce(json_agg(x), '[]') from (
        select c.conrelid::regclass::text as child, c.confrelid::regclass::text as parent,
               (select array_agg(a.attname order by k.ord) from unnest(c.conkey) with ordinality k(n, ord)
                join pg_attribute a on a.attrelid = c.conrelid and a.attnum = k.n) as cols
        from pg_constraint c where c.contype = 'f') x""")
    checks = q(db, """select coalesce(json_agg(x), '[]') from (
        select c.conrelid::regclass::text as tbl, c.conname as name, c.contype as type, pg_get_constraintdef(c.oid) as def
        from pg_constraint c join pg_class r on r.oid = c.conrelid join pg_namespace n on n.oid = r.relnamespace
        where n.nspname = 'public' and c.contype in ('c', 'x', 'u')) x""")
    indexes = q(db, """select coalesce(json_agg(x), '[]') from (
        select tablename as tbl, indexname as name, indexdef as def from pg_indexes where schemaname = 'public') x""")
    view_def = q(db, "select to_json(pg_get_viewdef('v_fee_month'::regclass, true))")

    by_table = defaultdict(list)
    for c in cols:
        by_table[c['table_name']].append(c)
    fk_of = {}
    for f in fks:
        if len(f['cols']) == 1:
            fk_of[(f['child'], f['cols'][0])] = f['parent']
        else:
            for col in f['cols']:
                fk_of.setdefault((f['child'], col), f['parent'] + ' (with ' + ', '.join(x for x in f['cols'] if x != col) + ')')
    values = {}
    other_checks = defaultdict(list)
    for ch in checks:
        d = ch['def']
        m = re.match(r"CHECK \(\(?\(?(\w+)\)?(?:::text)? = ANY \(\(?ARRAY\[(.*)\]", d)
        if ch['type'] == 'c' and m and d.count('ANY') == 1 and ' OR ' not in d and ' AND ' not in d:
            vals = re.findall(r"'([^']*)'", m.group(2))
            values[(ch['tbl'], m.group(1))] = vals
            continue
        m2 = re.match(r"CHECK \(\(?\(?(\w+)\)? (>=|>|<>|<=|<) \(?(-?[0-9.]+)\)?(?:::\w+)?\)?\)$", d)
        if ch['type'] == 'c' and m2:
            values.setdefault((ch['tbl'], m2.group(1)), f'{m2.group(2)} {m2.group(3)}')
            continue
        if ch['type'] == 'c' and ch['name'].endswith('_check') and d.count('ANY') == 1 and ' AND ' in d:
            # in(...) and cardinality, for arrays
            pass
        other_checks[ch['tbl']].append((ch['name'], d))
    uniq = defaultdict(list)
    constraint_names = {ch['name'] for ch in checks}
    for ix in indexes:
        if '_pkey' in ix['name'] or ix['name'] in constraint_names:
            continue
        other_checks[ix['tbl']].append((ix['name'], ix['def'].replace(' ON public.', ' ON ').replace('USING btree ', '')))

    first_tables = [t for a in notes['areas'] if a['phase'] != 'later' for t in a['tables']]
    later_tables = [t for a in notes['areas'] if a['phase'] == 'later' for t in a['tables']]
    missing = [t for t in by_table if t not in first_tables + later_tables and not t.startswith('v_')]
    if missing:
        raise SystemExit(f'Tables with no notes in tables.yaml: {missing}')

    # ---------------- diagrams
    (DOCS / 'diagrams').mkdir(parents=True, exist_ok=True)

    def links(table_set, keep_outside=True):
        out = []
        seen = set()
        for f in fks:
            col = f['cols'][0]
            if col in WHO_COLUMNS:
                continue
            if f['child'] not in table_set and f['parent'] not in table_set:
                continue
            if not keep_outside and (f['child'] not in table_set or f['parent'] not in table_set):
                continue
            key = (f['parent'], f['child'], col)
            if key in seen or f['child'] == f['parent']:
                continue
            seen.add(key)
            out.append(f"    {f['parent']} ||--o{{ {f['child']} : {col}")
        return out

    def area_diagram(area):
        lines = ['erDiagram']
        tset = set(area['tables'])
        for t in area['tables']:
            lines.append(f'    {t} {{')
            for c in by_table[t]:
                if c['column_name'] in HIDE_IN_EXAMPLES:
                    continue
                mark = ' PK' if c['column_name'] == 'id' or (t, c['column_name']) == ('app_setting', 'key') or (t, c['column_name']) == ('number_counter', 'counter_key') else (' FK' if (t, c['column_name']) in fk_of else '')
                lines.append(f"        {mermaid_type(type_text(c))} {c['column_name']}{mark}")
            lines.append('    }')
        lines += links(tset)
        return '\n'.join(lines)

    # The big picture: one box per area, a node per table, and the links between tables.
    # Links to academic_session are left out (nearly every fee table has one).
    overview = ['flowchart LR']
    for a in notes['areas']:
        if a['phase'] == 'later':
            continue
        overview.append(f'    subgraph {a["key"]}["{a["title"]}"]')
        for t in a['tables']:
            overview.append(f'        {t}[{t}]')
        overview.append('    end')
    seen = set()
    for f in fks:
        col = f['cols'][0]
        if col in WHO_COLUMNS or f['parent'] == 'academic_session' or f['child'] == f['parent']:
            continue
        if f['child'] not in first_tables or f['parent'] not in first_tables:
            continue
        if (f['parent'], f['child']) in seen:
            continue
        seen.add((f['parent'], f['child']))
        overview.append(f"    {f['parent']} --> {f['child']}")
    diagrams = OrderedDict([('overview', '\n'.join(overview))])
    for a in notes['areas']:
        diagrams[a['key']] = area_diagram(a)
    for k, text in diagrams.items():
        (DOCS / 'diagrams' / f'{k}.mmd').write_text(text + '\n', encoding='utf-8')

    # ---------------- markdown
    def esc(s):
        return str(s).replace('|', '\\|').replace('\n', ' ')

    def example_rows(t, where):
        rows = q(db, f"select coalesce(json_agg(x), '[]') from (select * from {t} where {where} order by 1 limit 4) x")
        out = []
        for r in rows:
            bits = []
            for k, v in r.items():
                if k in HIDE_IN_EXAMPLES or v is None or v == '' or v is False and k not in ('uses_bus', 'temporary', 'active'):
                    continue
                if isinstance(v, bool):
                    v = 'true' if v else 'false'
                elif isinstance(v, (dict, list)):
                    v = json.dumps(v, ensure_ascii=False)
                v = str(v)
                if re.match(r'^\d{4}-\d{2}-\d{2}T', v):
                    v = v.replace('T', ' ')[:16]
                if len(v) > 70:
                    v = v[:67] + '...'
                bits.append(f'`{k}` {v}')
            out.append('- ' + ' · '.join(bits))
        return out

    parts = []
    intro = (Path(__file__).parent / 'part-intro.md').read_text(encoding='utf-8')
    map_rows = ['| Flyway file | Phase | Tables |', '|---|---|---|']
    for a in notes['areas']:
        map_rows.append(f"| `{a['file']}` | {a['phase']} | {', '.join('`' + t + '`' for t in a['tables'])} |")
    map_rows.append('| `V7__fees.sql` | 7 | view `v_fee_month` |')
    intro = intro.replace('{{TABLES}}', str(len(first_tables))).replace('{{LATER}}', str(len(later_tables)))
    intro = intro.replace('{{MAP}}', '\n'.join(map_rows))
    intro = intro.replace('{{OVERVIEW}}', '```mermaid\n' + diagrams['overview'] + '\n```\n\nAlso as a picture: `docs/diagrams/overview.svg`.')
    parts.append(intro)

    for a in notes['areas']:
        parts.append(f"\n---\n\n## {a['title']}\n")
        parts.append(f"Flyway file: `{a['file']}`" + (f" · Phase {a['phase']}" if a['phase'] != 'later' else ' · **Later**, not in the first release') + '\n')
        parts.append('```mermaid\n' + diagrams[a['key']] + '\n```\n')
        for t in a['tables']:
            n = notes['tables'][t]
            parts.append(f"### `{t}`\n")
            parts.append(n['purpose'] + '\n')
            rows = ['| Column | Type | Can be empty | Values or limits | Note |', '|---|---|---|---|---|']
            for c in by_table[t]:
                col = c['column_name']
                if col in ('created_at', 'updated_at'):
                    continue
                note = comments.get((t, col), '')
                if (t, col) in fk_of:
                    note = f"→ `{fk_of[(t, col)]}`" + (f'. {note}' if note else '')
                if col == 'id' and c['is_identity'] == 'YES':
                    note = 'identity'
                v = values.get((t, col))
                vtext = ', '.join(f'`{x}`' for x in v) if isinstance(v, list) else (f'`{v}`' if v else '')
                default = c['column_default']
                if default and 'nextval' not in default:
                    d = re.sub(r"::[a-z ]+(\(\d+\))?(\[\])?", '', default).replace("'", '')
                    vtext = (vtext + f' · default `{d}`').strip(' ·')
                rows.append(f"| `{col}` | {type_text(c)} | {'yes' if c['is_nullable'] == 'YES' else ''} | {esc(vtext)} | {esc(note)} |")
            parts.append('\n'.join(rows) + '\n')
            parts.append('Plus `created_at` and `updated_at`.\n')
            if n.get('rules'):
                parts.append('**Rules**\n')
                parts.append('\n'.join(f'- {r}' for r in n['rules']) + '\n')
            oc = other_checks.get(t, [])
            if oc:
                parts.append('<details><summary>Constraints and indexes in the database</summary>\n')
                parts.append('\n'.join(f'- `{name}`: `{esc(d)}`' for name, d in oc))
                parts.append('\n</details>\n')
            if n.get('example'):
                ex = example_rows(t, n['example'])
                if ex:
                    parts.append('**Example rows** (from the sample data)\n')
                    parts.append('\n'.join(ex) + '\n')
            if n.get('used_by'):
                parts.append('**Used by:** ' + ', '.join(f'`{u}`' for u in n['used_by']) + '\n')
        if a['key'] == 'fees':
            parts.append('### View `v_fee_month`\n')
            parts.append('Each `fee_due` row with what is paid for it, its balance, its due date and its grace date. '
                         'The Java service adds today\'s date to get the status (see "The status of a month" below).\n')
            parts.append('```sql\n' + view_def.strip() + '\n```\n')
            ex = example_rows('v_fee_month', "student_id = 57 and month between '2026-08-01' and '2026-11-01'")
            parts.append('**Example rows** (Aryan, August to November, on 7 Oct)\n')
            parts.append('\n'.join(ex) + '\n')

    parts.append('\n---\n')
    parts.append((Path(__file__).parent / 'part-queries.md').read_text(encoding='utf-8'))
    parts.append('\n---\n')
    checks_sql = (ROOT / 'db-checks' / 'checks.sql').read_text(encoding='utf-8')
    n_expect = checks_sql.count('perform pg_temp.expect(')
    n_fail = checks_sql.count('perform pg_temp.must_fail(')
    end = (Path(__file__).parent / 'part-end.md').read_text(encoding='utf-8')
    end = end.replace('{{CHECKS}}', str(n_expect + n_fail)).replace('{{REFUSED}}', str(n_fail))
    parts.append(end)

    (DOCS / '03-data-model.md').write_text('\n'.join(parts), encoding='utf-8')
    print(f'Wrote docs/03-data-model.md: {len(first_tables)} tables + {len(later_tables)} later, {len(diagrams)} diagrams, {n_expect + n_fail} checks')


if __name__ == '__main__':
    main()
