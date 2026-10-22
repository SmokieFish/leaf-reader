"""Exercise the actual additive schema SQL on SQLite, without pretending to run Android."""
from pathlib import Path
import re
import sqlite3
import tempfile

src = (Path(__file__).parents[1]/'src/com/leafreader/app/Store.java').read_text()
schema = re.search(r'TERMS_SCHEMA="([^"]+)"', src).group(1)
legacy = re.findall(r'd.execSQL\("(CREATE TABLE [^"]+)"\)', src)
assert len(legacy) == 3
assert 'super(c,"reader.db",null,2)' in src
assert 'onUpgrade(SQLiteDatabase d,int a,int b){if(a<2)d.execSQL(TERMS_SCHEMA);}' in src
count = 0

def check(ok, label):
    global count
    assert ok, label
    count += 1
    print('PASS:', label)

with tempfile.TemporaryDirectory() as folder:
    path = str(Path(folder)/'reader.db')
    db = sqlite3.connect(path)
    for sql in legacy:
        db.execute(sql)
    db.execute('PRAGMA user_version=1')
    db.execute('INSERT INTO books VALUES (?,?,?,?,?,?,?,?)', ('book-a','Book','Author','EPUB',42,100,10,1))
    db.execute('INSERT INTO translations VALUES (?,?,?,?)', ('book-a','profile-a',42,'已有的付费译文'))
    db.execute('INSERT INTO history VALUES (?,?,?,?,?)', ('visit','book-a','Book',42,10))
    before = {table: db.execute('SELECT * FROM '+table).fetchall() for table in ['books','translations','history']}
    db.execute(schema)
    db.execute(schema)
    db.execute('PRAGMA user_version=2')
    check(all(db.execute('SELECT * FROM '+table).fetchall()==data for table,data in before.items()), 'additive migration retains books, positions, paid translations and history')
    check(db.execute('SELECT COUNT(*) FROM translation_terms').fetchone()[0]==0, 'new term table starts empty and migration SQL is idempotent')
    sql='INSERT OR IGNORE INTO translation_terms VALUES (?,?,?,?)'
    db.execute(sql, ('book-a','profile-a','Rusty','小锈'))
    db.execute(sql, ('book-a','profile-a','RUSTY','错误覆盖'))
    check(db.execute('SELECT target FROM translation_terms WHERE book=? AND profile=?',('book-a','profile-a')).fetchall()==[('小锈',)], 'first accepted name is stable even when later requests vary capitalization')
    db.execute(sql, ('book-b','profile-a','Rusty','另一部书'))
    db.execute(sql, ('book-a','profile-b','Rusty','另一种语言'))
    check(db.execute('SELECT COUNT(*) FROM translation_terms').fetchone()[0]==3, 'term memory is isolated by book and translation profile')
    db.commit();db.close();db=sqlite3.connect(path)
    check(db.execute('SELECT target FROM translation_terms WHERE book=? AND profile=?',('book-a','profile-a')).fetchone()[0]=='小锈','name memory survives database close and reopen')
    db.execute('DELETE FROM translation_terms WHERE book=?', ('book-a',))
    check(db.execute('SELECT book FROM translation_terms').fetchall()==[('book-b',)], 'deleting one book can remove only its term memories')
    db.close()
print(f'{count} SQLite schema migration checks passed.')
