// این فایل فقط برای تست محلی (Node) است — جایگزین connection.js در production
// نیست. همان قرارداد query/run/withTransaction را با موتور واقعی SQLite
// (node:sqlite، نه mock دستی) پیاده می‌کند تا SQL واقعیِ InvoiceRepo/CustomerRepo/
// migrateFromIndexedDB با یک دیتابیس واقعی صحت‌سنجی شود.
import { DatabaseSync } from 'node:sqlite';
import { SCHEMA_STATEMENTS, ensureCustomersFtsBackfilled, ensureInvoiceItemsBackfilled } from '../rasticpack-capacitor/src/db/schema.js';

let _db = null;

export async function getDb() {
  if (_db) return _db;
  // پروانه‌ی خروج فقط برای stress-test-phase7 (بخش seed مرحله‌ای، به‌خاطر سقف
  // زمانی هر دستور در sandbox): اگر STRESS_DB_PATH ست شده باشد، به‌جای
  // :memory: یک فایل واقعی روی دیسک باز می‌شود (schema فقط اگر فایل جدید بود
  // ساخته می‌شود). رفتار پیش‌فرض بدون این env var کاملاً بدون تغییر است.
  const filePath = process.env.STRESS_DB_PATH;
  const isFileMode = !!filePath;
  const isNewFile = isFileMode && !(await import('node:fs')).existsSync(filePath);
  const raw = new DatabaseSync(filePath || ':memory:');
  if (isFileMode) {
    raw.exec('PRAGMA journal_mode = WAL;');
  }
  if (!isFileMode || isNewFile) {
    for (const stmt of SCHEMA_STATEMENTS) raw.exec(stmt);
  }

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
  await ensureCustomersFtsBackfilled(wrapped);
  await ensureInvoiceItemsBackfilled(wrapped); // ✅ ۴.۲۸
  _db = wrapped;
  return _db;
}

export function resetDb() { _db = null; }
