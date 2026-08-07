/**
 * schema.js — تعریف جداول اصلی + ایندکس‌ها (فاز ۱ نقشه‌راه)
 * نسخه‌بندی‌شده با SCHEMA_VERSION برای migration‌های آینده.
 *
 * منبع: بخش ۴ / فاز ۱ سند roadmap-infinite-records-v4.md — این فایل دقیقاً
 * همان SQL مستندشده در آن سند است، به‌علاوه‌ی جداول کوچکِ نام‌برده‌شده
 * (inventory, van_drivers, production_queue, settings_kv, meta) که در سند
 * فقط با اسم اشاره شده بودند و شکل دقیق‌شان اینجا برای اولین‌بار نوشته شده
 * (بر مبنای معنایی که در html8.html استفاده می‌شوند — نگاه کن به یادداشت
 * هر جدول). این پنج جدول «بدون رشد نامحدود»اند طبق تعریف نقشه‌راه.
 */

export const SCHEMA_VERSION = 1;

export const SCHEMA_STATEMENTS = [
  `CREATE TABLE IF NOT EXISTS customers (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL COLLATE NOCASE,
    company TEXT, address TEXT, phone TEXT,
    lat REAL, lng REAL, locationLink TEXT,
    createdAt TEXT DEFAULT (datetime('now'))
  )`,
  `CREATE INDEX IF NOT EXISTS idx_cust_name  ON customers(name)`,
  `CREATE INDEX IF NOT EXISTS idx_cust_phone ON customers(phone)`,

  `CREATE TABLE IF NOT EXISTS invoices (
    id INTEGER PRIMARY KEY,
    customerId INTEGER NOT NULL,
    customerName TEXT NOT NULL,
    date TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'paid',
    sent INTEGER NOT NULL DEFAULT 0,
    sentToProduction INTEGER NOT NULL DEFAULT 0,
    paidAmount REAL,
    totalSheets INTEGER NOT NULL DEFAULT 0,
    editedAt TEXT,
    data TEXT NOT NULL,
    FOREIGN KEY(customerId) REFERENCES customers(id)
  )`,
  `CREATE INDEX IF NOT EXISTS idx_inv_cust        ON invoices(customerId)`,
  `CREATE INDEX IF NOT EXISTS idx_inv_date        ON invoices(date)`,
  `CREATE INDEX IF NOT EXISTS idx_inv_status      ON invoices(status)`,
  `CREATE INDEX IF NOT EXISTS idx_inv_sent        ON invoices(sent)`,
  `CREATE INDEX IF NOT EXISTS idx_inv_cust_date   ON invoices(customerId, date)`,
  `CREATE INDEX IF NOT EXISTS idx_inv_status_date ON invoices(status, date)`,
  // ایندکس پوششی (covering index) برای InvoiceRepo.getStatusCounts وقتی فقط
  // بازه‌ی تاریخ فیلتر می‌شود (بدون customerId) — طبق ریسک کشف‌شده در نسخه‌ی
  // ۴.۴ نقشه‌راه (بخش ۶): روی ۱M رکورد این کوئری ۴۱-۴۵ms طول می‌کشید، خیلی
  // نزدیک به آستانه‌ی ۵۰ms. چون SELECT فقط به date/status/sent نیاز دارد (نه
  // ستون‌های دیگر جدول)، این ایندکس اجازه می‌دهد SQLite کل کوئری را از روی
  // خودِ ایندکس جواب بدهد (index-only scan) بدون رفتن سراغ ردیف اصلی جدول.
  `CREATE INDEX IF NOT EXISTS idx_inv_date_status_sent ON invoices(date, status, sent)`,

  // جداول کوچک، «بدون رشد نامحدود» — شکل دقیق این پنج جدول در سند نقشه‌راه
  // مستند نشده بود؛ اینجا برای اولین بار به‌صورت حداقلی/عملی نوشته شدند تا
  // migrateFromIndexedDB و بقیه‌ی Repo ها بتوانند به آن‌ها تکیه کنند. اگر
  // شکل واقعی این جداول در پروژه‌ی اصلی جای دیگری تعریف شده، این تعاریف
  // باید با آن هم‌راستا/جایگزین شوند.
  `CREATE TABLE IF NOT EXISTS inventory (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    qty REAL NOT NULL DEFAULT 0,
    unit TEXT,
    data TEXT
  )`,
  `CREATE TABLE IF NOT EXISTS van_drivers (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    phone TEXT,
    data TEXT
  )`,
  `CREATE TABLE IF NOT EXISTS production_queue (
    id INTEGER PRIMARY KEY,
    invoiceId INTEGER,
    status TEXT NOT NULL DEFAULT 'pending',
    createdAt TEXT DEFAULT (datetime('now')),
    data TEXT
  )`,
  // key-value ساده برای پرچم‌های سیستمی مثل migrated_v1 (نگاه کن به migrateFromIndexedDB.js)
  `CREATE TABLE IF NOT EXISTS settings_kv (
    key TEXT PRIMARY KEY,
    value TEXT
  )`,
  // متادیتای schema (نسخه‌ی فعلی) — برای migration های ساختاری آینده
  `CREATE TABLE IF NOT EXISTS meta (
    key TEXT PRIMARY KEY,
    value TEXT
  )`,

  // آماده برای فاز ۳ (جستجوی متنی) — غیرفعال/بی‌اثر تا آن فاز، اما تعریفش
  // از پیش اینجا نوشته شده طبق طرح فاز ۱.
  `CREATE VIRTUAL TABLE IF NOT EXISTS customers_fts USING fts5(
    name, company, content='customers', content_rowid='id'
  )`
];

/**
 * تمام statement های schema را روی یک اتصال دیتابیس اجرا می‌کند (idempotent —
 * همه‌ی CREATE ها با IF NOT EXISTS نوشته شده‌اند). این تابع مستقل از نوع
 * اتصال (Capacitor SQLite واقعی یا هر db-like دیگری با متد db.run) است.
 * @param {{run:(sql:string, params?:any[])=>Promise<any>}} db
 */
export async function applySchema(db) {
  for (const stmt of SCHEMA_STATEMENTS) {
    await db.run(stmt);
  }
  await db.run(
    `INSERT INTO meta (key, value) VALUES ('schema_version', ?)
     ON CONFLICT(key) DO UPDATE SET value=excluded.value`,
    [String(SCHEMA_VERSION)]
  );
}
