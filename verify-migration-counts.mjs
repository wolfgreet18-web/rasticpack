/**
 * verify-migration-counts.mjs — فاز ۰ نقشه‌راه SQLite
 *
 * هدف: بعد از هر اجرای migration، تعداد رکوردهای IndexedDB را با تعداد
 * رکوردهای SQLite (برای invoices و customers، جداگانه) مقایسه کند.
 *
 * چرا Node نیست: IndexedDB فقط داخل مرورگر/WebView وجود دارد و اتصال
 * SQLite واقعی هم فقط داخل شل Capacitor (window.CapacitorSQLite) در
 * دسترس است — این دو هیچ‌کدام در Node ساده موجود نیستند. بنابراین این
 * اسکریپت باید *داخل خودِ اپ* اجرا شود، نه با `node ...`.
 *
 * نحوه‌ی اجرا (دو روش):
 *
 *   ۱) داخل DevTools console وقتی html8.html در Capacitor WebView یا حتی
 *      مرورگر معمولی باز است:
 *        import('./verify-migration-counts.mjs').then(m => m.verifyMigrationCounts())
 *      (یا محتوای تابع verifyMigrationCounts را مستقیم در کنسول paste کنید)
 *
 *   ۲) به‌صورت موقت به انتهای html8.html اضافه کنید (بعد از بلاک
 *      bootstrapSQLitePhase2Step1، طوری که window.__InvoiceRepo/__CustomerRepo
 *      از قبل ست شده باشند):
 *        <script type="module">
 *          import { verifyMigrationCounts } from './verify-migration-counts.mjs';
 *          window.__verifyMigrationCounts = verifyMigrationCounts;
 *        </script>
 *      و بعد در کنسول: await window.__verifyMigrationCounts()
 *
 * خروجی: یک جدول در کنسول + یک آبجکت {invoices:{idb,sqlite,match}, customers:{...}}.
 * اگر match=false شد، یعنی migration رکورد گم/تکراری کرده — باید قبل از
 * رفتن به فاز ۲ بررسی شود.
 */

function openIdb(dbName = 'rasticpack', storeNames = ['invoices', 'customers']) {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(dbName);
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
    req.onupgradeneeded = () => {
      // اگر دیتابیس هنوز وجود ندارد، این یعنی چیزی برای مقایسه نیست —
      // upgrade خالی را رد می‌کنیم تا خطای گمراه‌کننده نسازیم.
      resolve(req.result);
    };
  });
}

function idbCount(db, storeName) {
  return new Promise((resolve, reject) => {
    if (!db.objectStoreNames.contains(storeName)) {
      resolve(0);
      return;
    }
    const tx = db.transaction(storeName, 'readonly');
    const store = tx.objectStore(storeName);
    const req = store.count();
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function sqliteCount(getDb, tableName) {
  const db = await getDb();
  const res = await db.query(`SELECT COUNT(*) AS c FROM ${tableName}`);
  return Number(res?.values?.[0]?.c || 0);
}

export async function verifyMigrationCounts() {
  const result = {};

  // --- IndexedDB ---
  let idbCounts = { invoices: null, customers: null };
  try {
    const db = await openIdb();
    idbCounts.invoices = await idbCount(db, 'invoices');
    idbCounts.customers = await idbCount(db, 'customers');
    db.close();
  } catch (e) {
    console.warn('[verify-migration-counts] خواندن IndexedDB ناموفق:', e?.message);
  }

  // --- SQLite (از طریق window.__InvoiceRepo/__CustomerRepo که در
  // bootstrapSQLitePhase2Step1 ست می‌شوند؛ اگر نبود یعنی این محیط
  // Capacitor واقعی نیست یا migration هنوز اجرا نشده) ---
  let sqliteCounts = { invoices: null, customers: null };
  try {
    if (!window.__InvoiceRepo || !window.__CustomerRepo) {
      throw new Error('window.__InvoiceRepo/__CustomerRepo هنوز ست نشده — bootstrap هنوز اجرا نشده یا SQLite در دسترس نیست');
    }
    // خودِ InvoiceRepo/CustomerRepo متد COUNT(*) بدون سقف بیرون نمی‌دهند
    // (getPage/searchByName سقف‌دار هستند) — برای شمارش دقیق مصالحه‌ای،
    // مستقیم از همان اتصال زیرین (connection.js:getDb) استفاده می‌کنیم.
    const { getDb } = await import('./rasticpack-capacitor/src/db/connection.js');
    sqliteCounts.invoices = await sqliteCount(getDb, 'invoices');
    sqliteCounts.customers = await sqliteCount(getDb, 'customers');
  } catch (e) {
    console.warn('[verify-migration-counts] خواندن SQLite ناموفق:', e?.message);
  }

  result.invoices = {
    idb: idbCounts.invoices,
    sqlite: sqliteCounts.invoices,
    match: idbCounts.invoices != null && idbCounts.invoices === sqliteCounts.invoices,
  };
  result.customers = {
    idb: idbCounts.customers,
    sqlite: sqliteCounts.customers,
    match: idbCounts.customers != null && idbCounts.customers === sqliteCounts.customers,
  };

  console.table(result);
  if (!result.invoices.match || !result.customers.match) {
    console.warn('[verify-migration-counts] ⚠️ عدم تطابق شمارش — migration را بررسی کنید.');
  } else {
    console.log('[verify-migration-counts] ✅ شمارش IndexedDB و SQLite برای هر دو جدول برابر است.');
  }
  return result;
}
