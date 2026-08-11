// تست شمارش سقف‌دار (countCap/totalIsExact) روی InvoiceRepo.getPage —
// نسخه‌ی ۴.۲۵. کشف‌شده حین تست فشار واقعی فاز ۷ روی ۵M فاکتور: getPage قبل
// از این نسخه یک COUNT(*) خام می‌زد (همان باگی که CustomerRepo.searchByName
// در نسخه‌ی ۴.۱۰ رفع کرده بود، ولی هیچ‌وقت به InvoiceRepo اعمال نشده بود).
//
// اجرا: node --experimental-sqlite invoice-getpage-cap-test.mjs

import assert from 'node:assert';
import { InvoiceRepo } from './_node_test_copies/InvoiceRepo.js';
import { getDb, resetDb } from './testConnection.node-only.mjs';

let passed = 0;
function ok(msg) { passed++; console.log(`  ✓ ${msg}`); }

async function main() {
  resetDb();
  const db = await getDb();

  await db.run('BEGIN');
  await db.run(`INSERT INTO customers (id, name) VALUES (1, 'مشتری تست')`, []);
  // ۵۰۰۰ فاکتور برای مشتری ۱ (بیشتر از پیش‌فرض countCap=2000)
  for (let i = 1; i <= 5000; i++) {
    await db.run(
      `INSERT INTO invoices (id, customerId, customerName, date, status, sent, sentToProduction, paidAmount, totalSheets, data)
       VALUES (?,?,?,?,?,?,?,?,?,?)`,
      [i, 1, 'مشتری تست', new Date(2024, 0, 1).toISOString(), 'paid', 0, 0, 0, 1, JSON.stringify({ items: [] })]
    );
  }
  // ۳ فاکتور برای مشتری ۲ (زیر سقف)
  await db.run(`INSERT INTO customers (id, name) VALUES (2, 'مشتری دوم')`, []);
  for (let i = 5001; i <= 5003; i++) {
    await db.run(
      `INSERT INTO invoices (id, customerId, customerName, date, status, sent, sentToProduction, paidAmount, totalSheets, data)
       VALUES (?,?,?,?,?,?,?,?,?,?)`,
      [i, 2, 'مشتری دوم', new Date(2024, 0, 1).toISOString(), 'paid', 0, 0, 0, 1, JSON.stringify({ items: [] })]
    );
  }
  await db.run('COMMIT');

  // ۱) مشتری با ۵۰۰۰ فاکتور، سقف پیش‌فرض ۲۰۰۰ → total باید همان سقف باشد، totalIsExact=false
  const capped = await InvoiceRepo.getPage({ customerId: 1, limit: 10, offset: 0 });
  assert.equal(capped.total, 2000);
  ok('getPage: total == countCap (2000) when real match count (5000) exceeds it, not a fabricated 5000');
  assert.equal(capped.totalIsExact, false);
  ok('getPage: totalIsExact=false when capped');
  assert.equal(capped.items.length, 10);
  ok('getPage: page items unaffected by capping (LIMIT/OFFSET on the real query still returns 10 rows)');

  // ۲) مشتری با ۳ فاکتور، زیر سقف → total دقیق، totalIsExact=true
  const exact = await InvoiceRepo.getPage({ customerId: 2, limit: 10, offset: 0 });
  assert.equal(exact.total, 3);
  ok('getPage: total is the true exact count (3) when under the cap');
  assert.equal(exact.totalIsExact, true);
  ok('getPage: totalIsExact=true when under the cap');

  // ۳) سقف سفارشی بزرگ‌تر از تعداد واقعی → دوباره دقیق می‌شود
  const raisedCap = await InvoiceRepo.getPage({ customerId: 1, limit: 10, offset: 0, countCap: 1000000 });
  assert.equal(raisedCap.total, 5000);
  ok('getPage: total is exact (5000) once countCap is explicitly raised above the real match count');
  assert.equal(raisedCap.totalIsExact, true);
  ok('getPage: totalIsExact=true once cap is raised above real count');

  // ۴) پیش‌فرض بدون هیچ فیلتری (کل جدول) هم مسیر سقف‌دار را طی می‌کند، کرش نمی‌کند
  const unfiltered = await InvoiceRepo.getPage({ limit: 1, offset: 0 });
  assert.equal(unfiltered.total, 2000); // ۵۰۰۳ ردیف کل، سقف پیش‌فرض ۲۰۰۰
  ok('getPage: capped counting also applies with no filters (empty WHERE clause path)');

  console.log(`\n${passed} assertion(s) passed against a real in-memory SQLite database (node:sqlite).`);
}

main().catch(e => { console.error('TEST FAILED:', e); process.exit(1); });
