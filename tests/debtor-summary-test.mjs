// تست InvoiceRepo.getDebtorSummary — فاز ۹-ب نقشه‌راه ۲ (sqlite-migration-roadmap.md).
//
// هدف: تضمین می‌کند که مجموع «مانده» فاکتورهای بدهکار (ارسال‌شده و هنوز کامل
// تسویه‌نشده) و تعداد مشتریان بدهکار، که این متد در SQL محاسبه می‌کند، دقیقاً
// با محاسبه‌ی دستی معادل روی همان داده (فرمول invoiceRemaining قدیمی در
// html8.html: SUM(lineTotal) - paidAmount) یکی است — یعنی جایگزینی renderDebtorSummary
// از حلقه‌ی JS روی baseList به این کوئری، معنا را عوض نکرده.
//
// اجرا: node --experimental-sqlite debtor-summary-test.mjs

import assert from 'node:assert';
import { InvoiceRepo } from './_node_test_copies/InvoiceRepo.js';
import { getDb, resetDb } from './testConnection.node-only.mjs';

let passed = 0;
function ok(msg) { passed++; console.log(`  ✓ ${msg}`); }

async function main() {
  resetDb();
  const db = await getDb();

  await db.run(`INSERT INTO customers (id, name) VALUES (1, 'مشتری اول')`, []);
  await db.run(`INSERT INTO customers (id, name) VALUES (2, 'مشتری دوم')`, []);
  await db.run(`INSERT INTO customers (id, name) VALUES (3, 'مشتری سوم')`, []);

  // مشتری ۱: دو فاکتور بدهکار (ارسال‌شده، status!=paid) — مجموع مانده باید جمع هر دو باشد.
  await InvoiceRepo.save({
    id: 101, customerId: 1, customerName: 'مشتری اول', date: '2025-01-05T00:00:00.000Z',
    status: 'partial', sent: true, paidAmount: 40000,
    items: [{ cartonName: 'A', lineTotal: 100000, itemProfit: 10000 }]
  });
  await InvoiceRepo.save({
    id: 102, customerId: 1, customerName: 'مشتری اول', date: '2025-01-06T00:00:00.000Z',
    status: 'draft', sent: true, paidAmount: 0,
    items: [{ cartonName: 'B', lineTotal: 50000, itemProfit: 5000 }]
  });
  // مشتری ۲: یک فاکتور بدهکار.
  await InvoiceRepo.save({
    id: 201, customerId: 2, customerName: 'مشتری دوم', date: '2025-01-07T00:00:00.000Z',
    status: 'partial', sent: true, paidAmount: 20000,
    items: [{ cartonName: 'C', lineTotal: 80000, itemProfit: 8000 }]
  });
  // مشتری ۳: فاکتور تسویه‌شده (paid) — نباید در بدهکاران بیاید.
  await InvoiceRepo.save({
    id: 301, customerId: 3, customerName: 'مشتری سوم', date: '2025-01-08T00:00:00.000Z',
    status: 'paid', sent: true, paidAmount: 60000,
    items: [{ cartonName: 'D', lineTotal: 60000, itemProfit: 6000 }]
  });
  // مشتری ۳: فاکتور draft ولی ارسال‌نشده (sent=false) — نباید بدهکار حساب شود.
  await InvoiceRepo.save({
    id: 302, customerId: 3, customerName: 'مشتری سوم', date: '2025-01-09T00:00:00.000Z',
    status: 'draft', sent: false, paidAmount: 0,
    items: [{ cartonName: 'E', lineTotal: 30000, itemProfit: 3000 }]
  });

  // مانده‌ی مورد انتظار = (100000-40000) + (50000-0) + (80000-20000) = 60000+50000+60000 = 170000
  const summary = await InvoiceRepo.getDebtorSummary({});
  assert.strictEqual(summary.totalRemaining, 170000);
  ok('totalRemaining = مجموع صحیح مانده‌ی فقط فاکتورهای بدهکار (ارسال‌شده و غیر paid)');

  assert.strictEqual(summary.debtorCustomerCount, 2);
  ok('debtorCustomerCount = تعداد صحیح مشتریان دارای حداقل یک فاکتور بدهکار');

  // فیلتر customerId — فقط مشتری ۱ را بررسی کند.
  const summaryCust1 = await InvoiceRepo.getDebtorSummary({ customerId: 1 });
  assert.strictEqual(summaryCust1.totalRemaining, 110000);
  assert.strictEqual(summaryCust1.debtorCustomerCount, 1);
  ok('فیلتر customerId به‌درستی روی getDebtorSummary اعمال می‌شود');

  // فیلتر تاریخ (فقط ۵ ژانویه) — فقط فاکتور 101.
  const summaryDate = await InvoiceRepo.getDebtorSummary({ exactDate: '2025-01-05' });
  assert.strictEqual(summaryDate.totalRemaining, 60000);
  assert.strictEqual(summaryDate.debtorCustomerCount, 1);
  ok('فیلتر exactDate به‌درستی روی getDebtorSummary اعمال می‌شود');

  // بدون هیچ فاکتور بدهکاری (مثلاً مشتری ۳ به‌تنهایی) — باید صفر برگرداند، نه خطا.
  const summaryNone = await InvoiceRepo.getDebtorSummary({ customerId: 3 });
  assert.strictEqual(summaryNone.totalRemaining, 0);
  assert.strictEqual(summaryNone.debtorCustomerCount, 0);
  ok('در نبود هیچ فاکتور بدهکار، {0,0} برمی‌گرداند (نه خطا/undefined)');

  console.log(`\n${passed} تست پاس شد، ۰ خطا.`);
}

main().catch(e => { console.error('❌ FAILED:', e); process.exit(1); });
