// تست InvoiceRepo.updateCustomerNameForInvoices — فاز ۲ نقشه‌راه SQLite / گروه ب / مورد ۴
// (saveCustomerEdit در html8.html). هدف این متد: وقتی نام یک مشتری تغییر می‌کند،
// نام روی همه‌ی فاکتورهای آن مشتری (و ردیف‌های denormalize‌شده‌ی invoice_items)
// با یک UPDATE گروهی واحد به‌روز شود — نه لوپ روی آرایه + save تکی به‌ازای هر فاکتور.
//
// اجرا: node --experimental-sqlite update-customer-name-for-invoices-test.mjs

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
  // یک ردیف denormalize‌شده‌ی معادل در invoice_items — دقیقاً مثل InvoiceRepoAdditions.save واقعی
  await db.run(
    `INSERT INTO invoice_items (invoiceId, date, customerId, customerName, cartonName, cartonLength, cartonWidth, cartonHeight, layer, cartonQty, lineTotal, itemProfit)
     VALUES (?,?,?,?,?,?,?,?,?,?,?,?)`,
    [id, new Date(2024, 0, 1).toISOString(), customerId, customerName, 'کارتن نمونه', 10, 10, 10, '۱', 5, 1000, 100]
  );
}

async function main() {
  resetDb();
  const db = await getDb();

  await db.run('BEGIN');
  await db.run(`INSERT INTO customers (id, name) VALUES (1, 'نام قدیمی')`, []);
  await db.run(`INSERT INTO customers (id, name) VALUES (2, 'مشتری دیگر')`, []);
  let id = 1;
  for (let i = 0; i < 4; i++) await seedInvoice(db, id++, 1, 'نام قدیمی');
  for (let i = 0; i < 3; i++) await seedInvoice(db, id++, 2, 'مشتری دیگر');
  await db.run('COMMIT');

  // ۱) همه‌ی فاکتورهای مشتری ۱ باید با یک فراخوانی به نام جدید تغییر کنند
  await InvoiceRepo.updateCustomerNameForInvoices(1, 'نام جدید');
  const res1 = await db.query(`SELECT customerName FROM invoices WHERE customerId = 1`, []);
  assert.equal(res1.values.length, 4);
  assert.ok(res1.values.every(r => r.customerName === 'نام جدید'));
  ok('updateCustomerNameForInvoices: all 4 invoices of customer 1 updated to the new name in one call');

  // ۲) invoice_items denormalize‌شده هم باید هم‌زمان به‌روز شود (نه فقط invoices)
  const items1 = await db.query(`SELECT customerName FROM invoice_items WHERE customerId = 1`, []);
  assert.equal(items1.values.length, 4);
  assert.ok(items1.values.every(r => r.customerName === 'نام جدید'));
  ok('updateCustomerNameForInvoices: denormalized invoice_items.customerName also updated (stats tab would otherwise show stale names)');

  // ۳) فاکتورهای مشتری دیگر دست‌نخورده می‌مانند
  const res2 = await db.query(`SELECT customerName FROM invoices WHERE customerId = 2`, []);
  assert.ok(res2.values.every(r => r.customerName === 'مشتری دیگر'));
  ok('updateCustomerNameForInvoices: invoices belonging to a different customer are untouched');

  // ۴) مشتری بدون فاکتور (id ناموجود در invoices) → بدون خطا، صفر ردیف تغییر می‌کند
  const resultForMissing = await InvoiceRepo.updateCustomerNameForInvoices(999, 'هرچی');
  assert.equal(resultForMissing, true);
  ok('updateCustomerNameForInvoices: customerId with zero invoices does not throw, still resolves');

  // ۵) customerId خالی/null → بدون کوئری، false برمی‌گرداند
  const resultNull = await InvoiceRepo.updateCustomerNameForInvoices(null, 'هرچی');
  assert.equal(resultNull, false);
  ok('updateCustomerNameForInvoices: null customerId short-circuits to false without querying');

  console.log(`\n${passed} assertion(s) passed against a real in-memory SQLite database (node:sqlite).`);
}

main().catch(e => { console.error('TEST FAILED:', e); process.exit(1); });
