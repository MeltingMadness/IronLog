"""Focused SQLite check of the actual Room recovery queries, without device data."""
from pathlib import Path
import json
import re
import sqlite3

root = Path(__file__).resolve().parents[2]
schema = json.loads((root / 'core/database/schemas/com.ironlog.app.data.local.IronLogDatabase/15.json').read_text())
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

# Deloads are absent from both recovery and failure-history queries; legacy unknown
# context remains eligible. Execute the Room query text rather than a second SQL model.
db.execute('UPDATE workout_sessions SET isDeload = 1 WHERE id = 1')
assert [r[0] for r in db.execute(queries['getCompletedSessionIdsWithMissingOutcomes'])] == [2, 3, 10]
assert [r[0] for r in db.execute(queries['getCompletedSessionIdsWithMissingOutcomesBefore'],
    {'sourceEndTime': 500, 'sourceSessionId': 5})] == [2, 3]
history_args = dict(planId=1, exerciseId=1, orderIndex=0, sourceEndTime=500, sourceSessionId=5)
assert [r[0] for r in db.execute(queries['getPreviousTargets'], history_args)] == [4, 3, 2]

args = dict(planId=1, exerciseId=1, orderIndex=0, sourceEndTime=100, sourceSessionId=1)
freshness = queries['hasNewerCompletedWork']
def superseded(**changes):
    return bool(db.execute(freshness, args | changes).fetchone()[0])

assert not superseded()  # An unused plan position is not new evidence.
insert('workout_sets', dict(id=100, sessionId=3, exerciseId=1, planTargetSnapshotId=3,
    setNumber=1, reps=7, weightKg=100, setType='NORMAL'))
assert superseded()  # The outcome is INFORMATIONAL, not PENDING.
assert not superseded(planId=2)
assert not superseded(exerciseId=2)
assert not superseded(orderIndex=1)
assert superseded(sourceEndTime=300, sourceSessionId=2)
assert not superseded(sourceEndTime=300, sourceSessionId=3)
db.execute('UPDATE workout_sessions SET isDeload = 1 WHERE id = 3')
assert not superseded()
db.execute('UPDATE workout_sessions SET isDeload = NULL WHERE id = 3')
assert superseded()
db.execute("UPDATE workout_sets SET setType = 'WARMUP' WHERE id = 100")
assert not superseded()
db.execute("UPDATE workout_sets SET setType = 'FAILURE' WHERE id = 100")
assert superseded()  # Recent failed work must also prevent replaying old advice.
db.execute('UPDATE workout_sessions SET endTime = NULL WHERE id = 3')
assert not superseded()
print('Freshness SQL: newer work supersedes advice; deloads, warmups, unused positions and unfinished sessions do not; completion-time ties and legacy context passed.')
