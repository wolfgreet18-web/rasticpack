// نسخه‌ی فایل‌محور testConnection.node-only.mjs — فقط برای seed مرحله‌ای
// تست فشار فاز ۷ (stress-test-phase7.mjs) که به‌خاطر محدودیت زمانی هر
// دستور در این sandbox، باید در چند اجرای جدا (chunk) روی یک فایل واحد
// انجام شود؛ testConnection.node-only.mjs اصلی (:memory:) برای این کار
// مناسب نیست چون هر اجرای Node حافظه‌اش را از دست می‌دهد.
import { DatabaseSync } from 'node:sqlite';
import { SCHEMA_STATEMENTS, ensureCustomersFtsBackfilled, ensureInvoiceItemsBackfilled } from '../rasticpack-capacitor/src/db/schema.js';
import fs from 'node:fs';

export async function getFileDb(path) {
  const isNew = !fs.existsSync(path);
  const raw = new DatabaseSync(path);
  raw.exec('PRAGMA journal_mode = WAL;');
  raw.exec('PRAGMA synchronous = OFF;'); // فقط برای seed سریع تست؛ در production استفاده نمی‌شود
  if (isNew) {
    for (const stmt of SCHEMA_STATEMENTS) raw.exec(stmt);
  }
  const wrapped = {
    async query(sql, params = []) {
      const stmt = raw.prepare(sql);
      return { values: stmt.all(...params) };
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
  if (isNew) {
    await ensureCustomersFtsBackfilled(wrapped);
    await ensureInvoiceItemsBackfilled(wrapped); // ✅ ۴.۲۸ — بی‌اثر روی فایل تازه (invoices خالی است)
  }
  return wrapped;
}
