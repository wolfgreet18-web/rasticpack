// این فایل فقط برای تست محلی (Node) است — جایگزین connection.js در production
// نیست. همان قرارداد query/run/withTransaction را با موتور واقعی SQLite
// (node:sqlite، نه mock دستی) پیاده می‌کند تا SQL واقعیِ InvoiceRepo/CustomerRepo/
// migrateFromIndexedDB با یک دیتابیس واقعی صحت‌سنجی شود.
import { DatabaseSync } from 'node:sqlite';
import { SCHEMA_STATEMENTS } from '../rasticpack-capacitor/src/db/schema.js';

let _db = null;

export async function getDb() {
  if (_db) return _db;
  const raw = new DatabaseSync(':memory:');
  for (const stmt of SCHEMA_STATEMENTS) raw.exec(stmt);

  const wrapped = {
    async query(sql, params = []) {
      const stmt = raw.prepare(sql);
      const values = stmt.all(...params);
      return { values };
    },
    async run(sql, params = []) {
      const stmt = raw.prepare(sql);
      return stmt.run(...params);
    },
    async withTransaction(fn) {
      raw.exec('BEGIN');
      try {
        const result = await fn(wrapped);
        raw.exec('COMMIT');
        return result;
      } catch (e) {
        raw.exec('ROLLBACK');
        throw e;
      }
    },
    raw
  };
  _db = wrapped;
  return _db;
}

export function resetDb() { _db = null; }
