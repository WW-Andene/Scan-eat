#!/usr/bin/env python3
"""Replays the Kotlin Room migrations in SQLite and diffs the result against the exported schema.

Usage: tools/check-migrations.py [start_version=15]
Builds the DB from schemas/<start>.json createSql, applies every MIGRATION_n_n+1 found in
AppDatabase.kt (execSQL literals only; reports any dynamic migration it cannot replay), then compares
tables/columns/types/defaults/indices with the latest schema. No Android SDK needed.
Note: snapshots 20-24 predate the scan_history category index created by 15->16, so start at 15.
"""
import re, json, sqlite3, sys, os, glob
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app')
src = open(os.path.join(ROOT, 'src/main/java/fr/scanneat/data/local/db/AppDatabase.kt'), encoding='utf-8').read()
schemas = os.path.join(ROOT, 'schemas') + '/'
d = glob.glob(schemas + '*/')[0]
def load(v): return json.load(open(d + f'{v}.json'))['database']
# migrations
migs = {}
for m in re.finditer(r'val MIGRATION_(\d+)_(\d+) = object : Migration\(\d+, \d+\) \{', src):
    a, b = int(m.group(1)), int(m.group(2))
    start = m.end()
    nxt = re.search(r'\n\}\n', src[start:])
    body = src[start:start + nxt.start()]
    stmts = []
    for e in re.finditer(r'execSQL\(', body):
        i = e.end(); depth = 1; j = i
        while depth:
            c = body[j]
            if c == '"':
                j += 1
                while body[j] != '"':
                    if body[j] == '\\': j += 1
                    j += 1
            elif c == '(': depth += 1
            elif c == ')': depth -= 1
            j += 1
        arg = body[i:j-1]
        lits = re.findall(r'"((?:[^"\\]|\\.)*)"', arg)
        stripped = re.sub(r'"((?:[^"\\]|\\.)*)"|\s|\+', '', arg)
        stmts.append((''.join(l.replace('\\"', '"').replace('\\$', '$').replace('\\n', '\n') for l in lits), stripped != ''))
    other = [k for k in ('query(', 'rawQuery', 'for (', 'forEach', 'if (') if k in body]
    migs[(a, b)] = (stmts, other)
print('migrations parsed:', len(migs), 'versions', min(a for a,_ in migs), '->', max(b for _,b in migs))
missing = [v for v in range(15, 40) if (v, v+1) not in migs]
print('missing consecutive migrations from 15..40:', missing)
dyn = {k: v for k, v in migs.items() if any(s[1] for s in v[0]) or v[1]}
print('migrations with dynamic parts (not replayable):', sorted(dyn))

START = int(sys.argv[1]) if len(sys.argv)>1 else 15
db = sqlite3.connect(':memory:')
sch = load(START)
for ent in sch['entities']:
    db.execute(ent['createSql'].replace('${TABLE_NAME}', ent['tableName']))
    for ix in ent.get('indices', []):
        db.execute(ix['createSql'].replace('${TABLE_NAME}', ent['tableName']))
for v in range(START, 40):
    stmts, other = migs[(v, v+1)]
    for s, dynamic in stmts:
        if dynamic: print(f'  v{v}->{v+1}: skipping dynamic stmt'); continue
        try: db.execute(s)
        except Exception as ex: print(f'  v{v}->{v+1} FAILED: {ex}\n    {s[:120]}'); 
def tinfo(conn):
    out = {}
    for (n,) in conn.execute("select name from sqlite_master where type='table' and name not in ('sqlite_sequence','room_master_table','android_metadata')"):
        out[n] = {r[1]: (r[2].upper(), r[3], r[4], r[5]) for r in conn.execute(f'pragma table_info(`{n}`)')}
    return out
mine = tinfo(db)
exp = sqlite3.connect(':memory:')
t = load(40)
for ent in t['entities']:
    exp.execute(ent['createSql'].replace('${TABLE_NAME}', ent['tableName']))
want = tinfo(exp)
bad = 0
for n in sorted(set(mine) | set(want)):
    if n not in mine: print('TABLE MISSING after migration:', n); bad += 1; continue
    if n not in want: print('EXTRA TABLE after migration (not in v40):', n); bad += 1; continue
    for c in sorted(set(mine[n]) | set(want[n])):
        if c not in mine[n]: print(f'COLUMN MISSING {n}.{c}'); bad += 1
        elif c not in want[n]: print(f'EXTRA COLUMN {n}.{c}'); bad += 1
        elif mine[n][c][:2] != want[n][c][:2] or mine[n][c][3] != want[n][c][3]:
            print(f'COLUMN DIFF {n}.{c}: migrated={mine[n][c]} expected={want[n][c]}'); bad += 1
# indices
def idx(conn): return {r[0] for r in conn.execute("select name from sqlite_master where type='index' and name not like 'sqlite_%'")}
want_idx = set()
for ent in t['entities']:
    for ix in ent.get('indices', []): want_idx.add(ix['name'])
mi = idx(db)
print('indices missing after migration:', sorted(want_idx - mi))
print('indices extra after migration:', sorted(mi - want_idx))
print('structural differences:', bad)
sys.exit(1 if bad or (want_idx - mi) else 0)
