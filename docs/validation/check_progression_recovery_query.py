"""Focused SQLite check of the actual Room recovery queries, without device data."""
from pathlib import Path
import json
import re
import sqlite3

root = Path(__file__).resolve().parents[2]
schema = json.loads((root / 'core/database/schemas/com.ironlog.app.data.local.IronLogDatabase/11.json').read_text())
db = sqlite3.connect(':memory:')
entities = {e['tableName']: e for e in schema['database']['entities']}
for name, entity in entities.items():
    db.execute(entity['createSql'].replace('${TABLE_NAME}', name))

def insert(name, values):
    record = {}
    for field in entities[name]['fields']:
        if field.get('notNull'):
            record[field['columnName']] = '' if field['affinity'] == 'TEXT' else 0
    record.update(values)
    columns = ','.join('"' + c + '"' for c in record)
    db.execute(f'INSERT INTO {name} ({columns}) VALUES ({",".join("?" for _ in record)})', tuple(record.values()))

for ident in range(1, 11):
    insert('workout_sessions', dict(id=ident, endTime=ident * 100, planId=1))
    insert('workout_plan_targets', dict(id=ident, sessionId=ident, planId=1, exerciseId=1,
        targetWeightKg=0, progressionScheme='MANUAL' if ident == 9 else 'LINEAR', progressionRuleRevision=1))
for ident in range(2, 9):
    values = dict(id=ident, sourceSessionId=ident, sourceTargetSnapshotId=ident, sourceWeightKg=0,
        sourceProgressionRuleRevision=1, status='INFORMATIONAL', wasEdited=0,
        outcomeType='INSUFFICIENT_DATA', reasonCode='MANUAL_WEIGHT_DEVIATION',
        reasonArgumentsJson='{"actualWeightKg":45.0,"expectedWeightKg":0.0}')
    if ident in (3, 4):
        values.update(outcomeType='KEEP_TARGET', reasonCode='REPEAT_TARGET',
            reasonArgumentsJson='{}' if ident == 3 else '{"actualWeightKg":45.0}')
    if ident == 5: values['status'] = 'PENDING'
    if ident == 6: values['status'] = 'ACCEPTED'
    if ident == 7: values['wasEdited'] = 1
    if ident == 8: values['sourceWeightKg'] = 45.0
    insert('progression_suggestions', values)

source = (root / 'core/database/src/main/java/com/ironlog/app/data/local/dao/ProgressionDao.kt').read_text()
queries = dict((name, sql) for sql, name in re.findall(r'@Query\(\s*"""(.*?)"""\s*\)\s*suspend fun (\w+)', source, re.S))
all_rows = [r[0] for r in db.execute(queries['getCompletedSessionIdsWithMissingOutcomes'])]
before_rows = [r[0] for r in db.execute(queries['getCompletedSessionIdsWithMissingOutcomesBefore'], {'sourceEndTime': 500, 'sourceSessionId': 5})]
assert all_rows == [1, 2, 3, 10], all_rows
assert before_rows == [1, 2, 3], before_rows
print('Recovery SQL: missing + legacy candidates selected; current/decided/edited/nonzero/manual rows excluded; chronological bound passed.')
