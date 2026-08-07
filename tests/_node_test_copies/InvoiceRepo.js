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
  async getPage({ monthStart, monthEnd, exactDate, status, customerId, search, limit = 50, offset = 0 } = {}) {
    const db = await getDb();
    const { sql: whereBase, params: whereParams } = buildWhere({ monthStart, monthEnd, exactDate, customerId, search });

    let where = whereBase;
    const params = whereParams.slice();
    if (status === 'debtor') {
      where = where ? `${where} AND sent = 1 AND status != 'paid'` : `WHERE sent = 1 AND status != 'paid'`;
    } else if (status) {
      // «غیر بدهکار» یعنی وضعیت‌های draft/partial باید sent=0 باشند تا با
      // isInvoiceDebtor در html8.html هم‌خوان بمانند (paid هیچ‌وقت بدهکار نیست).
      const notDebtorClause = status === 'paid' ? `status = ?` : `status = ? AND sent = 0`;
      where = where ? `${where} AND ${notDebtorClause}` : `WHERE ${notDebtorClause}`;
      params.push(status);
    }

    const totalRes = await db.query(`SELECT COUNT(*) AS c FROM invoices ${where}`, params);
    const total = Number(totalRes?.values?.[0]?.c || 0);

    const pageRes = await db.query(
      `SELECT * FROM invoices ${where} ORDER BY date DESC LIMIT ? OFFSET ?`,
      [...params, limit, offset]
    );
    const items = (pageRes?.values || []).map(rowToInvoice);
    return { items, total };
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
   */
  async getTotalsByRange(start, end) {
    const db = await getDb();
    const sql = `
      SELECT
        COUNT(*) AS count,
        COALESCE(SUM(json_extract(data,'$.totalTurnover')),0) AS turnover,
        COALESCE(SUM(json_extract(data,'$.totalProfit')),0) AS profit
      FROM invoices WHERE date >= ? AND date < ?
    `;
    const res = await db.query(sql, [start, end]);
    const row = res?.values?.[0] || {};
    return { count: Number(row.count || 0), turnover: Number(row.turnover || 0), profit: Number(row.profit || 0) };
  },

  async getById(id) {
    const db = await getDb();
    const res = await db.query(`SELECT * FROM invoices WHERE id = ?`, [id]);
    const row = res?.values?.[0];
    return row ? rowToInvoice(row) : null;
  },

  /**
   * درج فاکتور + کسر موجودی در یک تراکنش (فاز ۵). عمداً هنوز جای دیگری
   * صدا زده نمی‌شود — منطق موجودی هنوز سمت جاوااسکریپت (html8.html) است.
   * @param {object} invoice
   * @param {{itemId:any, qtyDelta:number}[]} stockDeductions
   */
  async insertWithStockUpdate(invoice, stockDeductions = []) {
    const db = await getDb();
    return db.withTransaction(async (tx) => {
      for (const d of stockDeductions) {
        await tx.run(`UPDATE inventory SET qty = qty - ? WHERE id = ?`, [d.qtyDelta, d.itemId]);
      }
      await InvoiceRepo.save(invoice, tx);
      return true;
    });
  },

  /** حذف فاکتور + بازگرداندن موجودی در یک تراکنش (فاز ۵). */
  async deleteWithStockRestore(id, stockRestorations = []) {
    const db = await getDb();
    return db.withTransaction(async (tx) => {
      for (const r of stockRestorations) {
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
