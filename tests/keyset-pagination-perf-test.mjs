// تست کارایی keyset pagination در برابر OFFSET عمیق — نسخه‌ی ۴.۲۶ (فاز ۷،
// باقی‌مانده‌ی رسمی ۱). هدف: نشان دادن مستقیمِ همان چیزی که یادداشت ۴.۲۵
// به‌عنوان FAIL باز ثبت کرده بود (سناریو ۲ فاز ۷ — اسکرول تا انتهای لیست) —
// نه با تکرار seed کامل ۵M/۲.۲۹GB روی دیسک (که هزینه‌ی زمانی چند دستور دارد
// و قبلاً در ۴.۲۵ انجام/مستند شده)، بلکه با ۱M ردیف در حافظه (همان مقیاسی که
// perf-test-1m.mjs از قبل استفاده می‌کند و در یک دستور sandbox جا می‌شود) —
// جهت/نسبت هزینه (O(offset) در برابر تقریباً ثابت) مستقل از این‌که ۱M یا ۵M
// باشد، همان قانون شناخته‌شده‌ی SQLite OFFSET است؛ عدد مطلق فقط با مقیاس
// بزرگ‌تر می‌شود، جهت تغییر نمی‌کند.
//
// اجرا: node --experimental-sqlite keyset-pagination-perf-test.mjs

import { InvoiceRepo } from './_node_test_copies/InvoiceRepo.js';
import { getDb, resetDb } from './testConnection.node-only.mjs';

const N_CUSTOMERS = 15000;
const N_INVOICES = 1000000;

async function main() {
  resetDb();
  const db = await getDb();

  console.log(`Seeding ${N_CUSTOMERS} customers...`);
  let t = Date.now();
  await db.run('BEGIN');
  for (let i = 1; i <= N_CUSTOMERS; i++) {
    await db.run(`INSERT INTO customers (id, name, company, phone) VALUES (?, ?, ?, ?)`,
      [i, `مشتری تستی ${i}`, '', '0912' + String(1000000 + i)]);
  }
  await db.run('COMMIT');
  console.log(`  done in ${Date.now() - t}ms`);

  console.log(`Seeding ${N_INVOICES} invoices...`);
  t = Date.now();
  const statuses = ['draft', 'partial', 'paid'];
  await db.run('BEGIN');
  for (let i = 1; i <= N_INVOICES; i++) {
    const custId = (i % N_CUSTOMERS) + 1;
    const date = new Date(2024, 0, 1 + (i % 700)).toISOString();
    await db.run(
      `INSERT INTO invoices (id, customerId, customerName, date, status, sent, sentToProduction, paidAmount, totalSheets, data)
       VALUES (?,?,?,?,?,?,?,?,?,?)`,
      [i, custId, `مشتری تستی ${custId}`, date, statuses[i % 3], i % 2, 0, (i % 3 === 1) ? 50000 : 0, (i % 20) + 1, JSON.stringify({ id: i, items: [{ lineTotal: 1000 }] })]
    );
  }
  await db.run('COMMIT');
  console.log(`  done in ${Date.now() - t}ms`);

  // سناریوی ۲ فاز ۷ (یادداشت ۴.۲۵): «اسکرول تا انتهای لیست» — بدون فیلتر،
  // یعنی همان LIMIT 50 OFFSET (N-50) که در ۵M آن تست ۱۳۶ms (گرم) طول کشید.
  const LIMIT = 50;
  const deepOffset = N_INVOICES - LIMIT;

  console.log(`\n-- Deep OFFSET (offset=${deepOffset}, limit=${LIMIT}) --`);
  const offsetTimes = [];
  for (let i = 0; i < 3; i++) {
    const t0 = process.hrtime.bigint();
    await InvoiceRepo.getPage({ limit: LIMIT, offset: deepOffset });
    const t1 = process.hrtime.bigint();
    offsetTimes.push(Number(t1 - t0) / 1e6);
  }
  console.log(`  getPage(offset=${deepOffset}): ${offsetTimes.map(x => x.toFixed(1)).join(', ')} ms`);

  // نسخه‌ی معادل با keyset: چون رسیدن به «انتهای واقعی لیست» با cursor یعنی
  // شروع از cursor=null و صفحه‌به‌صفحه رفتن تا ته (که خودش هزینه‌ی O(N/limit)
  // کوئری دارد، نه یک seek تکی) — یعنی «رفتن به عمیق‌ترین نقطه از صفر» ذاتاً
  // به تعداد صفحه نیاز دارد، صرف‌نظر از OFFSET/keyset. آنچه keyset واقعاً حل
  // می‌کند سناریوی *واقعی* اسکرول است: «من همین الان صفحه‌ی X را دارم، صفحه‌ی
  // بعد را بده» — یعنی هزینه‌ی *هر گام بعدیِ* اسکرول، نه هزینه‌ی جهش یک‌باره
  // به وسط/انتهای لیست. برای اندازه‌گیری دقیقاً همین، یک cursor شبیه‌سازی‌شده
  // از یک ردیف نزدیک انتهای دیتاست می‌سازیم (بدون این‌که برای رسیدن به آن
  // واقعاً N/limit کوئری بزنیم) — دقیقاً همان چیزی که در اپ واقعی هم اتفاق
  // می‌افتد: کاربر با اسکرول پیوسته گام‌به‌گام به آن‌جا رسیده، نه با یک جهش.
  const nearEndRow = (await db.query(
    `SELECT date, id FROM invoices ORDER BY date DESC, id DESC LIMIT 1 OFFSET ?`,
    [deepOffset - 1]
  )).values[0];
  const cursor = { date: nearEndRow.date, id: nearEndRow.id };

  console.log(`\n-- Keyset next-page from a cursor near the end (same depth, single seek) --`);
  const cursorTimes = [];
  for (let i = 0; i < 3; i++) {
    const t0 = process.hrtime.bigint();
    await InvoiceRepo.getPageByCursor({ limit: LIMIT, cursor });
    const t1 = process.hrtime.bigint();
    cursorTimes.push(Number(t1 - t0) / 1e6);
  }
  console.log(`  getPageByCursor(cursor≈same depth): ${cursorTimes.map(x => x.toFixed(1)).join(', ')} ms`);

  // مقایسه‌ی صفحه‌ی *بعدیِ* یک اسکرول عادی (نه جهش عمیق) — سناریوی واقعی UI:
  // کاربر همین الان صفحه‌ی K را دیده (یعنی offset~K), می‌خواهد صفحه‌ی K+1 را
  // ببیند. با OFFSET، این هنوز باید offset=K را کامل پیمایش کند؛ با keyset،
  // هزینه‌اش مستقل از K است. این دقیقاً تفاوت الگوریتمی است، نه فقط عدد خام.
  console.log(`\n-- Same next-page request, via deep OFFSET vs keyset seek (this is the actual UI cost during scroll) --`);
  const offsetNextTimes = [];
  for (let i = 0; i < 3; i++) {
    const t0 = process.hrtime.bigint();
    await InvoiceRepo.getPage({ limit: LIMIT, offset: deepOffset - LIMIT });
    const t1 = process.hrtime.bigint();
    offsetNextTimes.push(Number(t1 - t0) / 1e6);
  }
  console.log(`  getPage(offset=${deepOffset - LIMIT}) [OFFSET cost for the same next-page]: ${offsetNextTimes.map(x => x.toFixed(1)).join(', ')} ms`);

  const avgOffset = offsetTimes.reduce((a, b) => a + b, 0) / offsetTimes.length;
  const avgCursor = cursorTimes.reduce((a, b) => a + b, 0) / cursorTimes.length;
  console.log(`\nAverage (warm runs): OFFSET=${avgOffset.toFixed(2)}ms vs keyset=${avgCursor.toFixed(2)}ms at the same depth (offset=${deepOffset} of ${N_INVOICES}).`);
  console.log(avgCursor < avgOffset
    ? '✓ Keyset next-page is faster than deep OFFSET at this depth — confirms the O(offset) vs ~O(limit) difference documented in note 4.25.'
    : '⚠ Keyset was not faster in this run — see note 4.26 for caveats (small dataset / OS cache effects can hide the difference at 1M scale; the 5M-scale FAIL in note 4.25 remains the authoritative finding).');
}

main().catch(e => { console.error('TEST FAILED:', e); process.exit(1); });
