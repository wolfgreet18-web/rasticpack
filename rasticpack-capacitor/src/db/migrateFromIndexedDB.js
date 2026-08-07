/**
 * migrateFromIndexedDB.js — مهاجرت یک‌باره از IndexedDB (یا fallback قدیمی‌تر
 * در window.storage['rasticpack-data']) به SQLite native.
 *
 * قوانین طبق فاز ۱ نقشه‌راه:
 *  - batch insert دسته‌ای (۵۰۰تایی) تا فشار حافظه کم بماند
 *  - قبل از حذف هیچ داده‌ای از IndexedDB، یک شمارش تطبیقی انجام می‌شود:
 *    اگر COUNT(*) مقصد (SQLite) < تعداد مبدأ باشد، خطا داده می‌شود و
 *    IndexedDB دست‌نخورده می‌ماند (این پروژه هرگز به‌صورت خودکار داده‌ی
 *    مبدأ را پاک نمی‌کند — فقط پرچم migrated_v1 را ست می‌کند تا دوباره
 *    اجرا نشود؛ حذف واقعی IndexedDB به فاز ۲.۶ موکول شده).
 *  - با settings_kv['migrated_v1'] از اجرای دوباره جلوگیری می‌کند.
 *
 * برای قابل‌تست‌بودن بدون مرورگر واقعی، خواندن داده‌ی مبدأ پشت دو تابع
 * قابل‌تزریق (readInvoicesSource/readCustomersSource) قرار گرفته که
 * پیش‌فرضشان IndexedDB واقعی مرورگر است؛ تست‌ها می‌توانند این دو را با
 * داده‌ی fake جایگزین کنند بدون نیاز به شبیه‌سازی کامل IndexedDB.
 */

import { CustomerRepo } from './CustomerRepo.js';
import { InvoiceRepo } from './InvoiceRepo.js';
import { getDb } from './connection.js';

const BATCH_SIZE = 500;
const MIGRATION_FLAG_KEY = 'migrated_v1';

/** خواندن یک object store کامل از IndexedDB مرورگر (پیاده‌سازی پیش‌فرض). */
function readIdbStore(dbName, storeName) {
  return new Promise((resolve) => {
    if (typeof indexedDB === 'undefined') return resolve([]);
    const openReq = indexedDB.open(dbName);
    openReq.onerror = () => resolve([]);
    openReq.onsuccess = () => {
      const db = openReq.result;
      if (!db.objectStoreNames.contains(storeName)) { db.close(); return resolve([]); }
      const tx = db.transaction(storeName, 'readonly');
      const store = tx.objectStore(storeName);
      const getAllReq = store.getAll();
      getAllReq.onsuccess = () => { db.close(); resolve(getAllReq.result || []); };
      getAllReq.onerror = () => { db.close(); resolve([]); };
    };
  });
}

/** fallback قدیمی‌تر: window.storage['rasticpack-data'] (نسخه‌های خیلی قدیمی اپ). */
function readLegacyWindowStorage(key) {
  try {
    if (typeof window === 'undefined' || !window.storage) return null;
    const raw = window.storage[key];
    return raw ? (typeof raw === 'string' ? JSON.parse(raw) : raw) : null;
  } catch {
    return null;
  }
}

async function defaultReadInvoicesSource() {
  const fromIdb = await readIdbStore('rasticpackDB', 'invoices');
  if (fromIdb.length) return fromIdb;
  const legacy = readLegacyWindowStorage('rasticpack-data');
  return legacy?.invoices || [];
}

async function defaultReadCustomersSource() {
  const fromIdb = await readIdbStore('rasticpackDB', 'customers');
  if (fromIdb.length) return fromIdb;
  const legacy = readLegacyWindowStorage('rasticpack-data');
  return legacy?.customers || [];
}

async function batchInsert(items, insertFn) {
  let written = 0;
  for (let i = 0; i < items.length; i += BATCH_SIZE) {
    const batch = items.slice(i, i + BATCH_SIZE);
    written += (await insertFn(batch)) || batch.length;
  }
  return written;
}

/**
 * @param {object} [opts]
 * @param {() => Promise<any[]>} [opts.readInvoicesSource]
 * @param {() => Promise<any[]>} [opts.readCustomersSource]
 * @param {boolean} [opts.force] اگر true باشد، حتی اگر پرچم migrated_v1 ست شده باشد دوباره اجرا می‌شود (فقط برای تست/دیباگ)
 * @returns {Promise<{status:'skipped'|'ok'|'error', invoiceCount?:number, customerCount?:number, message?:string}>}
 */
export async function migrate({
  readInvoicesSource = defaultReadInvoicesSource,
  readCustomersSource = defaultReadCustomersSource,
  force = false
} = {}) {
  const db = await getDb();

  if (!force) {
    const flagRes = await db.query(`SELECT value FROM settings_kv WHERE key = ?`, [MIGRATION_FLAG_KEY]);
    if (flagRes?.values?.[0]?.value === '1') {
      return { status: 'skipped', message: 'قبلاً مهاجرت انجام شده (migrated_v1=1).' };
    }
  }

  const [sourceCustomers, sourceInvoices] = await Promise.all([
    readCustomersSource(),
    readInvoicesSource()
  ]);

  await batchInsert(sourceCustomers, (batch) => CustomerRepo.bulkInsert(batch));

  // ⚠️ کشف‌شده حین تست ادغام واقعی (نسخه ۴.۲): ستون invoices.customerId یک
  // FOREIGN KEY به customers(id) دارد (schema.js). اگر مشتریِ یک فاکتور قبلاً
  // در IndexedDB حذف شده باشد ولی فاکتورهایش مانده باشند (orphan)، insert
  // آن فاکتور با خطای FOREIGN KEY constraint شکست می‌خورد و کل تراکنش
  // batch را rollback می‌کند. قبل از نوشتن فاکتورها، برای هر customerId
  // یتیم یک ردیف جایگزین («مشتری حذف‌شده») ساخته می‌شود تا مهاجرت متوقف
  // نشود؛ این ردیف‌های جایگزین در شمارش تطبیقی پایین‌تر هم لحاظ می‌شوند.
  const knownCustomerIds = new Set(sourceCustomers.map((c) => c.id));
  const orphanCustomerIds = [...new Set(
    sourceInvoices.map((inv) => inv.customerId).filter((id) => id != null && !knownCustomerIds.has(id))
  )];
  if (orphanCustomerIds.length) {
    await CustomerRepo.bulkInsert(
      orphanCustomerIds.map((id) => ({ id, name: '(مشتری حذف‌شده)' }))
    );
  }

  await batchInsert(sourceInvoices, (batch) => InvoiceRepo.bulkInsert(batch));

  // شمارش تطبیقی قبل از هرگونه ادعای موفقیت — طبق راهکار ریسک شماره‌ی ۱
  const custCountRes = await db.query(`SELECT COUNT(*) AS c FROM customers`, []);
  const invCountRes = await db.query(`SELECT COUNT(*) AS c FROM invoices`, []);
  const custCount = Number(custCountRes?.values?.[0]?.c || 0);
  const invCount = Number(invCountRes?.values?.[0]?.c || 0);

  if (custCount < sourceCustomers.length || invCount < sourceInvoices.length) {
    // به‌عمد پرچم migrated_v1 را ست نمی‌کنیم — اجرای بعدی دوباره تلاش می‌کند.
    // داده‌ی IndexedDB مبدأ اینجا هرگز پاک نمی‌شود (این تابع اصلاً IndexedDB
    // را نمی‌نویسد/پاک نمی‌کند)، پس چیزی برای «دست‌نخورده نگه‌داشتن» گم نمی‌شود.
    return {
      status: 'error',
      message: `شمارش مقصد کمتر از مبدأ است: customers ${custCount}/${sourceCustomers.length}, invoices ${invCount}/${sourceInvoices.length}`,
      invoiceCount: invCount,
      customerCount: custCount
    };
  }

  await db.run(
    `INSERT INTO settings_kv (key, value) VALUES (?, '1')
     ON CONFLICT(key) DO UPDATE SET value='1'`,
    [MIGRATION_FLAG_KEY]
  );

  return { status: 'ok', invoiceCount: invCount, customerCount: custCount };
}

export default { migrate };
