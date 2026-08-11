// تست InvoiceRepo.getCountsByCustomerIds — فاز ۲ نقشه‌راه SQLite / گروه ب / مورد ۳
// (renderCustomerViewCard در html8.html). هدف این متد: شمارش فاکتور به‌ازای هر
// مشتری در یک کوئری تجمیعی واحد به‌جای N کوئری جدا per-customer (N+1 query).
//
// اجرا: node --experimental-sqlite invoice-counts-by-customer-test.mjs

import assert from 'node:assert';
import { InvoiceRepo } from './_node_test_copies/InvoiceRepo.js';
import { getDb, resetDb } from './testConnection.node-only.mjs';

let passed = 0;
function ok(msg) { passed++; console.log(`  ✓ ${msg}`); }

async function seedInvoice(db, id, customerId, customerName) {
  await db.run(
    `INSERT INTO invoices (id, customerId, customerName, date, status, sent, sentToProduction, paidAmount, totalSheets, data)
     VALUES (?,?,?,?,?,?,?,?,?,?)`,
    [id, customerId, customerName, new Date(2024, 0, 1).toISOString(), 'paid', 0, 0, 0, 1, JSON.stringify({ items: [] })]
  );
}

async function main() {
  resetDb();
  const db = await getDb();

  await db.run('BEGIN');
  await db.run(`INSERT INTO customers (id, name) VALUES (1, 'مشتری الف')`, []);
  await db.run(`INSERT INTO customers (id, name) VALUES (2, 'مشتری ب')`, []);
  await db.run(`INSERT INTO customers (id, name) VALUES (3, 'مشتری ج (بدون فاکتور)')`, []);
  // مشتری ۱: ۵ فاکتور، مشتری ۲: ۲ فاکتور، مشتری ۳: صفر
  let id = 1;
  for (let i = 0; i < 5; i++) await seedInvoice(db, id++, 1, 'مشتری الف');
  for (let i = 0; i < 2; i++) await seedInvoice(db, id++, 2, 'مشتری ب');
  await db.run('COMMIT');

  // ۱) حالت پایه: چند مشتری با تعداد متفاوت + یک مشتری بدون فاکتور اصلاً کلید نمی‌گیرد
  const counts = await InvoiceRepo.getCountsByCustomerIds([1, 2, 3]);
  assert.equal(counts['1'], 5);
  ok('getCountsByCustomerIds: customer 1 → 5 (matches real row count)');
  assert.equal(counts['2'], 2);
  ok('getCountsByCustomerIds: customer 2 → 2');
  assert.equal(Object.prototype.hasOwnProperty.call(counts, '3'), false);
  ok('getCountsByCustomerIds: customer with zero invoices is simply absent from the map (no GROUP BY row), not present as 0');

  // ۲) فقط زیرمجموعه‌ی خواسته‌شده برمی‌گردد — مشتری ۱ در دیتابیس هست ولی در ورودی نیست
  const onlyTwo = await InvoiceRepo.getCountsByCustomerIds([2]);
  assert.equal(onlyTwo['2'], 2);
  ok('getCountsByCustomerIds: only requested ids are queried (WHERE customerId IN (...))');
  assert.equal(Object.prototype.hasOwnProperty.call(onlyTwo, '1'), false);
  ok('getCountsByCustomerIds: ids not in the requested list are excluded even though they exist in the table');

  // ۳) ورودی خالی → بدون کوئری، شیء خالی (نه throw)
  const empty = await InvoiceRepo.getCountsByCustomerIds([]);
  assert.deepEqual(empty, {});
  ok('getCountsByCustomerIds: empty input array returns {} without querying the database');

  // ۴) null/undefined در آرایه‌ی ورودی فیلتر می‌شوند، کرش نمی‌کند
  const withNulls = await InvoiceRepo.getCountsByCustomerIds([1, null, undefined, 2]);
  assert.equal(withNulls['1'], 5);
  assert.equal(withNulls['2'], 2);
  ok('getCountsByCustomerIds: null/undefined entries in the id list are filtered out safely');

  // ۵) بدون آرگومان (پیش‌فرض []) هم باید کار کند
  const noArg = await InvoiceRepo.getCountsByCustomerIds();
  assert.deepEqual(noArg, {});
  ok('getCountsByCustomerIds: called with no arguments defaults to {} rather than throwing');

  console.log(`\n${passed} assertion(s) passed against a real in-memory SQLite database (node:sqlite).`);
}

main().catch(e => { console.error('TEST FAILED:', e); process.exit(1); });
