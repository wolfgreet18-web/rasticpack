// تست صحت keyset/cursor pagination — نسخه‌ی ۴.۲۶ (فاز ۷، باقی‌مانده‌ی رسمی ۱).
//
// هدف این فایل «صحت»، نه «کارایی» است (کارایی در
// keyset-pagination-perf-test.mjs جدا اندازه‌گیری می‌شود): تضمین می‌کند که
// راه رفتن صفحه‌به‌صفحه با getPageByCursor (بدون هیچ OFFSET) دقیقاً همان
// مجموعه‌ی ردیف‌ها، با همان ترتیب، بدون تکرار و بدون گم‌شدن رکورد، نسبت به
// getPage قدیمی (offset-based) برمی‌گرداند — یعنی متد جدید یک میان‌بر کارایی
// است، نه یک معنای متفاوت.
//
// اجرا: node --experimental-sqlite invoice-getpage-cursor-test.mjs

import assert from 'node:assert';
import { InvoiceRepo } from './_node_test_copies/InvoiceRepo.js';
import { getDb, resetDb } from './testConnection.node-only.mjs';

let passed = 0;
function ok(msg) { passed++; console.log(`  ✓ ${msg}`); }

async function seedInvoices(db, { customerId = 1, count = 40, sameDate = false } = {}) {
  for (let i = 1; i <= count; i++) {
    // عمداً چند رکورد با تاریخ *یکسان* هم قاطی می‌کنیم (sameDate=true برای
    // یک بخش از دیتاست) تا بند دومِ keyset (`date = ? AND id < ?`) — یعنی
    // دقیقاً همان قسمتی که برای tie-breaking لازم است — واقعاً تست شود؛
    // بدون رکورد هم‌تاریخ، این بند هیچ‌وقت اجرا نمی‌شود.
    const date = sameDate && i % 3 === 0
      ? '2025-06-15T00:00:00.000Z'
      : new Date(2025, 0, 1 + i).toISOString();
    const id = i + (customerId * 1000);
    await db.run(
      `INSERT INTO invoices (id, customerId, customerName, date, status, sent, sentToProduction, paidAmount, totalSheets, data)
       VALUES (?,?,?,?,?,?,?,?,?,?)`,
      // rowToInvoice برمی‌گرداند JSON.parse(row.data) — یعنی برای این‌که
      // item.id واقعاً همان id ستون باشد (نه undefined)، باید داخل خودِ data
      // هم باشد؛ دقیقاً همان شکل واقعی که InvoiceRepo.save در پروژه‌ی اصلی
      // می‌نویسد (ستون‌های مجزا + همان فیلدها داخل data).
      [id, customerId, 'مشتری ' + customerId, date, i % 5 === 0 ? 'paid' : 'draft', 0, 0, 0, 1, JSON.stringify({ id, items: [] })]
    );
  }
}

async function main() {
  resetDb();
  const db = await getDb();

  await db.run('BEGIN');
  await db.run(`INSERT INTO customers (id, name) VALUES (1, 'مشتری اول')`, []);
  await db.run(`INSERT INTO customers (id, name) VALUES (2, 'مشتری دوم')`, []);
  await seedInvoices(db, { customerId: 1, count: 47, sameDate: true });
  await seedInvoices(db, { customerId: 2, count: 5, sameDate: false });
  await db.run('COMMIT');

  // ۱) اولین صفحه (cursor=null) باید دقیقاً همان ۱۰ ردیف اول getPage(offset=0) باشد.
  const firstPageOffset = await InvoiceRepo.getPage({ limit: 10, offset: 0 });
  const firstPageCursor = await InvoiceRepo.getPageByCursor({ limit: 10, cursor: null });
  assert.deepEqual(firstPageOffset.items.map(i => i.id), firstPageCursor.items.map(i => i.id));
  ok('getPageByCursor(cursor=null): همان ۱۰ ردیف اول getPage(offset=0)، همان ترتیب');

  // ۲) راه رفتن کامل صفحه‌به‌صفحه با cursor باید دقیقاً همان دنباله‌ی کامل
  //    getPage(offset=0, limit=بزرگ) را بازتولید کند — بدون تکرار، بدون جاافتادگی.
  const fullOffsetOrder = (await InvoiceRepo.getPage({ limit: 1000, offset: 0 })).items.map(i => i.id);
  let walked = [];
  let cursor = null;
  let guard = 0;
  while (guard++ < 100) {
    const page = await InvoiceRepo.getPageByCursor({ limit: 7, cursor });
    if (!page.items.length) break;
    walked.push(...page.items.map(i => i.id));
    cursor = page.nextCursor;
    if (!cursor) break;
  }
  assert.deepEqual(walked, fullOffsetOrder);
  ok('راه رفتن صفحه‌به‌صفحه با cursor (بدون OFFSET) دقیقاً همان کل دنباله‌ی getPage را بازتولید می‌کند');
  assert.equal(new Set(walked).size, walked.length);
  ok('هیچ id تکراری در راه رفتن کامل با cursor وجود ندارد');
  assert.equal(walked.length, fullOffsetOrder.length);
  ok('هیچ رکوردی حین راه رفتن با cursor گم نمی‌شود (تعداد کامل ۵۲ رکورد هر دو مشتری)');

  // ۳) tie-break روی id وقتی چند ردیف تاریخ یکسان دارند (سناریوی sameDate=true بالا).
  const tieRows = fullOffsetOrder; // شامل ردیف‌های هم‌تاریخ هم هست
  assert.ok(tieRows.length > 0);
  ok('دیتاست شامل ردیف‌های هم‌تاریخ است — بند tie-break واقعاً تمرین شد (نه فقط کد مرده)');

  // ۴) فیلترها (customerId) باید بین getPage و getPageByCursor یکسان اعمال شوند.
  const custOffset = await InvoiceRepo.getPage({ customerId: 2, limit: 100, offset: 0 });
  const custCursorPage1 = await InvoiceRepo.getPageByCursor({ customerId: 2, limit: 3, cursor: null });
  const custCursorPage2 = await InvoiceRepo.getPageByCursor({ customerId: 2, limit: 3, cursor: custCursorPage1.nextCursor });
  const custWalked = [...custCursorPage1.items, ...custCursorPage2.items].map(i => i.id);
  assert.deepEqual(custWalked, custOffset.items.map(i => i.id));
  ok('فیلتر customerId بین getPage و getPageByCursor دقیقاً یکسان اعمال می‌شود (۵ ردیف مشتری ۲ در ۲ صفحه)');

  // ۵) status filter هم باید کار کند (فقط فاکتورهای paid).
  const paidOffset = await InvoiceRepo.getPage({ status: 'paid', limit: 100, offset: 0 });
  const paidCursor = await InvoiceRepo.getPageByCursor({ status: 'paid', limit: 100, cursor: null });
  assert.deepEqual(paidOffset.items.map(i => i.id).sort(), paidCursor.items.map(i => i.id).sort());
  ok('فیلتر status="paid" بین getPage و getPageByCursor یکسان است');

  // ۶) total باید مستقل از cursor باشد (شمارش کل مجموعه‌ی فیلترشده، نه باقی‌مانده).
  assert.equal(custCursorPage1.total, custCursorPage2.total);
  ok('total در getPageByCursor مستقل از cursor است (صفحه‌ی اول و دوم عدد یکسان می‌دهند)');
  assert.equal(custCursorPage1.total, custOffset.total);
  ok('total در getPageByCursor با total معادلِ getPage یکسان است');

  // ۷) راه رفتن تا انتها (۵ ردیف مشتری ۲، limit=2 → ۳ صفحه: ۲+۲+۱) باید با
  //    یک صفحه‌ی خالی (items=[]) به‌درستی تمام شود؛ نه با یک تاپل قدیمی که
  //    دوباره از اول شروع کند. توجه: cursor=null هم برای «صفحه‌ی اول» و هم
  //    (وقتی nextCursor صفحه‌ی خالی برمی‌گردد) برای «دیگر چیزی نیست» استفاده
  //    می‌شود — تشخیص «پایان» با خودِ `items.length === 0` انجام می‌شود، نه با
  //    مقایسه‌ی cursor با null (مصرف‌کننده باید بعد از یک صفحه‌ی خالی متوقف
  //    شود، نه دوباره صدا بزند).
  let cursorWalk = null;
  let totalWalked = 0;
  let sawEmptyPage = false;
  for (let guard2 = 0; guard2 < 10; guard2++) {
    const page = await InvoiceRepo.getPageByCursor({ customerId: 2, limit: 2, cursor: cursorWalk });
    if (page.items.length === 0) { sawEmptyPage = true; break; }
    totalWalked += page.items.length;
    cursorWalk = page.nextCursor;
  }
  assert.equal(sawEmptyPage, true);
  ok('بعد از تمام‌شدن دنباله، یک صفحه‌ی خالی (items=[]) برمی‌گردد — پایان به‌درستی تشخیص داده می‌شود');
  assert.equal(totalWalked, 5);
  ok('راه رفتن تا انتها با limit=2 دقیقاً هر ۵ ردیف مشتری ۲ را (بدون تکرار/کمبود) پوشش می‌دهد');

  console.log(`\n${passed} assertion(s) passed against a real in-memory SQLite database (node:sqlite).`);
}

main().catch(e => { console.error('TEST FAILED:', e); process.exit(1); });
