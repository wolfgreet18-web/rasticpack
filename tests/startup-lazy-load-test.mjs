// تست بار قبل/بعد برای ریزمرحله‌ی ۲.۷.۵ (نسخه‌ی ۴.۱۳): مقایسه‌ی هزینه‌ی خواندن
// «کل جدول» (رفتار قدیمی loadState با idbGetAll) در برابر «فقط یک صفحه‌ی محدود»
// (رفتار جدید loadState با idbGetAllLimited) روی مقیاس هدف رسمی نقشه‌راه (۱M فاکتور).
//
// ══ محدودیت مهم (صادقانه، نه پنهان‌شده) ══
// IndexedDB واقعی در Node در دسترس نیست (بدون دسترسی شبکه برای نصب fake-indexeddb
// در این محیط). به‌جای شبیه‌سازی، همان الگویی که بقیه‌ی تست‌های این پوشه هم استفاده
// می‌کنند به کار رفته: node:sqlite به‌عنوان یک **پروکسی معتبر** برای رفتار خواندن
// bounded-vs-full روی یک storage engine واقعی (نه mock حافظه‌ای) — چون هر دو موتور
// (IndexedDB با cursor+limit، SQL با LIMIT) از نظر الگوریتمی همان ویژگی را دارند:
// هزینه‌ی خواندن با LIMIT به تعداد رکورد بازگشتی وابسته است، نه به اندازه‌ی کل جدول؛
// هزینه‌ی خواندن کامل با اندازه‌ی کل جدول رشد می‌کند. عدد دقیق میلی‌ثانیه‌ی IndexedDB
// روی دستگاه واقعی هنوز اندازه‌گیری نشده — طبق همان محدودیت مستندشده در
// tests/README.md برای بقیه‌ی تست‌ها (رفتار موتور واقعی Capacitor/مرورگر).
//
// اجرا: node --experimental-sqlite startup-lazy-load-test.mjs

import { getDb, resetDb } from './testConnection.node-only.mjs';

const N_INVOICES = 1000000;
const STARTUP_PAGE_SIZE = 500; // همان مقدار استفاده‌شده در loadState (html8.html)

async function main() {
  resetDb();
  const db = await getDb();

  // یک مشتری برای رعایت FOREIGN KEY(customerId) → customers(id) (schema.js)
  await db.run(`INSERT INTO customers (id, name, company, phone) VALUES (?, ?, ?, ?)`,
    [1, 'مشتری تستی', '', '09120000000']);

  console.log(`Seeding ${N_INVOICES} invoices...`);
  let t = Date.now();
  await db.run('BEGIN');
  for (let i = 1; i <= N_INVOICES; i++) {
    const date = new Date(2024, 0, 1 + (i % 700)).toISOString();
    await db.run(
      `INSERT INTO invoices (id, customerId, customerName, date, status, sent, sentToProduction, paidAmount, totalSheets, data)
       VALUES (?,?,?,?,?,?,?,?,?,?)`,
      [i, 1, `مشتری ${i}`, date, 'draft', 0, 0, 0, 1, JSON.stringify({ items: [] })]
    );
  }
  await db.run('COMMIT');
  console.log(`  done in ${Date.now() - t}ms\n`);

  // ── رفتار قدیمی (قبل از ۴.۱۲): معادل idbGetAll('invoices') — خواندن کامل جدول
  t = Date.now();
  const full = await db.query(`SELECT * FROM invoices`, []);
  const fullMs = Date.now() - t;
  console.log(`«قبل» — خواندن کامل جدول (idbGetAll معادل): ${fullMs}ms برای ${full.values.length} ردیف`);

  // ── رفتار جدید (۴.۱۲): معادل idbGetAllLimited('invoices', 500) — خواندن محدود
  t = Date.now();
  const page = await db.query(`SELECT * FROM invoices ORDER BY id DESC LIMIT ?`, [STARTUP_PAGE_SIZE]);
  const pageMs = Date.now() - t;
  console.log(`«بعد» — خواندن صفحه‌ی محدود (idbGetAllLimited معادل): ${pageMs}ms برای ${page.values.length} ردیف`);

  console.log(`\nنسبت سرعت اولین رندر: ~${(fullMs / Math.max(pageMs, 1)).toFixed(1)}× سریع‌تر (روی node:sqlite)`);

  let pass = 0, fail = 0;
  function assert(cond, msg) { if (cond) pass++; else { fail++; console.error('❌ FAIL:', msg); } }

  assert(page.values.length === STARTUP_PAGE_SIZE, `صفحه‌ی محدود باید دقیقاً ${STARTUP_PAGE_SIZE} ردیف برگرداند`);
  assert(full.values.length === N_INVOICES, 'خواندن کامل باید همه‌ی ردیف‌ها را برگرداند');
  assert(pageMs <= fullMs, 'خواندن محدود نباید کندتر از خواندن کامل باشد');
  assert(Number(page.values[0].id) === N_INVOICES, 'اولین رکورد صفحه‌ی محدود باید جدیدترین id باشد (ORDER BY id DESC)');

  console.log(`\n${pass} پاس، ${fail} خطا`);
  if (fail > 0) process.exit(1);
}

main().catch(e => { console.error(e); process.exit(1); });
