// تست InvoiceRepo.getDistinctCustomers — فاز ۲ نقشه‌راه SQLite / گروه ب / مورد ۶
// (renderInvoicesFilterOptions در html8.html). هدف این متد: جایگزینی
// `[...new Set(invoices.map(i=>i.customerId))]` + `invoices.find(...)` (کاملاً
// sync روی آرایه‌ی حافظه) با یک کوئری SQLite واحد که customerId های یکتا +
// یک customerName نمونه برای هرکدام را برمی‌گرداند.
//
// اجرا: node --experimental-sqlite invoice-distinct-customers-test.mjs

import assert from 'node:assert';
import { InvoiceRepo } from './_node_test_copies/InvoiceRepo.js';
import { getDb, resetDb } from './testConnection.node-only.mjs';

let passed = 0;
function ok(msg) { passed++; console.log(`  ✓ ${msg}`); }

async function seedInvoice(db, id, customerId, customerName, date) {
  await db.run(
    `INSERT INTO invoices (id, customerId, customerName, date, status, sent, sentToProduction, paidAmount, totalSheets, data)
     VALUES (?,?,?,?,?,?,?,?,?,?)`,
    [id, customerId, customerName, date, 'paid', 0, 0, 0, 1, JSON.stringify({ items: [] })]
  );
}

async function main() {
  resetDb();
  const db = await getDb();

  await db.run('BEGIN');
  await db.run(`INSERT INTO customers (id, name) VALUES (1, 'مشتری الف')`, []);
  await db.run(`INSERT INTO customers (id, name) VALUES (2, 'مشتری ب')`, []);
  // مشتری ۱: ۳ فاکتور (یکتا باید یک ردیف بدهد)، مشتری ۲: ۱ فاکتور
  await seedInvoice(db, 1, 1, 'مشتری الف', new Date(2024, 0, 1).toISOString());
  await seedInvoice(db, 2, 1, 'مشتری الف', new Date(2024, 0, 5).toISOString());
  await seedInvoice(db, 3, 1, 'مشتری الف', new Date(2024, 0, 10).toISOString());
  await seedInvoice(db, 4, 2, 'مشتری ب', new Date(2024, 0, 2).toISOString());
  await db.run('COMMIT');

  const rows = await InvoiceRepo.getDistinctCustomers();

  // ۱) دقیقاً به تعداد customerId های یکتا (نه به تعداد فاکتورها) ردیف برمی‌گردد
  assert.equal(rows.length, 2);
  ok('getDistinctCustomers: 4 invoices across 2 real customerIds → exactly 2 rows (GROUP BY works)');

  // ۲) هر customerId موردنظر در نتیجه هست، با یک customerName معتبر (نه undefined/خالی)
  const byId = Object.fromEntries(rows.map(r => [String(r.customerId), r.customerName]));
  assert.equal(byId['1'], 'مشتری الف');
  ok('getDistinctCustomers: customerId 1 present with its customerName');
  assert.equal(byId['2'], 'مشتری ب');
  ok('getDistinctCustomers: customerId 2 present with its customerName');

  // ۳) دیتابیس کاملاً خالی → آرایه‌ی خالی، نه throw
  resetDb();
  const empty = await InvoiceRepo.getDistinctCustomers();
  assert.deepEqual(empty, []);
  ok('getDistinctCustomers: empty invoices table returns [] without throwing');

  console.log(`\n${passed} assertion(s) passed against a real in-memory SQLite database (node:sqlite).`);
}

main().catch(e => { console.error('TEST FAILED:', e); process.exit(1); });
