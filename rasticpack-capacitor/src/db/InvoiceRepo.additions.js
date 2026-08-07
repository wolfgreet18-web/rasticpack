/**
 * ══════════════════════════════════════════════════════════════════════════
 * InvoiceRepo.additions.js
 * ──────────────────────────────────────────────────────────────────────────
 * چهار متدی که طبق روند پیاده‌سازی ریزمرحله‌های ۲.۳ / ۲.۴ / ۲.۵ نقشه‌راه
 * (roadmap-infinite-records-v4.md) کشف شدند و در فاز ۱ مستند نشده بودند،
 * ولی کد سمت 4.html (dbSaveInvoice / dbDeleteInvoiceRecord / renderInvMonthSwitcher /
 * buildInvMonthList) از این پس به آن‌ها متکی است:
 *
 *   InvoiceRepo.save(invoice)                                   ← ریزمرحله ۲.۳
 *   InvoiceRepo.remove(id)                                      ← ریزمرحله ۲.۳
 *   InvoiceRepo.getMonthCount({monthStart,monthEnd,exactDate})  ← ریزمرحله ۲.۵
 *   InvoiceRepo.getAllDates()                                   ← ریزمرحله ۲.۵
 *
 * ── نحوه‌ی ادغام در پروژه‌ی واقعی (rasticpack-capacitor/src/db/InvoiceRepo.js) ──
 * این فایل عمداً یک ماژول کامل و مستقل نیست؛ یک شیء `InvoiceRepoAdditions` صادر
 * می‌کند که متدهایش را باید داخل شیء/کلاس `InvoiceRepo` واقعی merge کنید، مثلاً:
 *
 *     import { InvoiceRepoAdditions } from './InvoiceRepo.additions.js';
 *     Object.assign(InvoiceRepo, InvoiceRepoAdditions(getDb));
 *
 * یا اگر InvoiceRepo.js شما به‌صورت یک شیء literal با متدهای export شده است،
 * می‌توانید بدنه‌ی هر تابع را مستقیم کپی و در همان الگو paste کنید — نکته‌ی
 * مهم فقط SQL و منطق map کردن ردیف‌هاست، نه امضای بیرونی ماژول.
 *
 * ⚠️ فرض‌های این فایل که باید با connection.js واقعی شما تطبیق داده شوند:
 *   ۱. یک تابع async `getDb()` وجود دارد که یک instance متصل از
 *      @capacitor-community/sqlite برمی‌گرداند (همان چیزی که در فاز ۱ نوشته شده،
 *      طبق سند: «اتصال idempotent» در connection.js).
 *   ۲. آن instance دو متد دارد:
 *        db.query(sql, params) → Promise<{ values: Array<object> }>
 *        db.run(sql, params)   → Promise<any>  (برای INSERT/UPDATE/DELETE)
 *      این دقیقاً API استاندارد پلاگین @capacitor-community/sqlite است
 *      (SQLiteDBConnection.query / .run) — اگر connection.js شما یک wrapper
 *      دیگر با نام متفاوت دارد (مثلاً exec/all/get)، فقط دو خط
 *      `const rows = await runQuery(...)` را با معادلش عوض کنید.
 *   ۳. نام جدول و ستون‌ها دقیقاً طبق schema.js مستندشده در نقشه‌راه است:
 *      invoices(id, customerId, customerName, date, status, sent,
 *               sentToProduction, paidAmount, totalSheets, editedAt, data)
 *      که ستون `data` شامل JSON کامل آبجکت فاکتور (شامل items[]) است.
 * ══════════════════════════════════════════════════════════════════════════
 */

/**
 * @param {() => Promise<any>} getDb  همان تابعی که در connection.js واقعی شما
 *   اتصال SQLite را برمی‌گرداند (idempotent — صدا زدن مکرر مشکلی ایجاد نمی‌کند).
 * @returns {object} شیء حاوی ۴ متد که باید با Object.assign به InvoiceRepo اضافه شود.
 */
export function InvoiceRepoAdditions(getDb) {
  return {
    /* ══════════════════════════════════════════════════════════════════
       save(invoice) — ریزمرحله ۲.۳
       ──────────────────────────────────────────────────────────────────
       upsert ساده‌ی یک فاکتور — بدون هیچ عارضه‌ی جانبی روی موجودی
       (کسر/بازگردانی qty ورق‌ها همچنان در جاوااسکریپت سمت 4.html انجام
       می‌شود و قبل از صدا زدن این متد، روی آرایه‌ی inventory اعمال شده).
       این متد جایگزین insertWithStockUpdate نیست — آن متد تراکنشی/موجودی‌محور
       برای فاز ۵ نگه داشته می‌شود؛ این یکی صرفاً یک ردیف را می‌نویسد.

       فیلدهای متغیر فاکتور (items, editedAt که به‌صورت شرطی است، هر فیلد
       اضافه‌ی دیگری که در آینده به آبجکت فاکتور اضافه شود) همگی داخل ستون
       JSON یعنی `data` ذخیره می‌شوند؛ ستون‌های مجزا فقط آن‌هایی هستند که
       باید ایندکس/فیلتر/GROUP BY بشوند (طبق schema.js).
       ══════════════════════════════════════════════════════════════════ */
    async save(invoice) {
      if (!invoice || invoice.id == null) {
        throw new Error('InvoiceRepo.save: invoice.id الزامی است.');
      }
      const db = await getDb();

      // فیلدهای ستونی مجزا — برای فیلتر/سورت/GROUP BY سریع
      const id = invoice.id;
      const customerId = invoice.customerId ?? null;
      const customerName = invoice.customerName ?? '';
      const date = invoice.date ?? new Date().toISOString();
      const status = invoice.status || 'paid'; // هم‌راستا با invoiceStatusOf در 4.html
      const sent = invoice.sent ? 1 : 0;
      const sentToProduction = invoice.sentToProduction ? 1 : 0;
      const paidAmount = invoice.paidAmount != null ? invoice.paidAmount : null;
      const totalSheets = invoice.totalSheets || 0;
      const editedAt = invoice.editedAt || null;

      // کل آبجکت (شامل items[] و هر فیلد دیگری) در ستون data به‌صورت JSON
      const data = JSON.stringify(invoice);

      // INSERT OR REPLACE بر مبنای PRIMARY KEY id — دقیقاً معنای «upsert»
      // که برای این متد لازم است (چه فاکتور جدید باشد چه ویرایش موجود).
      const sql = `
        INSERT INTO invoices
          (id, customerId, customerName, date, status, sent, sentToProduction, paidAmount, totalSheets, editedAt, data)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
          customerId=excluded.customerId,
          customerName=excluded.customerName,
          date=excluded.date,
          status=excluded.status,
          sent=excluded.sent,
          sentToProduction=excluded.sentToProduction,
          paidAmount=excluded.paidAmount,
          totalSheets=excluded.totalSheets,
          editedAt=excluded.editedAt,
          data=excluded.data
      `;
      await db.run(sql, [
        id, customerId, customerName, date, status,
        sent, sentToProduction, paidAmount, totalSheets, editedAt, data
      ]);
      return true;
    },

    /* ══════════════════════════════════════════════════════════════════
       remove(id) — ریزمرحله ۲.۳
       ──────────────────────────────────────────────────────────────────
       حذف ساده‌ی یک ردیف. بازگرداندن موجودی ورق (مثل deleteInvoice در
       4.html) قبل از صدا زدن این متد، در جاوااسکریپت روی آرایه‌ی inventory
       انجام شده — این متد فقط ردیف فاکتور را از دیتابیس پاک می‌کند.
       (معادل تراکنشی‌اش برای فاز ۵: deleteWithStockRestore)
       ══════════════════════════════════════════════════════════════════ */
    async remove(id) {
      if (id == null) throw new Error('InvoiceRepo.remove: id الزامی است.');
      const db = await getDb();
      await db.run(`DELETE FROM invoices WHERE id = ?`, [id]);
      return true;
    },

    /* ══════════════════════════════════════════════════════════════════
       getMonthCount({monthStart, monthEnd, exactDate}) — ریزمرحله ۲.۵
       ──────────────────────────────────────────────────────────────────
       شمارش «تعداد کل فاکتور» یک ماه شمسی (که از قبل توسط
       monthSortValToIsoRange در 4.html به بازه‌ی میلادی/ISO تبدیل شده)
       یا یک روز خاص — بدون فیلتر مشتری/جستجو/وضعیت (دقیقاً همان چیزی که
       زیر برچسب ماه/روز در سوییچر فاکتورها نمایش داده می‌شود).

       قرارداد: اگر exactDate پر باشد، اولویت با آن است (نادیده‌گرفتن
       monthStart/monthEnd) — دقیقاً هم‌راستا با فراخوان‌کننده در 4.html
       (fetchInvMonthCount) که وقتی selectedInvExactDate ست شده، monthSortVal
       را null می‌فرستد.
       ══════════════════════════════════════════════════════════════════ */
    async getMonthCount({ monthStart = null, monthEnd = null, exactDate = null } = {}) {
      const db = await getDb();

      if (exactDate) {
        // date در schema یک ISO datetime کامل است (نه فقط تاریخ)؛
        // پس برای «یک روز خاص» باید با substr/date() مقایسه شود —
        // معادل همان‌کاری که fallback حافظه در 4.html با
        // .toISOString().slice(0,10) انجام می‌دهد.
        const sql = `SELECT COUNT(*) AS c FROM invoices WHERE date(date) = date(?)`;
        const res = await db.query(sql, [exactDate]);
        return Number(res?.values?.[0]?.c || 0);
      }

      if (monthStart && monthEnd) {
        // قرارداد >= start و < end — دقیقاً همان قرارداد «< end» که در
        // بقیه‌ی جاهای این فایل (getStatsBuckets در 4.html) استفاده شده.
        const sql = `SELECT COUNT(*) AS c FROM invoices WHERE date >= ? AND date < ?`;
        const res = await db.query(sql, [monthStart, monthEnd]);
        return Number(res?.values?.[0]?.c || 0);
      }

      // نه exactDate نه بازه‌ی ماه داده شده — شمارش کل (حالت لبه‌ای،
      // در عمل توسط fetchInvMonthCount در 4.html هرگز این‌طور صدا زده نمی‌شود
      // چون همیشه یکی از این دو مقدار پر است، اما برای ایمنی مدیریت شده).
      const sql = `SELECT COUNT(*) AS c FROM invoices`;
      const res = await db.query(sql, []);
      return Number(res?.values?.[0]?.c || 0);
    },

    /* ══════════════════════════════════════════════════════════════════
       getAllDates() — ریزمرحله ۲.۵
       ──────────────────────────────────────────────────────────────────
       فقط ستون سبک `date` همه‌ی فاکتورها را برمی‌گرداند — نه کل رکورد با
       items و بقیه‌ی فیلدها. مصرف‌کننده (buildInvMonthList در 4.html) خودش
       این آرایه‌ی تاریخ‌های ISO را با getPersianMonthInfo به ماه شمسی
       گروه‌بندی می‌کند، چون تقویم شمسی در SQLite بومی نیست.

       نکته: buildInvMonthList در نسخه‌ی فعلی UI جایی صدا زده نمی‌شود
       (طبق یادداشت صداقت‌آمیز در نقشه‌راه) — این متد برای زمانی آماده است
       که یک انتخاب‌گر «فهرست کامل ماه‌ها» به UI اضافه شود.
       ══════════════════════════════════════════════════════════════════ */
    async getAllDates() {
      const db = await getDb();
      const sql = `SELECT date FROM invoices ORDER BY date DESC`;
      const res = await db.query(sql, []);
      const rows = res?.values || [];
      return rows.map(r => r.date);
    }
  };
}

/**
 * ── نمونه‌ی ادغام سریع (برای کپی مستقیم در انتهای InvoiceRepo.js واقعی) ──
 *
 *   import { InvoiceRepoAdditions } from './InvoiceRepo.additions.js';
 *   import { getDb } from './connection.js';   // یا هر نامی که فایل واقعی شما دارد
 *
 *   Object.assign(InvoiceRepo, InvoiceRepoAdditions(getDb));
 *
 *   export default InvoiceRepo;
 *
 * بعد از ادغام، این خط در انتهای src/db/InvoiceRepo.js باید باعث شود
 * که در 4.html (بدون هیچ تغییر دیگری) موارد زیر از fallback حافظه خارج
 * و مستقیماً از SQLite تغذیه شوند:
 *   - dbSaveInvoice / dbDeleteInvoiceRecord   (چون از قبل window.__InvoiceRepo.save/remove را صدا می‌زنند)
 *   - fetchInvMonthCount                       (چون از window.__InvoiceRepo.getMonthCount استفاده می‌کند)
 *   - fetchInvMonthList                        (چون از window.__InvoiceRepo.getAllDates استفاده می‌کند)
 */
