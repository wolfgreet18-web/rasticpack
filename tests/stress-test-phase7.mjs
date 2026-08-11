// فاز ۷ — تست فشار (اقدام ۱+۲+۳ نقشه‌راه، بخش ۴.۷):
//   ۱) seed: ۱ میلیون مشتری + ۵ میلیون فاکتور فیک (batch insert)
//   ۲) ۵ سناریوی رسمی نقشه: باز کردن تب فاکتورها / اسکرول تا انتها /
//      جستجوی نام مشتری / فیلتر ترکیبی ماه+وضعیت+مشتری / باز کردن تب آمار
//   ۳) آستانه‌ی پذیرش رسمی: هر عملیات زیر ۱۰۰ms
//
// مثل perf-test-1m.mjs/ram-usage-test.mjs، این هم روی node:sqlite (نه پلاگین
// واقعی @capacitor-community/sqlite روی دستگاه) اجرا می‌شود — پروکسی معتبرِ
// از قبل پذیرفته‌شده در این پروژه، نه جایگزین Performance Profiler مرورگر
// (اقدام ۴ فاز ۷) که در این sandbox (بدون مرورگر/دستگاه) اصلاً ممکن نیست.
//
// اجرا: node --experimental-sqlite --expose-gc stress-test-phase7.mjs

import { InvoiceRepo } from './_node_test_copies/InvoiceRepo.js';
import { CustomerRepo } from './_node_test_copies/CustomerRepo.js';
import { getDb, resetDb } from './testConnection.node-only.mjs';

const N_CUSTOMERS = 1_000_000;
const N_INVOICES = 5_000_000;
const ACCEPTANCE_MS = 100; // معیار پذیرش رسمی فاز ۷
const COMMIT_EVERY = 20000; // تراکنش‌های خیلی بزرگ حافظه/لاگ WAL را باد می‌کنند؛ batch شد

async function seedCustomers(db) {
  console.log(`Seeding ${N_CUSTOMERS} customers...`);
  const t0 = Date.now();
  await db.run('BEGIN');
  for (let i = 1; i <= N_CUSTOMERS; i++) {
    await db.run(`INSERT INTO customers (id, name, company, phone) VALUES (?, ?, ?, ?)`,
      [i, `مشتری تستی ${i}`, i % 7 === 0 ? `شرکت ${i}` : '', '0912' + String(1000000 + (i % 8999999))]);
    if (i % COMMIT_EVERY === 0) {
      await db.run('COMMIT');
      await db.run('BEGIN');
      if (i % 200000 === 0) console.log(`  ${i}/${N_CUSTOMERS} (${Date.now() - t0}ms elapsed)`);
    }
  }
  await db.run('COMMIT');
  console.log(`  done in ${Date.now() - t0}ms`);
}

async function seedInvoices(db) {
  console.log(`Seeding ${N_INVOICES} invoices...`);
  const t0 = Date.now();
  const statuses = ['draft', 'partial', 'paid'];
  await db.run('BEGIN');
  for (let i = 1; i <= N_INVOICES; i++) {
    const custId = (i % N_CUSTOMERS) + 1;
    const date = new Date(2022, 0, 1 + (i % 1460)).toISOString(); // ۴ سال بازه، مثل داده‌ی واقعی چندساله
    await db.run(
      `INSERT INTO invoices (id, customerId, customerName, date, status, sent, sentToProduction, paidAmount, totalSheets, data)
       VALUES (?,?,?,?,?,?,?,?,?,?)`,
      [i, custId, `مشتری تستی ${custId}`, date, statuses[i % 3], i % 2, 0, (i % 3 === 1) ? 50000 : 0, (i % 20) + 1,
       JSON.stringify({ items: [{ lineTotal: 1000 + (i % 500), itemProfit: 100 + (i % 50), cartonQty: (i % 30) + 1, cartonName: `کارتن${i % 12}`, cartonLength: 30, cartonWidth: 20, cartonHeight: 15, layer: 2 }] })]
    );
    if (i % COMMIT_EVERY === 0) {
      await db.run('COMMIT');
      await db.run('BEGIN');
      if (i % 500000 === 0) console.log(`  ${i}/${N_INVOICES} (${Date.now() - t0}ms elapsed)`);
    }
  }
  await db.run('COMMIT');
  console.log(`  done in ${Date.now() - t0}ms`);
}

async function main() {
  resetDb();
  const db = await getDb();

  await seedCustomers(db);
  await seedInvoices(db);

  const results = {};

  // سناریو ۱: باز کردن تب فاکتورها (صفحه‌ی اول، بدون فیلتر — دقیقاً همان
  // کوئری‌ای که fetchInvoicesPage با offset=0 در html8.html می‌زند)
  let t = Date.now();
  const firstPage = await InvoiceRepo.getPage({ limit: 50, offset: 0 });
  results.openInvoicesTab = Date.now() - t;
  console.log(`[1] Open invoices tab (page 0): ${results.openInvoicesTab}ms — ${firstPage.items.length} rows, total=${firstPage.total}`);

  // سناریو ۲: اسکرول تا انتهای لیست (بزرگ‌ترین offset ممکن روی ۵M ردیف)
  t = Date.now();
  const lastPage = await InvoiceRepo.getPage({ limit: 50, offset: N_INVOICES - 50 });
  results.scrollToEnd = Date.now() - t;
  console.log(`[2] Scroll to end (offset=${N_INVOICES - 50}): ${results.scrollToEnd}ms — ${lastPage.items.length} rows`);

  // سناریو ۳: جستجوی نام مشتری (روی ۱M مشتری)
  t = Date.now();
  const custSearch = await CustomerRepo.searchByName('مشتری تستی 5', { prefix: true, limit: 50 });
  results.customerSearch = Date.now() - t;
  console.log(`[3] Customer name search on ${N_CUSTOMERS}: ${results.customerSearch}ms — ${custSearch.items.length} matches, totalIsExact=${custSearch.totalIsExact}`);

  // سناریو ۴: فیلتر ترکیبی ماه + وضعیت + مشتری
  const monthStart = new Date(2023, 5, 1).toISOString();
  const monthEnd = new Date(2023, 6, 1).toISOString();
  const targetCustomer = 400000;
  t = Date.now();
  const filtered = await InvoiceRepo.getPage({
    monthStart, monthEnd, customerId: targetCustomer, status: 'paid', limit: 50, offset: 0
  });
  results.combinedFilter = Date.now() - t;
  console.log(`[4] Combined filter (month+status+customer): ${results.combinedFilter}ms — matched ${filtered.total}`);

  // سناریو ۵: باز کردن تب آمار (بازه‌ی یک ماهه، bucket + leaders — دقیقاً
  // دو کوئری واقعی‌ای که fetchStatsData/fetchStatsLeaders در html8.html می‌زنند)
  t = Date.now();
  const buckets = await InvoiceRepo.getStatsBuckets([{ start: monthStart, end: monthEnd, label: 'تیر ۱۴۰۲' }]);
  const leaders = await InvoiceRepo.getStatsLeaders(monthStart, monthEnd);
  results.statsTab = Date.now() - t;
  console.log(`[5] Open stats tab (buckets+leaders): ${results.statsTab}ms — turnover=${buckets[0]?.turnover}, topBuyer=${leaders.topBuyer?.name}`);

  console.log('');
  console.log(`Acceptance check (< ${ACCEPTANCE_MS}ms per Phase-7 target, section 4.7):`);
  let allPass = true;
  for (const [name, ms] of Object.entries(results)) {
    const pass = ms < ACCEPTANCE_MS;
    if (!pass) allPass = false;
    console.log(`  ${name}: ${ms}ms -> ${pass ? 'PASS' : 'FAIL'}`);
  }

  if (global.gc) global.gc();
  const mem = process.memoryUsage();
  console.log('');
  console.log(`heapUsed after full run: ${(mem.heapUsed / 1024 / 1024).toFixed(1)}MB (proxy for RAM growth, see ram-usage-test.mjs precedent)`);

  console.log('');
  console.log('NOTE: node:sqlite in-process, dev-container CPU — not @capacitor-community/sqlite');
  console.log('IPC on a real Android/iOS device, and not the browser Performance Profiler pass');
  console.log('(action 4 of Phase 7). This is a scale/index-soundness signal, not the final field test.');

  console.log('');
  console.log(allPass ? 'OVERALL: PASS (sandbox proxy)' : 'OVERALL: FAIL');
  if (!allPass) process.exit(1);
}

main().catch(e => { console.error('STRESS TEST FAILED:', e); process.exit(1); });
