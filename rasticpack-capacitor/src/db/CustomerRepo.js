/**
 * CustomerRepo.js — Repository مشتریان (فاز ۱ نقشه‌راه).
 * امضای متدها دقیقاً همان چیزی است که در roadmap-infinite-records-v4.md
 * مستند شده: searchByName, getPage, findByExactName, insert/save/remove, bulkInsert.
 */

import { getDb } from './connection.js';

function rowToCustomer(row) {
  return {
    id: row.id,
    name: row.name,
    company: row.company || '',
    address: row.address || '',
    phone: row.phone || '',
    lat: row.lat,
    lng: row.lng,
    locationLink: row.locationLink || '',
    createdAt: row.createdAt
  };
}

/** مسیر قدیمی (پیش از ۴.۱۸) — LIKE ساده روی name، با شمارش سقف‌دار. */
async function searchByNameLike(db, likePattern, limit, offset, countCap) {
  const cappedRes = await db.query(
    `SELECT COUNT(*) AS c FROM (SELECT 1 FROM customers WHERE name LIKE ? LIMIT ?)`,
    [likePattern, countCap]
  );
  const cappedCount = Number(cappedRes?.values?.[0]?.c || 0);
  const totalIsExact = cappedCount < countCap;
  const res = await db.query(
    `SELECT * FROM customers WHERE name LIKE ? ORDER BY name LIMIT ? OFFSET ?`,
    [likePattern, limit, offset]
  );
  return { items: (res?.values || []).map(rowToCustomer), total: cappedCount, totalIsExact };
}

/** مسیر جدید (۴.۱۸) — FTS5 روی customers_fts، هم name هم company را می‌بیند. */
async function searchByNameFts(db, ftsQuery, limit, offset, countCap) {
  const cappedRes = await db.query(
    `SELECT COUNT(*) AS c FROM (
       SELECT 1 FROM customers_fts WHERE customers_fts MATCH ? LIMIT ?
     )`,
    [ftsQuery, countCap]
  );
  const cappedCount = Number(cappedRes?.values?.[0]?.c || 0);
  const totalIsExact = cappedCount < countCap;
  const res = await db.query(
    `SELECT customers.* FROM customers_fts
     JOIN customers ON customers.id = customers_fts.rowid
     WHERE customers_fts MATCH ?
     ORDER BY rank
     LIMIT ? OFFSET ?`,
    [ftsQuery, limit, offset]
  );
  return { items: (res?.values || []).map(rowToCustomer), total: cappedCount, totalIsExact };
}

/**
 * یک کوئری آزاد کاربر (مثلاً «علی رستمی») را به سینتکس MATCH فایل FTS5
 * تبدیل می‌کند: هر توکن جدا با فاصله را به‌صورت یک عبارت پیشوندی (`"tok"*`)
 * می‌نویسد؛ چند توکن با AND ضمنی FTS5 ترکیب می‌شوند. کوتیشن‌های داخل توکن
 * (نادر، ولی ممکن) با دوبرابر کردن escape می‌شوند تا سینتکس FTS5 نشکند.
 * ورودی خالی/فقط-فاصله → null (یعنی «این مسیر را رد کن»).
 */
function buildFtsPrefixQuery(query) {
  const tokens = String(query || '').trim().split(/\s+/).filter(Boolean);
  if (!tokens.length) return null;
  return tokens.map(t => `"${t.replace(/"/g, '""')}"*`).join(' ');
}

/**
 * کش سبک total برای getPage — رفع باگ عملکردی مکمل باگ «اسکرول خراب بعد از
 * بازیابی بکاپ ۱۰۰هزار مشتری‌ای» (این یکی خودِ محتوا را خراب نمی‌کند، ولی
 * اسکرول را کند/لرزان می‌کند که همان حس «خراب بودن» را می‌دهد):
 * قبلاً getPage در هر فراخوانی (یعنی هر بار که Virtual List با اسکرول یک
 * بازه‌ی جدید را fetch می‌کرد) یک `SELECT COUNT(*) FROM customers` کامل روی
 * کل جدول اجرا می‌کرد — روی ۱۰۰هزار ردیف، از پل Capacitor (WebView↔نیتیو)
 * عبور می‌کرد، یعنی هر فریم اسکرول عملاً دو رفت‌وبرگشت کامل به SQLite داشت
 * (یکی COUNT، یکی SELECT صفحه). چون تعداد کل ردیف‌ها بین دو نوشتن (افزودن/
 * حذف/بازیابی) عوض نمی‌شود، همان الگوی «شمارش سقف‌دار» که searchByName از
 * قبل دارد این‌جا هم به‌کار رفت: فقط یک‌بار شمرده و کش می‌شود، و با هر
 * نوشتنی (save/remove/bulkInsert/clearAll) باطل می‌شود. */
let _pageTotalCache = null;
function invalidatePageTotalCache() { _pageTotalCache = null; }

export const CustomerRepo = {
  /**
   * جستجوی نام/شرکت. دو حالت:
   * - `prefix:true` («شروع با» روی کل نام، الگوی قدیمی‌تر): `LIKE 'نام%'`
   *   که مستقیماً از ایندکس `idx_cust_name` بهره می‌برد — سریع‌ترین حالت،
   *   ولی فقط ابتدای فیلد `name` را می‌بیند (نه `company`، نه وسط کلمه).
   * - در غیر این صورت (پیش‌فرض، فاز ۳ اقدام ۲ — نسخه‌ی ۴.۱۸): جستجوی
   *   FTS5 روی `customers_fts` — هم `name` و هم `company` را می‌بیند، و
   *   کلمه‌ی دوم/سوم را هم پیدا می‌کند (نه فقط پیشوند کل رشته). هر توکن
   *   ورودی به‌صورت «پیشوند توکن» (`"token"*`) جستجو می‌شود؛ توکن‌های
   *   چندتایی با AND ضمنی FTS5 ترکیب می‌شوند (یعنی «علی رستمی» یعنی سطری
   *   که هم با «علی» و هم با «رستمی» شروع‌شونده‌ای دارد، نه لزوماً پشت‌سرهم).
   *   **صادقانه، نه پنهان‌شده:** این هنوز substring واقعی «وسط یک کلمه»
   *   نیست (FTS5 توکنایزر پیش‌فرض `unicode61` کلمه را واحد می‌بیند، نه
   *   حرف‌به‌حرف) — برای آن یک توکنایزر trigram لازم است که خارج از دامنه‌ی
   *   این ریزمرحله نگه داشته شده (نگاه کن به یادداشت ۴.۱۸ نقشه‌راه). اگر
   *   `customers_fts` در دسترس نبود/خطا داد (مثلاً روی یک دیتابیس قدیمی‌تر
   *   که هنوز `ensureCustomersFtsBackfilled` رویش اجرا نشده)، بی‌صدا به همان
   *   `LIKE '%query%'` قدیمی روی `name` برمی‌گردیم — رفتار قبل از ۴.۱۸.
   *
   * شمارش `total` عمداً «سقف‌دار» (capped) است، نه همیشه دقیق: برای جستجوهای
   * خیلی گسترده (پیشوند کوتاه/رایج که تقریباً کل جدول را match می‌کند)،
   * شمارش دقیق با COUNT(*) روی میلیون‌ها تطابق هزینه‌ی خودش را دارد (روی ۱M
   * مشتری با یک پیشوند گسترده حدود ۵۰ms، بالاتر از آستانه‌ی پذیرش ۳۰ms —
   * اندازه‌گیری و ریشه‌یابی‌شده در نسخه‌ی ۴.۹ نقشه‌راه، حل‌شده در ۴.۱۰). به‌جای
   * COUNT(*) خام، شمارش داخل یک زیرکوئری با LIMIT=countCap محدود می‌شود، پس
   * هزینه‌اش هیچ‌وقت از سقف بیشتر نمی‌شود، صرف‌نظر از این‌که جستجو چند
   * میلیون ردیف را match کند. اگر تعداد شمرده‌شده به سقف برسد، `totalIsExact`
   * برابر false برمی‌گردد — یعنی «حداقل countCap تطابق»، نه عدد قطعی؛ UI باید
   * در آن حالت چیزی مثل «+countCap نتیجه» نشان دهد، نه عدد را به‌عنوان کل
   * دقیق فرض کند. برای جستجوهای معمولی (selectivity معقول)، شمارش هنوز
   * دقیق و آنی است، چون خودِ سقف هرگز لمس نمی‌شود.
   */
  async searchByName(query, { limit = 50, offset = 0, prefix = false, countCap = 2000 } = {}) {
    const db = await getDb();
    if (prefix) {
      return searchByNameLike(db, `${query}%`, limit, offset, countCap);
    }
    const ftsQuery = buildFtsPrefixQuery(query);
    if (ftsQuery) {
      try {
        return await searchByNameFts(db, ftsQuery, limit, offset, countCap);
      } catch (e) {
        // customers_fts نبود/خطا داد (مثلاً backfill هنوز اجرا نشده) — بی‌صدا
        // به رفتار قبل از ۴.۱۸ برمی‌گردیم، نه اینکه جستجو کلاً بشکند.
      }
    }
    return searchByNameLike(db, `%${query}%`, limit, offset, countCap);
  },

  async getPage({ limit = 50, offset = 0 } = {}) {
    const db = await getDb();
    if (_pageTotalCache == null) {
      const totalRes = await db.query(`SELECT COUNT(*) AS c FROM customers`, []);
      _pageTotalCache = Number(totalRes?.values?.[0]?.c || 0);
    }
    const res = await db.query(`SELECT * FROM customers ORDER BY name LIMIT ? OFFSET ?`, [limit, offset]);
    return { items: (res?.values || []).map(rowToCustomer), total: _pageTotalCache };
  },

  /**
   * فقط ستون name برای همه‌ی مشتری‌ها، بدون صفحه‌بندی — برای پر کردن
   * <datalist> خودتکمیلی فرم مشتری در html8.html (فاز ۳ نقشه‌راه، مورد
   * «datalist مشتریان»). سبک‌تر از getPage چون فقط یک ستون می‌خواند و کل
   * دیتابیس را یک‌جا برمی‌گرداند (datalist نمی‌تواند صفحه‌بندی‌شده باشد).
   */
  async getAllNames() {
    const db = await getDb();
    const res = await db.query(`SELECT name FROM customers ORDER BY name`, []);
    return (res?.values || []).map(r => r.name);
  },

  /** معادل getCustomerById فعلی در html8.html — بازیابی یک مشتری با id. */
  async getById(id) {
    const db = await getDb();
    const res = await db.query(`SELECT * FROM customers WHERE id = ? LIMIT 1`, [id]);
    const row = res?.values?.[0];
    return row ? rowToCustomer(row) : null;
  },

  /** معادل findCustomerByName فعلی در html8.html — تطابق دقیق (نه substring). */
  async findByExactName(name) {
    const db = await getDb();
    const res = await db.query(`SELECT * FROM customers WHERE name = ? COLLATE NOCASE LIMIT 1`, [name]);
    const row = res?.values?.[0];
    return row ? rowToCustomer(row) : null;
  },

  async insert(customer) {
    return CustomerRepo.save(customer);
  },

  async save(customer) {
    if (!customer || customer.id == null) throw new Error('CustomerRepo.save: customer.id الزامی است.');
    const db = await getDb();
    const sql = `
      INSERT INTO customers (id, name, company, address, phone, lat, lng, locationLink)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(id) DO UPDATE SET
        name=excluded.name, company=excluded.company, address=excluded.address,
        phone=excluded.phone, lat=excluded.lat, lng=excluded.lng, locationLink=excluded.locationLink
    `;
    await db.run(sql, [
      customer.id, customer.name || '', customer.company || null, customer.address || null,
      customer.phone || null, customer.lat ?? null, customer.lng ?? null, customer.locationLink || null
    ]);
    /* save روی ON CONFLICT هم می‌تواند insert جدید باشد هم update — چون این‌جا
       نمی‌دانیم کدام‌یک بود، برای درستی همیشه باطل می‌کنیم (فقط یک COUNT اضافه
       در بدترین حالت، خیلی ارزان‌تر از total نادرست). */
    invalidatePageTotalCache();
    return true;
  },

  async remove(id) {
    const db = await getDb();
    await db.run(`DELETE FROM customers WHERE id = ?`, [id]);
    invalidatePageTotalCache();
    return true;
  },

  async bulkInsert(customers = []) {
    if (!customers.length) return 0;
    const db = await getDb();
    return db.withTransaction(async (tx) => {
      for (const c of customers) {
        await tx.run(
          `INSERT INTO customers (id, name, company, address, phone, lat, lng, locationLink)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT(id) DO UPDATE SET
             name=excluded.name, company=excluded.company, address=excluded.address,
             phone=excluded.phone, lat=excluded.lat, lng=excluded.lng, locationLink=excluded.locationLink`,
          [c.id, c.name || '', c.company || null, c.address || null, c.phone || null, c.lat ?? null, c.lng ?? null, c.locationLink || null]
        );
      }
      return customers.length;
    }).then(n => { invalidatePageTotalCache(); return n; });
  },

  /**
   * ✅ فاز ۴ نقشه‌راه (`doBackup` مستقیم از SQLite، نه IndexedDB) — پیمایش
   * کامل جدول customers، batch به batch، با keyset روی `id` — همان الگوی
   * دقیق `InvoiceRepo.forEachBatch` (نگاه کن به یادداشت آنجا برای دلیل
   * انتخاب keyset به‌جای OFFSET). هم‌قرارداد `idbForEachBatch`: (batchSize, onBatch).
   * @param {number} batchSize
   * @param {(batch:any[])=>any} onBatch
   */
  async forEachBatch(batchSize, onBatch) {
    const db = await getDb();
    let lastId = 0;
    while (true) {
      const res = await db.query(
        `SELECT * FROM customers WHERE id > ? ORDER BY id LIMIT ?`,
        [lastId, batchSize]
      );
      const rows = res?.values || [];
      if (!rows.length) break;
      await onBatch(rows.map(rowToCustomer));
      lastId = rows[rows.length - 1].id;
      if (rows.length < batchSize) break;
    }
  },

  /**
   * حذف کامل تمام ردیف‌های جدول customers (نه DROP TABLE — schema/ایندکس‌ها
   * دست‌نخورده می‌مانند). برای `clearAllData`/`doRestore` در html8.html
   * (ریسک بخش ۶: این دو تابع قبلاً فقط IndexedDB را پاک می‌کردند، نه SQLite).
   * **مهم — ترتیب فراخوانی:** schema.js یک FOREIGN KEY(customerId) REFERENCES
   * customers(id) روی invoices دارد؛ اگر این متد قبل از `InvoiceRepo.clearAll()`
   * صدا زده شود، SQLite با «FOREIGN KEY constraint failed» رد می‌شود (تا وقتی
   * فاکتورهای وابسته هنوز وجود دارند). همیشه اول `InvoiceRepo.clearAll()`،
   * بعد این متد.
   * @returns {Promise<boolean>}
   */
  async clearAll() {
    const db = await getDb();
    await db.run(`DELETE FROM customers`, []);
    invalidatePageTotalCache();
    return true;
  }
};

export default CustomerRepo;
