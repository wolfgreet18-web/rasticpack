// اجرای مرحله‌ی «کوئری» تست فشار فاز ۷ روی فایل دیتابیسی که با
// seed-chunk.node-only.mjs (چند اجرای جدا، به‌خاطر سقف زمانی هر دستور در این
// sandbox) از قبل پر شده — دقیقاً همان ۵ سناریوی رسمی فاز ۷ + معیار پذیرش
// <۱۰۰ms. نسخه‌ی اول این فایل سعی کرد ماژول testConnection.node-only.mjs را
// runtime monkey-patch کند تا از یک فایل واقعی به‌جای :memory: بخواند — این
// در ESM واقعی کار نمی‌کند (module namespace object قابل تغییر نیست).
// نسخه‌ی درست: از env var `STRESS_DB_PATH` که خودِ testConnection.node-only.mjs
// می‌خواند استفاده می‌کند (نگاه کن به همان فایل).
//
// اجرا: STRESS_DB_PATH=/path/to/stress.db node --experimental-sqlite stress-test-phase7-query.node-only.mjs
import { InvoiceRepo } from './_node_test_copies/InvoiceRepo.js';
import { CustomerRepo } from './_node_test_copies/CustomerRepo.js';

const N_INVOICES = Number(process.env.STRESS_N_INVOICES || 5000000);
const N_CUSTOMERS = Number(process.env.STRESS_N_CUSTOMERS || 250000);
const ACCEPTANCE_MS = 100; // معیار پذیرش رسمی فاز ۷ (بخش ۴.۷)

async function main() {
  if (!process.env.STRESS_DB_PATH) {
    throw new Error('STRESS_DB_PATH env var required — point it at a file seeded by seed-chunk.node-only.mjs');
  }

  const results = {};

  let t = Date.now();
  const firstPage = await InvoiceRepo.getPage({ limit: 50, offset: 0 });
  results.openInvoicesTab = Date.now() - t;
  console.log(`[1] Open invoices tab (page 0): ${results.openInvoicesTab}ms — ${firstPage.items.length} rows, total=${firstPage.total}${firstPage.totalIsExact ? '' : ' (capped)'}`);

  t = Date.now();
  const lastPage = await InvoiceRepo.getPage({ limit: 50, offset: N_INVOICES - 50 });
  results.scrollToEnd = Date.now() - t;
  console.log(`[2] Scroll to end (offset=${N_INVOICES - 50}): ${results.scrollToEnd}ms — ${lastPage.items.length} rows`);

  t = Date.now();
  const custSearch = await CustomerRepo.searchByName('مشتری تستی 5', { prefix: true, limit: 50 });
  results.customerSearch = Date.now() - t;
  console.log(`[3] Customer name search on ${N_CUSTOMERS}: ${results.customerSearch}ms — ${custSearch.items.length} matches, totalIsExact=${custSearch.totalIsExact}`);

  const monthStart = new Date(2023, 5, 1).toISOString();
  const monthEnd = new Date(2023, 6, 1).toISOString();
  const targetCustomer = Math.floor(N_CUSTOMERS * 0.4);
  t = Date.now();
  const filtered = await InvoiceRepo.getPage({
    monthStart, monthEnd, customerId: targetCustomer, status: 'paid', limit: 50, offset: 0
  });
  results.combinedFilter = Date.now() - t;
  console.log(`[4] Combined filter (month+status+customer): ${results.combinedFilter}ms — matched ${filtered.total}${filtered.totalIsExact ? '' : ' (capped)'}`);

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
  console.log('');
  console.log('NOTE: node:sqlite in-process, dev-container CPU, file-backed DB — not');
  console.log('@capacitor-community/sqlite IPC on a real Android/iOS device, and not the');
  console.log('browser Performance Profiler pass (action 4 of Phase 7).');
  console.log('');
  console.log(allPass ? 'OVERALL: PASS (sandbox proxy)' : 'OVERALL: FAIL — see roadmap v4.25 for root-cause notes');
  if (!allPass) process.exit(1);
}

main().catch(e => { console.error('QUERY STAGE FAILED:', e); process.exit(1); });
