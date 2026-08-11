// تست فاز ۴ اقدام ۱ (نسخه‌ی ۴.۱۹) — محاسبات آماری بدون فریز UI:
//   ۱) InvoiceRepo.getTotalsByRange / getStatsBuckets روی یک دیتابیس واقعی
//      node:sqlite (رفع باگ: نسخه‌ی قبلی getTotalsByRange جمع را از فیلدهای
//      ناموجود $.totalTurnover/$.totalProfit می‌گرفت و همیشه ۰ برمی‌گرداند).
//   ۲) fetchStatsData در html8.html — پل بین این متد و computeStatsDataFallback
//      (حلقه‌ی قدیمی JS روی آرایه‌ی حافظه)، مستقیم با regex استخراج شده،
//      همان روش resolve-invoice-by-id-test.mjs/customer-search-wire-test.mjs.
// اجرا: node --experimental-sqlite tests/stats-aggregation-test.mjs

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { InvoiceRepo } from './_node_test_copies/InvoiceRepo.js';
import { getDb } from './testConnection.node-only.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const html = fs.readFileSync(path.join(__dirname, '..', 'html8.html'), 'utf-8');

let pass = 0, fail = 0;
function assert(cond, msg) { if (cond) pass++; else { fail++; console.error('❌ FAIL:', msg); } }
function approx(a, b, eps = 1e-6) { return Math.abs(a - b) < eps; }

function extractBlock(src) {
  const startMarker = 'function calcInvoiceProfit(inv){';
  const endMarker = 'async function fetchStatsData(period){';
  const startIdx = src.indexOf(startMarker);
  if (startIdx === -1) { console.error('❌ FAIL: calcInvoiceProfit در html8.html پیدا نشد.'); process.exit(1); }
  const fetchStartIdx = src.indexOf(endMarker, startIdx);
  if (fetchStartIdx === -1) { console.error('❌ FAIL: fetchStatsData در html8.html پیدا نشد.'); process.exit(1); }
  // انتهای خودِ fetchStatsData: اولین "\n}\n" بعد از شروعش
  const closeIdx = src.indexOf('\n}\n', fetchStartIdx);
  if (closeIdx === -1) { console.error('❌ FAIL: انتهای fetchStatsData پیدا نشد.'); process.exit(1); }
  return src.slice(startIdx, closeIdx + 3);
}

function getFetchStatsData(invoicesArr, windowObj) {
  const block = extractBlock(html);
  // toFaDigits (تبدیل عدد به رقم فارسی، برای برچسب بازه‌های ساعتی period='day')
  // جای دیگری از html8.html تعریف شده، خارج از این بلوک استخراج‌شده — این تست
  // به‌جای بازتولید کل فایل، یک stub بی‌ضرر تزریق می‌کند (فقط روی برچسب اثر
  // دارد، نه روی محاسبات turnover/profit/count که هدف واقعی این تست است).
  const factory = new Function('invoices', 'window', 'toFaDigits', `${block}; return fetchStatsData;`);
  return factory(invoicesArr, windowObj, (n) => String(n));
}

async function run() {
  const db = await getDb();

  // ── داده‌ی seed: یک مشتری + چند فاکتور با items[] واقعی (lineTotal/itemProfit) ──
  await db.run(`INSERT INTO customers (id, name) VALUES (?, ?)`, [1, 'مشتری تست']);
  const mkInvoice = (id, date, items) => ({
    id, customerId: 1, customerName: 'مشتری تست', date, status: 'paid', sent: 0,
    sentToProduction: 0, totalSheets: 0, items
  });
  const invA = mkInvoice(101, '2026-03-01T08:00:00.000Z', [{ lineTotal: 1000, itemProfit: 100 }, { lineTotal: 500, itemProfit: 50 }]);
  const invB = mkInvoice(102, '2026-03-02T08:00:00.000Z', [{ lineTotal: 2000, itemProfit: 300 }]);
  const invOutOfRange = mkInvoice(103, '2026-04-15T08:00:00.000Z', [{ lineTotal: 9999, itemProfit: 9999 }]);
  await InvoiceRepo.save(invA);
  await InvoiceRepo.save(invB);
  await InvoiceRepo.save(invOutOfRange);

  // ── ۱) getTotalsByRange روی یک بازه که فقط invA/invB را در بر می‌گیرد ──
  {
    const totals = await InvoiceRepo.getTotalsByRange('2026-03-01T00:00:00.000Z', '2026-03-03T00:00:00.000Z');
    assert(totals.count === 2, `getTotalsByRange باید ۲ فاکتور را بشمارد، ${totals.count} برگرداند`);
    assert(approx(totals.turnover, 3500), `getTotalsByRange باید turnover=3500 برگرداند (رفع باگ فیلد ناموجود)، ${totals.turnover} برگرداند`);
    assert(approx(totals.profit, 450), `getTotalsByRange باید profit=450 برگرداند، ${totals.profit} برگرداند`);
  }

  // ── ۲) فاکتور خارج از بازه نباید حساب شود ──
  {
    const totals = await InvoiceRepo.getTotalsByRange('2026-03-01T00:00:00.000Z', '2026-03-03T00:00:00.000Z');
    assert(totals.turnover < 9999, 'فاکتور خارج از بازه (اردیبهشت) نباید در جمع بازه‌ی اسفند حساب شود');
  }

  // ── ۳) getStatsBuckets — چند بازه هم‌زمان ──
  {
    const buckets = [
      { start: '2026-03-01T00:00:00.000Z', end: '2026-03-02T00:00:00.000Z', label: 'روز۱' },
      { start: '2026-03-02T00:00:00.000Z', end: '2026-03-03T00:00:00.000Z', label: 'روز۲' },
      { start: '2026-03-03T00:00:00.000Z', end: '2026-03-04T00:00:00.000Z', label: 'روز۳ (خالی)' }
    ];
    const res = await InvoiceRepo.getStatsBuckets(buckets);
    assert(res.length === 3, 'getStatsBuckets باید به تعداد بازه‌ها ردیف برگرداند');
    assert(res[0].label === 'روز۱' && approx(res[0].turnover, 1500) && res[0].count === 1, `بازه‌ی روز۱ باید turnover=1500,count=1 باشد — ${JSON.stringify(res[0])}`);
    assert(res[1].label === 'روز۲' && approx(res[1].turnover, 2000) && res[1].count === 1, `بازه‌ی روز۲ باید turnover=2000,count=1 باشد — ${JSON.stringify(res[1])}`);
    assert(res[2].count === 0 && approx(res[2].turnover, 0) && approx(res[2].profit, 0), 'بازه‌ی بدون فاکتور باید همه‌چیز صفر برگرداند، نه خطا');
  }

  // ── ۴) getStatsBuckets با آرایه‌ی خالی ──
  {
    const res = await InvoiceRepo.getStatsBuckets([]);
    assert(Array.isArray(res) && res.length === 0, 'getStatsBuckets با ورودی خالی باید آرایه‌ی خالی برگرداند');
  }

  // ── ۵) fetchStatsData — وقتی InvoiceRepo.getStatsBuckets در دسترس است، باید از آن استفاده کند نه fallback ──
  {
    let calledWith = null;
    const fakeRepo = { getStatsBuckets: async (buckets) => { calledWith = buckets; return buckets.map(b => ({ label: b.label, turnover: 111, profit: 22, count: 3 })); } };
    const fetchStatsData = getFetchStatsData([], { __InvoiceRepo: fakeRepo });
    const res = await fetchStatsData('week');
    assert(Array.isArray(calledWith) && calledWith.length === 7, 'برای period=week باید ۷ بازه (۷ روز اخیر) به getStatsBuckets پاس داده شود');
    assert(calledWith.every(b => typeof b.start === 'string' && typeof b.end === 'string'), 'start/end باید رشته‌ی ISO باشند، نه شیء Date خام');
    assert(res.length === 7 && res[0].turnover === 111 && res[0].count === 3, 'fetchStatsData باید نتیجه‌ی getStatsBuckets را (نرمالایزشده) برگرداند');
  }

  // ── ۶) fetchStatsData — نبود window.__InvoiceRepo → باید بی‌صدا به computeStatsDataFallback برگردد ──
  // (period='month' -> ۵ بازه‌ی هفتگی؛ فاکتور همین لحظه قطعاً داخل آخرین بازه می‌افتد)
  {
    const invoices = [
      { id: 1, date: new Date().toISOString(), items: [{ lineTotal: 700, itemProfit: 70 }] }
    ];
    const fetchStatsData = getFetchStatsData(invoices, {});
    const res = await fetchStatsData('month');
    assert(Array.isArray(res) && res.length === 5, 'period=month باید ۵ بازه (fallback) برگرداند');
    const last = res[res.length - 1];
    assert(approx(last.turnover, 700) && approx(last.profit, 70) && last.count === 1, 'fallback باید از آرایه‌ی حافظه با calcInvoiceTurnover/Profit درست جمع بزند');
  }

  // ── ۷) fetchStatsData — خطای getStatsBuckets → باید بی‌صدا catch و fallback بزند ──
  {
    const invoices = [{ id: 1, date: new Date().toISOString(), items: [{ lineTotal: 50, itemProfit: 5 }] }];
    const fakeRepo = { getStatsBuckets: async () => { throw new Error('DB down'); } };
    const fetchStatsData = getFetchStatsData(invoices, { __InvoiceRepo: fakeRepo });
    let threw = false, res;
    try { res = await fetchStatsData('month'); } catch { threw = true; }
    assert(threw === false, 'خطای getStatsBuckets نباید کل زنجیره را بترکاند');
    assert(res.length === 5 && approx(res[res.length - 1].turnover, 50), 'روی خطا باید نتیجه‌ی fallback (آرایه‌ی حافظه) برگردد');
  }

  // ── ۸) fetchStatsData — نبود متد getStatsBuckets روی repo (نسخه‌ی ناقص/قدیمی) → نباید بترکد ──
  {
    const invoices = [{ id: 1, date: new Date().toISOString(), items: [{ lineTotal: 10, itemProfit: 1 }] }];
    const fakeRepo = {}; // بدون getStatsBuckets
    const fetchStatsData = getFetchStatsData(invoices, { __InvoiceRepo: fakeRepo });
    let threw = false, res;
    try { res = await fetchStatsData('month'); } catch { threw = true; }
    assert(threw === false, 'نبود متد getStatsBuckets نباید بترکاند');
    assert(res.length === 5 && approx(res[res.length - 1].turnover, 10), 'باید بی‌صدا به fallback برگردد');
  }

  // ── ۹) fetchStatsData — پاسخ با طول متفاوت از buckets (repo ناقص/نامعتبر) → باید به fallback برگردد ──
  {
    const invoices = [{ id: 1, date: new Date().toISOString(), items: [{ lineTotal: 10, itemProfit: 1 }] }];
    const fakeRepo = { getStatsBuckets: async () => ([{ label: 'x', turnover: 1, profit: 1, count: 1 }]) }; // طول اشتباه برای period=week (باید ۷ بازه باشد)
    const fetchStatsData = getFetchStatsData(invoices, { __InvoiceRepo: fakeRepo });
    const res = await fetchStatsData('week'); // week=۷ بازه، ولی fakeRepo فقط ۱ تا برمی‌گرداند → طول نامعتبر
    assert(res.length === 7, `طول نامعتبر پاسخ repo باید نادیده گرفته شود و fallback (۷ بازه‌ی هفته) جایگزین شود — ${res.length} برگشت`);
  }

  console.log(`\n${pass} پاس، ${fail} خطا.`);
  if (fail > 0) process.exit(1);
}

run();
