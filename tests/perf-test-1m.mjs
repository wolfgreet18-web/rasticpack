// تست کارایی روی ۱ میلیون ردیف — این دقیقاً همان معیار پذیرش رسمی فاز ۱ است
// («یک SELECT با فیلتر ماه+مشتری+وضعیت روی ۱ میلیون ردیف تست، زیر ۵۰ms»)،
// نه معیار غیررسمی ۲۰۰k که در perf-test.mjs بود. مثل perf-test.mjs، این هم
// روی node:sqlite (نه پلاگین واقعی @capacitor-community/sqlite روی دستگاه)
// اجرا می‌شود — نتیجه شواهد جهت‌گیری قوی‌تر است، نه جایگزین تست روی موبایل واقعی.
//
// اجرا: node --experimental-sqlite perf-test-1m.mjs

import { InvoiceRepo } from './_node_test_copies/InvoiceRepo.js';
import { CustomerRepo } from './_node_test_copies/CustomerRepo.js';
import { getDb, resetDb } from './testConnection.node-only.mjs';

const N_CUSTOMERS = 15000;
const N_INVOICES = 1000000;
const ACCEPTANCE_MS = 50; // معیار پذیرش فاز ۱: فیلتر ترکیبی زیر ۵۰ms

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
      [i, custId, `مشتری تستی ${custId}`, date, statuses[i % 3], i % 2, 0, (i % 3 === 1) ? 50000 : 0, (i % 20) + 1, JSON.stringify({ items: [{ lineTotal: 1000 }] })]
    );
  }
  await db.run('COMMIT');
  console.log(`  done in ${Date.now() - t}ms`);

  // معیار پذیرش فاز ۱: فیلتر ترکیبی (ماه + مشتری + وضعیت) زیر ۵۰ms
  const monthStart = new Date(2024, 3, 1).toISOString();
  const monthEnd = new Date(2024, 4, 1).toISOString();
  const targetCustomer = 1500;

  t = Date.now();
  const page = await InvoiceRepo.getPage({
    monthStart, monthEnd, customerId: targetCustomer, status: 'paid', limit: 50, offset: 0
  });
  const filterMs = Date.now() - t;
  console.log(`Combined filter (month+customer+status) on ${N_INVOICES} invoices: ${filterMs}ms — matched ${page.total} rows`);

  t = Date.now();
  const counts = await InvoiceRepo.getStatusCounts({ monthStart, monthEnd });
  const countsMs = Date.now() - t;
  console.log(`getStatusCounts over one month: ${countsMs}ms — ${JSON.stringify(counts)}`);

  t = Date.now();
  const custSearch = await CustomerRepo.searchByName('مشتری تستی 15', { prefix: true });
  const searchMs = Date.now() - t;
  console.log(`Customer prefix search on ${N_CUSTOMERS} customers: ${searchMs}ms — ${custSearch.items.length} matches`);

  console.log('');
  console.log(`Acceptance check (< ${ACCEPTANCE_MS}ms per phase-1 target):`);
  console.log(`  combined filter: ${filterMs}ms -> ${filterMs < ACCEPTANCE_MS ? 'PASS' : 'FAIL'}`);
  console.log(`  status counts:   ${countsMs}ms -> ${countsMs < ACCEPTANCE_MS ? 'PASS' : 'FAIL'}`);
  console.log(`  name search:     ${searchMs}ms -> ${searchMs < ACCEPTANCE_MS ? 'PASS' : 'FAIL'}`);
  console.log('');
  console.log('NOTE: this measures node:sqlite (in-process, no IPC) on the dev container\'s CPU,');
  console.log('not the @capacitor-community/sqlite plugin on a real Android/iOS device. It confirms');
  console.log('the SQL/index design is sound, not on-device performance (see roadmap risk table).');

  if (filterMs >= ACCEPTANCE_MS) process.exit(1);
}

main().catch(e => { console.error('PERF TEST FAILED:', e); process.exit(1); });
