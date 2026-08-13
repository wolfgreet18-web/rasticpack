/**
 * connection.js — اتصال idempotent به SQLite native (از طریق
 * @capacitor-community/sqlite) + migration اولیه‌ی schema داخل یک تراکنش
 * + یک تابع عمومی withTransaction(fn) برای استفاده در فاز ۵.
 *
 * این فایل کد production است و روی خود مرورگر/Node اجرا نمی‌شود — فقط داخل
 * یک شل Capacitor واقعی (اندروید/iOS) کار می‌کند، چون به window.CapacitorSQLite
 * وابسته است. برای تست منطق InvoiceRepo/CustomerRepo بدون دستگاه واقعی،
 * از یک پیاده‌سازی جایگزین db-like (همان قرارداد query/run) استفاده کنید —
 * نمونه‌اش در تست‌های این پروژه (نگاه کن به کامنت پایین فایل) با node:sqlite
 * نوشته شده، صرفاً برای صحت‌سنجی SQL؛ جایگزین این فایل در production نیست.
 */

import { SCHEMA_STATEMENTS, applySchema, ensureCustomersFtsBackfilled, ensureInvoiceItemsBackfilled } from './schema.js';

const DB_NAME = 'rasticpack';

let _sqlite = null;   // instance CapacitorSQLite (پلاگین)
let _dbConn = null;   // SQLiteDBConnection متصل‌شده
let _connecting = null; // promise در حال اجرا، برای جلوگیری از اتصال موازی

/**
 * اتصال idempotent: اگر قبلاً باز شده، همان instance را برمی‌گرداند؛
 * اگر هم‌زمان چند جا صدا زده شود، همه به یک promise مشترک گره می‌خورند
 * (نه اینکه هرکدام جدا سعی کنند دیتابیس را باز کنند).
 * @returns {Promise<import('@capacitor-community/sqlite').SQLiteDBConnection>}
 */
export async function getDb() {
  if (_dbConn) return _dbConn;
  if (_connecting) return _connecting;

  _connecting = (async () => {
    const { CapacitorSQLite, SQLiteConnection } = await import('@capacitor-community/sqlite');
    _sqlite = new SQLiteConnection(CapacitorSQLite);

    const isConn = (await _sqlite.isConnection(DB_NAME, false)).result;
    const conn = isConn
      ? await _sqlite.retrieveConnection(DB_NAME, false)
      : await _sqlite.createConnection(DB_NAME, false, 'no-encryption', 1, false);

    await conn.open();

    // migration اولیه‌ی schema — همه در یک تراکنش تا یک کرش وسط‌راه
    // جدول‌های نصفه‌کاره نسازد.
    await conn.beginTransaction();
    try {
      for (const stmt of SCHEMA_STATEMENTS) {
        await conn.execute(stmt);
      }
      await conn.commitTransaction();
    } catch (e) {
      await conn.rollbackTransaction();
      throw e;
    }

    _dbConn = wrapConnection(conn);
    // ✅ ۴.۱۸: بعد از commit شدن schema/تریگرها، customers_fts را (فقط یک‌بار،
    // idempotent) از روی داده‌ی موجود پر می‌کند — نگاه کن به schema.js.
    await ensureCustomersFtsBackfilled(_dbConn);
    // ✅ ۴.۲۸: پر کردن یک‌باره‌ی invoice_items از روی invoices.data موجود
    // (نگاه کن به schema.js) — برای اتصال تازه بی‌اثر (idempotent).
    await ensureInvoiceItemsBackfilled(_dbConn);
    return _dbConn;
  })();

  try {
    return await _connecting;
  } finally {
    _connecting = null;
  }
}

/**
 * قرارداد query/run را که InvoiceRepo.js/CustomerRepo.js/additions انتظارش
 * را دارند، روی متدهای واقعی SQLiteDBConnection (query/run) پیاده می‌کند.
 * (پلاگین @capacitor-community/sqlite از قبل query(sql,params)→{values}
 * و run(sql,params)→any را دارد — این wrapper فقط برای شفافیت/تک‌نقطه‌ای
 * بودن قرارداد نگه داشته شده، تا اگر فردا امضای پلاگین عوض شد، فقط همین‌جا
 * تغییر کند.)
 */
function wrapConnection(conn) {
  return {
    async query(sql, params = []) {
      const res = await conn.query(sql, params);
      return { values: res.values || [] };
    },
    async run(sql, params = []) {
      return conn.run(sql, params);
    },
    async withTransaction(fn) {
      await conn.beginTransaction();
      try {
        const result = await fn(this);
        await conn.commitTransaction();
        return result;
      } catch (e) {
        await conn.rollbackTransaction();
        throw e;
      }
    },
    raw: conn
  };
}

/**
 * اجرای یک تابع درون یک تراکنش SQL — برای فاز ۵ (نوشتن‌های چندمرحله‌ای atomic).
 * @param {(db: ReturnType<typeof wrapConnection>) => Promise<any>} fn
 */
export async function withTransaction(fn) {
  const db = await getDb();
  return db.withTransaction(fn);
}

// دوباره صادر می‌کنیم تا کد بالادست (مثلاً migrateFromIndexedDB.js) بدون
// import اضافه به applySchema دسترسی داشته باشد اگر لازم شد (مثلاً برای
// اجرای دستی migration در یک اتصال تازه‌ساز که از مسیر getDb عبور نکرده).
export { applySchema };
