// تست InvoiceRepo.forEachBatch / CustomerRepo.forEachBatch — فاز ۴ نقشه‌راه
// SQLite (سند sqlite-migration-roadmap.md): «doBackup باید مستقیماً از SQLite
// (صفحه‌بندی‌شده) بخواند، نه IndexedDB». این دو متد، معادل SQLite همان
// idbForEachBatch قدیمی html8.html هستند — با keyset روی `id` (نه OFFSET
// عمیق، طبق همان درسی که در getPageByCursor آموخته شد) و دقیقاً همان قرارداد
// (batchSize, onBatch) → تا doBackup بتواند بدون تغییر buildBackupParts بین
// این متد و idbForEachBatch سوییچ کند.
//
// اجرا: node --experimental-sqlite repo-foreach-batch-test.mjs

import assert from 'node:assert/strict';
import { InvoiceRepo } from './_node_test_copies/InvoiceRepo.js';
import { CustomerRepo } from './_node_test_copies/CustomerRepo.js';
import { getDb, resetDb } from './testConnection.node-only.mjs';

let passed = 0;
function ok(label) { passed++; console.log('  ✓', label); }

async function main() {
  resetDb();

  console.log('== CustomerRepo.forEachBatch ==');
  // ۱) دیتابیس خالی → هیچ batch ای صدا زده نمی‌شود، بدون throw
  {
    let calls = 0;
    await CustomerRepo.forEachBatch(50, () => { calls++; });
    assert.equal(calls, 0);
    ok('forEachBatch: empty table → zero batches, no throw');
  }

  // ۲) ۲۳ مشتری، batchSize=10 → باید دقیقاً ۳ batch (۱۰+۱۰+۳) بدهد، بدون تکرار/گم‌شدن
  for (let i = 1; i <= 23; i++) {
    await CustomerRepo.save({ id: i, name: `مشتری ${i}` });
  }
  {
    const batchSizes = [];
    const seenIds = new Set();
    await CustomerRepo.forEachBatch(10, (batch) => {
      batchSizes.push(batch.length);
      for (const c of batch) seenIds.add(c.id);
    });
    assert.deepEqual(batchSizes, [10, 10, 3]);
    ok('forEachBatch: 23 rows / batchSize=10 → batches of [10,10,3]');
    assert.equal(seenIds.size, 23);
    ok('forEachBatch: exactly 23 unique ids seen (no duplicates, none skipped)');
    for (let i = 1; i <= 23; i++) assert.ok(seenIds.has(i));
    ok('forEachBatch: every id from 1..23 present');
  }

  // ۳) هر ردیف باید شکل rowToCustomer را داشته باشد (name واقعی، نه فقط id خام)
  {
    const names = [];
    await CustomerRepo.forEachBatch(100, (batch) => { for (const c of batch) names.push(c.name); });
    assert.ok(names.includes('مشتری 1') && names.includes('مشتری 23'));
    ok('forEachBatch: rows mapped through rowToCustomer (name field present)');
  }

  console.log('== InvoiceRepo.forEachBatch ==');
  resetDb();
  {
    let calls = 0;
    await InvoiceRepo.forEachBatch(50, () => { calls++; });
    assert.equal(calls, 0);
    ok('forEachBatch: empty invoices table → zero batches, no throw');
  }

  await CustomerRepo.save({ id: 1, name: 'مشتری الف' });
  for (let i = 1; i <= 15; i++) {
    await InvoiceRepo.save({ id: i, customerId: 1, customerName: 'مشتری الف', date: new Date(2026, 0, i).toISOString(), status: 'paid', sent: 1, items: [{ lineTotal: 1000 }] });
  }
  {
    const batchSizes = [];
    const seenIds = new Set();
    await InvoiceRepo.forEachBatch(4, (batch) => {
      batchSizes.push(batch.length);
      for (const inv of batch) seenIds.add(inv.id);
    });
    assert.deepEqual(batchSizes, [4, 4, 4, 3]);
    ok('forEachBatch: 15 rows / batchSize=4 → batches of [4,4,4,3]');
    assert.equal(seenIds.size, 15);
    ok('forEachBatch: exactly 15 unique invoice ids seen');
  }

  // ۴) هر ردیف باید شکل rowToInvoice (JSON ستون data پارس‌شده) را داشته باشد
  {
    let sample = null;
    await InvoiceRepo.forEachBatch(100, (batch) => { if (!sample) sample = batch[0]; });
    assert.equal(sample.customerName, 'مشتری الف');
    assert.ok(Array.isArray(sample.items));
    ok('forEachBatch: rows mapped through rowToInvoice (parsed data column, items[] present)');
  }

  console.log(`\n${passed} assertion(s) passed against a real in-memory SQLite database (node:sqlite).`);
}

main().catch(e => { console.error('TEST FAILED:', e); process.exit(1); });
