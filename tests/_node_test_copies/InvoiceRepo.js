/**
 * InvoiceRepo.js — تنها نقطه‌ای که کد UI (html8.html) برای خواندن/نوشتن
 * فاکتور با دیتابیس صحبت می‌کند (Repository Pattern، بخش ۳ نقشه‌راه).
 *
 * امضای متدها دقیقاً هم‌راستا با نسخه‌ی نهایی مستندشده در roadmap-infinite-records-v4.md
 * (بعد از رفع ریسک ۲.۵) است: getPage/getStatusCounts با monthStart/monthEnd
 * ISO کار می‌کنند، نه یک sortVal شمسی خام.
 */

import { getDb } from '../testConnection.node-only.mjs';
import { InvoiceRepoAdditions } from './InvoiceRepo.additions.js';

/**
 * یک عبارت WHERE مشترک برای getPage/getStatusCounts می‌سازد — هر دو باید
 * دقیقاً همان فیلترها (بازه‌ی تاریخ/مشتری/جستجو) را اعمال کنند تا شمارش
 * دکمه‌های وضعیت با لیست واقعی فاکتورها هم‌خوان بماند.
 */
function buildWhere({ monthStart, monthEnd, exactDate, customerId, search }) {
  const clauses = [];
  const params = [];

  if (exactDate) {
    clauses.push(`date(date) = date(?)`);
    params.push(exactDate);
  } else if (monthStart && monthEnd) {
    clauses.push(`date >= ? AND date < ?`);
    params.push(monthStart, monthEnd);
  }

  if (customerId != null && customerId !== '') {
    clauses.push(`customerId = ?`);
    params.push(customerId);
  }

  if (search) {
    clauses.push(`LOWER(customerName) LIKE ?`);
    params.push(`%${String(search).toLowerCase()}%`);
  }

  return {
    sql: clauses.length ? `WHERE ${clauses.join(' AND ')}` : '',
    params
  };
}

/**
 * ✅ نسخه‌ی ۴.۲۶ — استخراج شده از بدنه‌ی getPage (بدون تغییر رفتار) تا هم
 * getPage (offset-based) و هم getPageByCursor (keyset-based، فاز ۷ اقدام ۱)
 * دقیقاً همان فیلترها (بازه/مشتری/جستجو/status) را از یک منبع واحد بگیرند —
 * همان اصل «یک عبارت WHERE مشترک» که buildWhere برای filters پایه رعایت
 * می‌کرد، اینجا برای status هم تکرار می‌شود.
 */
function buildFullWhere({ monthStart, monthEnd, exactDate, customerId, search, status }) {
  const { sql: whereBase, params: whereParams } = buildWhere({ monthStart, monthEnd, exactDate, customerId, search });
  let where = whereBase;
  const params = whereParams.slice();
  if (status === 'debtor') {
    where = where ? `${where} AND sent = 1 AND status != 'paid'` : `WHERE sent = 1 AND status != 'paid'`;
  } else if (status) {
    const notDebtorClause = status === 'paid' ? `status = ?` : `status = ? AND sent = 0`;
    where = where ? `${where} AND ${notDebtorClause}` : `WHERE ${notDebtorClause}`;
    params.push(status);
  }
  return { sql: where, params };
}

function rowToInvoice(row) {
  // ستون data شامل شیء کامل فاکتور (items[] و هر فیلد دیگر) است؛ ستون‌های
  // مجزا (status/sent/...) فقط برای فیلتر/ایندکس نگه داشته می‌شوند و همیشه
  // باید با آنچه داخل data ذخیره شده یکی باشند (چون InvoiceRepo.save هر دو
  // را هم‌زمان از روی همان invoice می‌نویسد).
  try {
    return JSON.parse(row.data);
  } catch {
    // اگر به هر دلیل data خراب/خالی بود، حداقل ستون‌های مجزا را برگردان
    // تا رندر کارت کاملاً نشکند.
    return { ...row };
  }
}

export const InvoiceRepo = {
  /**
   * صفحه‌بندی واقعی با LIMIT/OFFSET در SQL (نه slice روی آرایه).
   * @returns {Promise<{items: any[], total: number}>}
   */
  /**
   * ✅ نسخه‌ی ۴.۲۵ — کشف حین تست فشار واقعی فاز ۷ (۵M فاکتور، نه ۱M قبلی):
   * `COUNT(*)` خام روی `where` قبل از این نسخه دقیقاً همان باگی بود که
   * `CustomerRepo.searchByName` در نسخه‌ی ۴.۱۰ با «شمارش سقف‌دار» رفع کرده
   * بود — ولی آن رفع هیچ‌وقت به `InvoiceRepo.getPage` هم اعمال نشده بود.
   * روی ۵M ردیف، فیلترهای غیرپوشیده‌ی کامل توسط ایندکس (مثلاً `customerId`
   * تنها) باعث می‌شدند این COUNT از آستانه‌ی ۱۰۰ms فاز ۷ رد شود. رفع با همان
   * الگوی دقیق `searchByNameLike`: `COUNT(*) FROM (SELECT 1 ... LIMIT countCap)`.
   */
  async getPage({ monthStart, monthEnd, exactDate, status, customerId, search, limit = 50, offset = 0, countCap = 2000 } = {}) {
    const db = await getDb();
    const { sql: where, params } = buildFullWhere({ monthStart, monthEnd, exactDate, customerId, search, status });

    const cappedRes = await db.query(
      `SELECT COUNT(*) AS c FROM (SELECT 1 FROM invoices ${where} LIMIT ?)`,
      [...params, countCap]
    );
    const cappedCount = Number(cappedRes?.values?.[0]?.c || 0);
    const totalIsExact = cappedCount < countCap;

    const pageRes = await db.query(
      `SELECT * FROM invoices ${where} ORDER BY date DESC LIMIT ? OFFSET ?`,
      [...params, limit, offset]
    );
    const items = (pageRes?.values || []).map(rowToInvoice);
    return { items, total: cappedCount, totalIsExact };
  },

  /**
   * ✅ نسخه‌ی ۴.۲۶ — فاز ۷، باقی‌مانده‌ی رسمی (۱): keyset/cursor pagination.
   *
   * چرا: طبق یافته‌ی صادقانه‌ی ثبت‌شده در یادداشت ۴.۲۵ (سناریو ۲ فاز ۷،
   * اسکرول تا انتهای لیست روی ۵M ردیف)، `LIMIT ? OFFSET ?` در SQLite یعنی
   * موتور باید تمام ردیف‌های قبل از offset را واقعاً پیمایش/دور بریزد —
   * هزینه‌ی O(offset)، نه O(limit). این یک محدودیت شناخته‌شده‌ی SQL است، نه
   * باگ ایندکس. راه‌حل استاندارد: keyset pagination — به‌جای «۵۰ تای بعد از
   * ردیف‌شمار N»، «۵۰ تای بعد از (date, id) مشخص» بخواهیم؛ چون (date, id) با
   * ایندکس `idx_inv_date_id` (این نسخه، پایین schema.js) قابل seek مستقیم
   * است، هزینه صرف‌نظر از عمق اسکرول تقریباً ثابت می‌ماند.
   *
   * **تصمیم طراحی عمدی — جایگزین getPage نمی‌شود، کنارش اضافه می‌شود:**
   * getPage (offset-based) برای پرش مستقیم به یک صفحه‌ی دلخواه (مثلاً
   * scrollbar/جهش به وسط لیست) هنوز لازم است — keyset فقط می‌تواند «صفحه‌ی
   * بعد از X» را جواب بدهد، نه «صفحه‌ی شماره‌ی K» را. یعنی این متد مکمل
   * getPage است، نه جایگزینش؛ Virtual List (فاز ۲) باید بر اساس رفتار
   * واقعی اسکرول (پرش تصادفی در برابر اسکرول پیوسته) تصمیم بگیرد کدام را
   * صدا بزند — **این تصمیم/اتصال UI عمداً خارج از دامنه‌ی این نسخه نگه
   * داشته شده** (نگاه کن به یادداشت ۴.۲۶ بالای سند برای جزئیات) چون به یک
   * تغییر در مدل اسکرول (نه فقط یک صدا زدن متد جدید) نیاز دارد.
   *
   * @param {object} opts - همان فیلترهای getPage + `cursor` به‌جای `offset`.
   * @param {{date:string,id:number}|null} [opts.cursor] - (date,id) آخرین
   *   ردیفِ صفحه‌ی قبل؛ null یعنی «اولین صفحه» (معادل offset=0).
   * @returns {Promise<{items:any[], total:number, totalIsExact:boolean, nextCursor:{date:string,id:number}|null}>}
   */
  async getPageByCursor({ monthStart, monthEnd, exactDate, status, customerId, search, limit = 50, cursor = null, countCap = 2000 } = {}) {
    const db = await getDb();
    const { sql: whereBase, params: whereParams } = buildFullWhere({ monthStart, monthEnd, exactDate, customerId, search, status });

    let where = whereBase;
    const params = whereParams.slice();
    if (cursor && cursor.date != null && cursor.id != null) {
      // ORDER BY date DESC, id DESC یعنی «بعدی» = تاپل کوچک‌تر از cursor.
      //
      // ⚠️ کشف حین اندازه‌گیری این نسخه (نه نظری) — فرم اولیه‌ی این شرط با
      // OR نوشته شده بود: `(date < ? OR (date = ? AND id < ?))`. تست کارایی
      // (`tests/keyset-pagination-perf-test.mjs`) نشان داد این فرم **کندتر**
      // از OFFSET عمیق بود (۶۷ms در برابر ۳۰ms روی ۱M ردیف) — دقیقاً برخلاف
      // هدف این اقدام. علت با `EXPLAIN QUERY PLAN` پیدا شد: planner با فرم
      // OR نمی‌تواند ایندکس ترکیبی `idx_inv_date_id` را برای seek استفاده
      // کند و به یک `SCAN ... USING COVERING INDEX` (پیمایش کامل، فقط
      // index-only نه seek) سقوط می‌کند. راه‌حل: سینتکس row-value SQLite
      // (پشتیبانی‌شده از نسخه‌ی ۳.۱۵ به بعد، در `node:sqlite`/موتور مدرن
      // Capacitor SQLite هر دو موجود است) — `(date, id) < (?, ?)` — که
      // planner را مجبور به `SEARCH ... (date<?)` (seek واقعی روی ایندکس)
      // می‌کند. رفع شد و دوباره اندازه‌گیری شد (نگاه کن به یادداشت ۴.۲۶).
      const keysetClause = `(date, id) < (?, ?)`;
      where = where ? `${where} AND ${keysetClause}` : `WHERE ${keysetClause}`;
      params.push(cursor.date, cursor.id);
    }

    // total مستقل از cursor است (شمارش کل مجموعه‌ی فیلترشده، نه فقط باقی‌مانده
    // بعد از cursor) — تا رفتار نمایشی «تعداد کل» با getPage یکسان بماند؛
    // برای همین از whereBase (بدون keysetClause) استفاده می‌کنیم، نه where.
    const cappedRes = await db.query(
      `SELECT COUNT(*) AS c FROM (SELECT 1 FROM invoices ${whereBase} LIMIT ?)`,
      [...whereParams, countCap]
    );
    const cappedCount = Number(cappedRes?.values?.[0]?.c || 0);
    const totalIsExact = cappedCount < countCap;

    const pageRes = await db.query(
      `SELECT * FROM invoices ${where} ORDER BY date DESC, id DESC LIMIT ?`,
      [...params, limit]
    );
    const rows = pageRes?.values || [];
    const items = rows.map(rowToInvoice);
    const lastRow = rows[rows.length - 1];
    const nextCursor = lastRow ? { date: lastRow.date, id: lastRow.id } : null;
    return { items, total: cappedCount, totalIsExact, nextCursor };
  },

  /**
   * شمارش هر ۴ وضعیت (draft/partial/paid/debtor) هم‌زمان، بدون پارامتر status —
   * چون UI باید هر ۴ عدد را روی دکمه‌های فیلتر نشان دهد صرف‌نظر از این‌که
   * کدام دکمه فعلاً فعال است.
   */
  async getStatusCounts({ monthStart, monthEnd, exactDate, customerId, search } = {}) {
    const db = await getDb();
    const { sql: where, params } = buildWhere({ monthStart, monthEnd, exactDate, customerId, search });
    const sql = `
      SELECT
        SUM(CASE WHEN status='draft'   AND sent=0 THEN 1 ELSE 0 END) AS draft,
        SUM(CASE WHEN status='partial' AND sent=0 THEN 1 ELSE 0 END) AS partial,
        SUM(CASE WHEN status='paid' THEN 1 ELSE 0 END) AS paid,
        SUM(CASE WHEN sent=1 AND status!='paid' THEN 1 ELSE 0 END) AS debtor
      FROM invoices ${where}
    `;
    const res = await db.query(sql, params);
    const row = res?.values?.[0] || {};
    return {
      draft: Number(row.draft || 0),
      partial: Number(row.partial || 0),
      paid: Number(row.paid || 0),
      debtor: Number(row.debtor || 0)
    };
  },

  /**
   * جمع تعداد/گردش‌مالی/سود در یک بازه‌ی تاریخ (فاز ۴ از این استفاده می‌کند؛
   * فاز ۱ فقط امضایش را مستند کرده بود).
   *
   * ✅ رفع باگ نسخه‌ی ۴.۱۹: نسخه‌ی قبلی این متد `json_extract(data,'$.totalTurnover')`
   * و `'$.totalProfit')` را جمع می‌زد — ولی این دو فیلد در ساختار واقعی فاکتور
   * (نگاه کن به `calcInvoiceTurnover`/`calcInvoiceProfit` در html8.html) هرگز روی
   * خودِ شیء فاکتور ذخیره نمی‌شوند؛ آن‌ها هر بار از روی آرایه‌ی `items[]` (فیلدهای
   * `lineTotal`/`itemProfit` هر آیتم) در سمت JS جمع زده می‌شوند. یعنی این متد از
   * روز اول (فاز ۱، فقط به‌عنوان امضا مستند شده بود) همیشه ۰ برمی‌گرداند — چون هیچ‌
   * کجا صدا زده نشده بود، این باگ تا امروز آشکار نشده بود. رفع شد با `json_each` روی
   * `data->'$.items'` تا واقعاً همان جمعی را بزند که `calcInvoiceTurnover`/`calcInvoiceProfit`
   * در JS می‌زنند.
   */
  /**
   * ✅ نسخه‌ی ۴.۲۸ (فاز ۷ — denormalize کردن آیتم‌ها، سناریو ۵) — این متد
   * دیگر `json_each` روی `invoices.data` نمی‌زند. `invoice_items` اکنون
   * یک کپی مسطح/denormalize‌شده‌ی هر ردیف items[] است (نگاه کن به
   * `additions.js#save` و `schema.js`)، با `date` از قبل کپی‌شده روی خودِ
   * آن جدول — پس `SUM` مستقیم با یک ایندکس ساده (`idx_invitems_date`)
   * جواب داده می‌شود، بدون این‌که SQLite مجبور باشد متن JSON را در زمان
   * کوئری parse کند. این دقیقاً همان راهکاری است که در یادداشت ۴.۲۵ (بخش ۶)
   * به‌عنوان ریسک/راه‌حل محتمل ثبت شده بود.
   */
  async getTotalsByRange(start, end) {
    const db = await getDb();
    const sql = `
      SELECT
        (SELECT COUNT(*) FROM invoices WHERE date >= ? AND date < ?) AS count,
        COALESCE((SELECT SUM(lineTotal) FROM invoice_items WHERE date >= ? AND date < ?),0) AS turnover,
        COALESCE((SELECT SUM(itemProfit) FROM invoice_items WHERE date >= ? AND date < ?),0) AS profit
    `;
    const res = await db.query(sql, [start, end, start, end, start, end]);
    const row = res?.values?.[0] || {};
    return { count: Number(row.count || 0), turnover: Number(row.turnover || 0), profit: Number(row.profit || 0) };
  },

  /**
   * جمع تعداد/گردش‌مالی/سود برای چند بازه‌ی تاریخ هم‌زمان (فاز ۴، اقدام ۱ —
   * جایگزین حلقه‌ی JS در `computeStatsData`/`html8.html` که روی کل آرایه‌ی
   * `invoices[]` اجرا می‌شد). هر بازه یک کوئری مستقل است (نه یک UNION غول‌پیکر)
   * چون تعداد بازه‌ها همیشه کم است (۵ تا ۱۲ تا، طبق `getStatsBuckets` در
   * html8.html) و سادگی/قابل‌اعتماد‌بودن روی سرعت میکروثانیه‌ای ترجیح داده شد —
   * همان تصمیمی که برای `getTotalsByRange` بالا هم گرفته شده.
   * @param {{start:string,end:string,label:string}[]} buckets - start/end باید رشته‌ی ISO باشند (همان قرارداد monthStart/monthEnd بقیه‌ی متدها)
   * @returns {Promise<{label:string,turnover:number,profit:number,count:number}[]>}
   */
  async getStatsBuckets(buckets = []) {
    if (!buckets.length) return [];
    const out = [];
    for (const b of buckets) {
      const totals = await InvoiceRepo.getTotalsByRange(b.start, b.end);
      out.push({ label: b.label, turnover: totals.turnover, profit: totals.profit, count: totals.count });
    }
    return out;
  },

  /**
   * سه «رکورددار» یک بازه (فاز ۴، ادامه‌ی اقدام ۱ — نسخه‌ی ۴.۲۰): پرفروش‌ترین
   * کارتن (بر اساس مجموع `cartonQty`)، مشتری با بیشترین گردش‌مالی (`lineTotal`)،
   * مشتری با بیشترین سود (`itemProfit`) — جایگزین حلقه‌ی JS در
   * `computeStatsLeaders`/`html8.html` که روی کل `invoices[]` اجرا می‌شد.
   *
   * **تقسیم مسئولیت عمدی:** این متد فقط Aggregation خام SQL را برمی‌گرداند
   * (نام کارتن/ابعاد/لایه‌ی خام، نه برچسب فارسی نهایی) — ساخت متن نمایشی
   * (`layerLabel`, فرمت ابعاد `L×W×H`) در `html8.html` (`fetchStatsLeaders`)
   * باقی می‌ماند، چون آن منطق کاملاً UI/i18n است، نه داده. همان تفکیکی که در
   * بقیه‌ی این فایل هم رعایت شده (Repo فقط SQL/داده، html8.html فرمت/رندر).
   *
   * **محدودیت آگاهانه:** برخلاف نسخه‌ی JS (که برای مشتری بدون customerId به
   * customerName بازمی‌گردد)، اینجا همیشه روی `customerId` گروه‌بندی می‌شود —
   * چون `invoices.customerId` در schema فاز ۱ `NOT NULL` است (نگاه کن به
   * `schema.js`)، این افتراق فقط در داده‌ی migrate‌شده/خراب با customerId
   * placeholder می‌تواند فرق کند، نه در مسیر عادی نوشتن.
   * @returns {Promise<{topCarton:object|null, topBuyer:object|null, topProfitCustomer:object|null}>}
   */
  /**
   * ✅ نسخه‌ی ۴.۲۸ — مثل getTotalsByRange بالا، حالا از `invoice_items`
   * می‌خواند (بدون json_each، بدون JOIN با invoices چون customerId/
   * customerName هم روی خودِ invoice_items denormalize شده‌اند). سه ایندکس
   * `idx_invitems_date` / `idx_invitems_date_customer` / `idx_invitems_date_carton`
   * دقیقاً برای این سه کوئری در schema.js اضافه شدند.
   */
  async getStatsLeaders(start, end) {
    const db = await getDb();

    const cartonRes = await db.query(`
      SELECT cartonName, cartonLength, cartonWidth, cartonHeight, layer,
        SUM(COALESCE(cartonQty,0)) AS qty
      FROM invoice_items
      WHERE date >= ? AND date < ?
      GROUP BY cartonName, cartonLength, cartonWidth, cartonHeight, layer
      HAVING qty > 0
      ORDER BY qty DESC
      LIMIT 1
    `, [start, end]);
    const cartonRow = cartonRes?.values?.[0] || null;

    const buyerRes = await db.query(`
      SELECT customerId AS customerId, customerName AS name,
        SUM(COALESCE(lineTotal,0)) AS turnover
      FROM invoice_items
      WHERE date >= ? AND date < ?
      GROUP BY customerId
      HAVING turnover != 0
      ORDER BY turnover DESC
      LIMIT 1
    `, [start, end]);
    const buyerRow = buyerRes?.values?.[0] || null;

    const profitRes = await db.query(`
      SELECT customerId AS customerId, customerName AS name,
        SUM(COALESCE(itemProfit,0)) AS profit
      FROM invoice_items
      WHERE date >= ? AND date < ?
      GROUP BY customerId
      HAVING profit != 0
      ORDER BY profit DESC
      LIMIT 1
    `, [start, end]);
    const profitRow = profitRes?.values?.[0] || null;

    return {
      topCarton: cartonRow ? {
        cartonName: cartonRow.cartonName || null,
        cartonLength: cartonRow.cartonLength ?? null,
        cartonWidth: cartonRow.cartonWidth ?? null,
        cartonHeight: cartonRow.cartonHeight ?? null,
        layer: cartonRow.layer ?? null,
        qty: Number(cartonRow.qty || 0)
      } : null,
      topBuyer: buyerRow ? { customerId: buyerRow.customerId, name: buyerRow.name, turnover: Number(buyerRow.turnover || 0) } : null,
      topProfitCustomer: profitRow ? { customerId: profitRow.customerId, name: profitRow.name, profit: Number(profitRow.profit || 0) } : null
    };
  },

  /**
   * ✅ فاز ۲ / گروه ب / مورد ۳ — شمارش فاکتور به‌ازای هر مشتری، به‌صورت یک
   * کوئری تجمیعی (aggregate) واحد به‌جای N کوئری جدا.
   *
   * چرا این متد لازم بود (نه یک getById/count ساده در حلقه): `renderCustomerViewCard`
   * در html8.html داخل یک `.map()` روی صفحه‌ی جاری مشتری‌ها صدا زده می‌شود — تبدیل
   * ساده‌ی آن تابع به async و صدا زدن یک کوئری per-customer یعنی «N+1 query»
   * (یک کوئری به‌ازای هر کارت مشتری، هر بار رندر لیست). این متد به‌جایش یک
   * `GROUP BY customerId` روی همان صفحه از customerIdها می‌زند — همیشه دقیقاً
   * ۱ کوئری، صرف‌نظر از تعداد مشتری‌های آن صفحه.
   *
   * @param {Array<number|string>} customerIds
   * @returns {Promise<Record<string, number>>} نگاشت customerId(به‌صورت رشته) → تعداد فاکتور
   */
  async getCountsByCustomerIds(customerIds = []) {
    const ids = (customerIds || []).filter(id => id != null);
    if (!ids.length) return {};
    const db = await getDb();
    const placeholders = ids.map(() => '?').join(',');
    const res = await db.query(
      `SELECT customerId, COUNT(*) AS c FROM invoices WHERE customerId IN (${placeholders}) GROUP BY customerId`,
      ids
    );
    const rows = res?.values || [];
    const map = {};
    for (const row of rows) map[String(row.customerId)] = Number(row.c || 0);
    return map;
  },

  /**
   * ✅ فاز ۲ / گروه ب / مورد ۶ — فهرست customerId های یکتای موجود در فاکتورها،
   * به‌همراه یک customerName نمونه برای هرکدام (برای زمانی که getCustomerById
   * رکورد مشتری را پیدا نکند — مثلاً مشتری بعداً حذف شده). این متد جایگزین
   * `[...new Set(invoices.map(i=>i.customerId))]` + `invoices.find(...)` در
   * `renderInvoicesFilterOptions` (html8.html) شد — همان الگوی GROUP BY
   * بدون تابع تجمیعی روی customerName که در getStatsLeaders بالا هم استفاده
   * شده (SQLite یک مقدار از یکی از ردیف‌های گروه انتخاب می‌کند؛ چون این ستون
   * فقط برای برچسبِ fallback است، نه منبع رسمی نام، این رفتار کافی است).
   *
   * @returns {Promise<Array<{customerId:*, customerName:string}>>}
   */
  async getDistinctCustomers() {
    const db = await getDb();
    const res = await db.query(
      `SELECT customerId, customerName FROM invoices WHERE customerId IS NOT NULL GROUP BY customerId`,
      []
    );
    return res?.values || [];
  },

  /**
   * ✅ فاز ۲ / گروه ب / مورد ۴ — به‌روزرسانی نام مشتری روی همه‌ی فاکتورهای
   * مرتبط، به‌صورت یک UPDATE گروهی به‌جای لوپ روی آرایه + `save` تکی برای
   * هر فاکتور (که در نسخه‌ی قبلی html8.html هر بار تغییر نام مشتری، یک
   * فراخوانی جدای Repo به‌ازای هر فاکتور آن مشتری می‌ساخت — دقیقاً همان
   * الگوی N+1 که در مورد ۳ برای شمارش رفع شد).
   *
   * ⚠️ نکته‌ی کشف‌شده حین همین مورد: `customerName` علاوه بر `invoices` روی
   * `invoice_items` هم denormalize شده (schema.js، برای کوئری‌های تب آمار
   * بدون JOIN). اگر فقط `invoices` را UPDATE می‌کردیم، بعد از تغییر نام
   * مشتری، تب آمار همچنان نام قدیمی را برای فاکتورهای قبلی نشان می‌داد —
   * پس هر دو جدول باید در یک تراکنش واحد به‌روز شوند.
   *
   * @param {number|string} customerId
   * @param {string} newName
   * @returns {Promise<boolean>}
   */
  async updateCustomerNameForInvoices(customerId, newName) {
    if (customerId == null || customerId === '') return false;
    const db = await getDb();
    return db.withTransaction(async (tx) => {
      await tx.run(`UPDATE invoices SET customerName = ? WHERE customerId = ?`, [newName, customerId]);
      await tx.run(`UPDATE invoice_items SET customerName = ? WHERE customerId = ?`, [newName, customerId]);
      return true;
    });
  },

  async getById(id) {
    const db = await getDb();
    const res = await db.query(`SELECT * FROM invoices WHERE id = ?`, [id]);
    const row = res?.values?.[0];
    return row ? rowToInvoice(row) : null;
  },

  /**
   * درج فاکتور + کسر موجودی در یک تراکنش (فاز ۵). از نسخه‌ی ۴.۲۲ به بعد
   * واقعاً از سه نقطه‌ی نوشتنِ html8.html صدا زده می‌شود (submitCalc2Invoice/
   * saveInvoiceEdit → dbSaveInvoiceWithStock) — قبلش فقط پیش‌نمایش/تست بود.
   * @param {object} invoice
   * @param {{itemId:any, qtyDelta:number, name?:string}[]} stockDeductions
   *
   * ⚠️ محدودیت آگاهانه (کشف‌شده در ۴.۲۲، نه پنهان‌شده): جدول `inventory` هیچ‌جا
   * از html8.html seed نمی‌شد (migrateFromIndexedDB.js مسیر جدای Capacitor است
   * و از html8.html اصلاً صدا زده نمی‌شود) — یعنی یک `UPDATE ... WHERE id=?`
   * ساده روی id ناموجود بی‌صدا صفر ردیف را تغییر می‌داد (نه خطا، فقط گم‌شدن
   * دلتا). به همین دلیل قبل از هر UPDATE، یک `INSERT OR IGNORE` با qty پایه‌ی
   * صفر می‌زنیم: اگر ردیف از قبل بود (مسیر عادی، مثل تست‌های قبلی این فایل)
   * IGNORE می‌شود و رفتار قبلی دقیقاً حفظ می‌شود؛ اگر نبود، دیگر دلتا گم نمی‌شود
   * — هرچند عدد پایه (۰) واقعی نیست تا یک seed/sync کامل inventory جدا اضافه شود
   * (خارج از دامنه‌ی این نسخه؛ نگاه کن به یادداشت ۴.۲۲ بالای سند نقشه‌راه).
   */
  async insertWithStockUpdate(invoice, stockDeductions = []) {
    const db = await getDb();
    return db.withTransaction(async (tx) => {
      for (const d of stockDeductions) {
        await tx.run(`INSERT OR IGNORE INTO inventory (id, name, qty) VALUES (?, ?, 0)`, [d.itemId, d.name || '']);
        await tx.run(`UPDATE inventory SET qty = qty - ? WHERE id = ?`, [d.qtyDelta, d.itemId]);
      }
      await InvoiceRepo.save(invoice, tx);
      return true;
    });
  },

  /** حذف فاکتور + بازگرداندن موجودی در یک تراکنش (فاز ۵). همان محدودیت/راهکار
   *  INSERT OR IGNORE بالا (insertWithStockUpdate) اینجا هم اعمال شده. */
  async deleteWithStockRestore(id, stockRestorations = []) {
    const db = await getDb();
    return db.withTransaction(async (tx) => {
      for (const r of stockRestorations) {
        await tx.run(`INSERT OR IGNORE INTO inventory (id, name, qty) VALUES (?, ?, 0)`, [r.itemId, r.name || '']);
        await tx.run(`UPDATE inventory SET qty = qty + ? WHERE id = ?`, [r.qtyDelta, r.itemId]);
      }
      await InvoiceRepo.remove(id, tx);
      return true;
    });
  },

  /** batch insert داخل یک تراکنش — برای مهاجرت اولیه و Restore بکاپ. */
  async bulkInsert(invoices = []) {
    if (!invoices.length) return 0;
    const db = await getDb();
    return db.withTransaction(async (tx) => {
      for (const inv of invoices) {
        await InvoiceRepo.save(inv, tx);
      }
      return invoices.length;
    });
  },

  /**
   * حذف کامل تمام ردیف‌های جدول invoices (نه DROP TABLE — schema/ایندکس‌ها
   * دست‌نخورده می‌مانند). برای `clearAllData`/`doRestore` در html8.html
   * (ریسک بخش ۶: این دو تابع قبلاً فقط IndexedDB را پاک می‌کردند، نه SQLite).
   * @returns {Promise<boolean>}
   */
  async clearAll() {
    const db = await getDb();
    // ✅ ۴.۲۸: invoice_items دیگر یک محاسبه‌ی مشتق‌شده‌ی زنده از invoices
    // نیست (یک جدول مستقل با کپی خودش است)، پس clearAllData/doRestore هم
    // باید صریحاً این جدول را پاک کنند — وگرنه بعد از یک «پاک کردن همه‌ی
    // داده‌ها»، تب آمار همچنان اعداد فاکتورهای حذف‌شده را جمع می‌زد. ترتیب
    // مثل remove() بالا: به‌خاطر FOREIGN KEY(invoiceId)، باید قبل از invoices
    // پاک شود.
    await db.run(`DELETE FROM invoice_items`, []);
    await db.run(`DELETE FROM invoices`, []);
    return true;
  }
};

/**
 * ادغام ۴ متد کشف‌شده در ریزمرحله‌های ۲.۳/۲.۵ (save/remove/getMonthCount/
 * getAllDates) — طبق دستورالعمل ادغام داخل خود InvoiceRepo.additions.js.
 * save/remove اینجا یک آرگومان دوم اختیاری `dbOverride` هم می‌پذیرند تا
 * insertWithStockUpdate/deleteWithStockRestore بالا بتوانند همان اتصال
 * تراکنشی (tx) را به آن‌ها پاس بدهند به‌جای باز کردن یک اتصال جدید.
 */
const additions = InvoiceRepoAdditions(getDb);
Object.assign(InvoiceRepo, {
  async save(invoice, dbOverride) {
    if (!dbOverride) return additions.save(invoice);
    // نسخه‌ی درون‌تراکنشی: همان SQL additions.save را با اتصال tx اجرا می‌کند.
    return InvoiceRepoAdditions(async () => dbOverride).save(invoice);
  },
  async remove(id, dbOverride) {
    if (!dbOverride) return additions.remove(id);
    return InvoiceRepoAdditions(async () => dbOverride).remove(id);
  },
  getMonthCount: additions.getMonthCount,
  getAllDates: additions.getAllDates
});

export default InvoiceRepo;
