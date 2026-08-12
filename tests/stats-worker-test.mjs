// تست فاز ۴ اقدام ۲ (نسخه‌ی ۴.۲۱) — Web Worker برای مسیر fallback آمار:
//   ۱) خودِ منطق داخل STATS_WORKER_SRC (رشته‌ی متنی که با Blob به یک Worker
//      واقعی تبدیل می‌شود) — با شبیه‌سازی یک محیط self.onmessage/postMessage
//      (بدون نیاز به Worker واقعی مرورگر، چون Node آن را ندارد)، مستقیم از
//      خودِ html8.html استخراج و اجرا می‌شود. نتیجه باید دقیقاً با
//      همان منطق حلقه‌ی aggregation (که قبل از فاز ۸ در computeStatsDataFallback/
//      computeStatsLeadersFallback هم‌زمان هم تکرار می‌شد، پیش از حذف آن‌ها)
//      باشد — تست صحت منطق aggregation خودِ Worker.
//   ۲) getStatsWorker/callStatsWorker/fetchStatsData/fetchStatsLeaders —
//      تأیید این‌که وقتی Worker سراسری (global) در دسترس نیست (مثل این
//      محیط Node)، از فاز ۸ به بعد دیگر fallback حافظه‌ای وجود ندارد و این
//      دو تابع reject می‌کنند؛ و وقتی یک Worker جعلی (fake) تزریق می‌شود، از
//      همان مسیر Worker جواب درست می‌گیرند.
// اجرا: node tests/stats-worker-test.mjs   (به SQLite نیاز ندارد)

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const html = fs.readFileSync(path.join(__dirname, '..', 'html8.html'), 'utf-8');

let pass = 0, fail = 0;
function assert(cond, msg) { if (cond) pass++; else { fail++; console.error('❌ FAIL:', msg); } }
function approx(a, b, eps = 1e-6) { return Math.abs(a - b) < eps; }

function extractBlock(endMarker) {
  const startMarker = 'function calcInvoiceProfit(inv){';
  const startIdx = html.indexOf(startMarker);
  if (startIdx === -1) { console.error('❌ FAIL: calcInvoiceProfit در html8.html پیدا نشد.'); process.exit(1); }
  const fnStartIdx = html.indexOf(endMarker, startIdx);
  if (fnStartIdx === -1) { console.error(`❌ FAIL: ${endMarker} در html8.html پیدا نشد.`); process.exit(1); }
  const closeIdx = html.indexOf('\n}\n', fnStartIdx);
  if (closeIdx === -1) { console.error('❌ FAIL: انتهای بلوک پیدا نشد.'); process.exit(1); }
  return html.slice(startIdx, closeIdx + 3);
}

// ── استخراج خودِ STATS_WORKER_SRC (رشته‌ی متنی که به Blob تبدیل می‌شود) ──
function extractWorkerSrc() {
  const startMarker = 'const STATS_WORKER_SRC=`';
  const startIdx = html.indexOf(startMarker);
  if (startIdx === -1) { console.error('❌ FAIL: STATS_WORKER_SRC در html8.html پیدا نشد.'); process.exit(1); }
  const contentStart = startIdx + startMarker.length;
  const endIdx = html.indexOf('`;', contentStart);
  if (endIdx === -1) { console.error('❌ FAIL: انتهای STATS_WORKER_SRC پیدا نشد.'); process.exit(1); }
  return html.slice(contentStart, endIdx);
}

// اجرای واقعی متن Worker در یک محیط self جعلی، دقیقاً همان چیزی که مرورگر
// با new Worker(blobUrl) اجرا می‌کند — فقط بدون thread جدا (کافی است چون
// هدف این بخش تست *صحت منطق aggregation*، نه خودِ زیرساخت threading مرورگر).
function runWorkerMessage(workerSrc, message) {
  let captured = null;
  const fakeSelf = { onmessage: null, postMessage: (msg) => { captured = msg; } };
  const factory = new Function('self', `${workerSrc}\nreturn self;`);
  const initializedSelf = factory(fakeSelf);
  initializedSelf.onmessage({ data: message });
  return captured;
}

function getFetchFns(invoicesArr, windowObj) {
  const dataBlock = extractBlock('async function fetchStatsData(period){');
  const leadersBlock = extractBlock('async function fetchStatsLeaders(period){');
  const layerLabel = l => l === '5' ? 'پنج‌لایه' : 'سه‌لایه';
  // [فاز ۱۱ نقشه‌راه ۲] computeStatsDataViaWorker/computeStatsLeadersViaWorker
  // دیگر آرایه‌ی سراسری invoices را نمی‌خوانند — fetchAllInvoicesForBackgroundLoad
  // (بیرون از این بلوک استخراج‌شده) را صدا می‌زنند؛ همان invoicesArr قبلی از
  // طریق یک stub تزریق می‌شود تا مسیر fallback همچنان روی همان دیتاست تست شود.
  const fetchAllInvoicesForBackgroundLoad = async () => invoicesArr;
  const dataFactory = new Function('invoices', 'window', 'toFaDigits', 'fetchAllInvoicesForBackgroundLoad', `${dataBlock}; return fetchStatsData;`);
  const leadersFactory = new Function('invoices', 'window', 'toFaDigits', 'layerLabel', 'fetchAllInvoicesForBackgroundLoad', `${leadersBlock}; return fetchStatsLeaders;`);
  return {
    fetchStatsData: dataFactory(invoicesArr, windowObj, (n) => String(n), fetchAllInvoicesForBackgroundLoad),
    fetchStatsLeaders: leadersFactory(invoicesArr, windowObj, (n) => String(n), layerLabel, fetchAllInvoicesForBackgroundLoad)
  };
}

// یک Worker جعلی که همان الگوی id/kind/postMessage واقعی را با
// setTimeout(…,0) شبیه‌سازی می‌کند — برای تست مسیر callStatsWorker/
// getStatsWorker بدون نیاز به Worker واقعی مرورگر.
function makeFakeWorkerGlobal(workerSrc) {
  class FakeWorker {
    constructor() {
      this.onmessage = null;
      this.onerror = null;
    }
    postMessage(msg) {
      setTimeout(() => {
        const result = runWorkerMessage(workerSrc, msg);
        if (this.onmessage) this.onmessage({ data: result });
      }, 0);
    }
  }
  return FakeWorker;
}

async function run() {
  const workerSrc = extractWorkerSrc();

  // ── ۱) statsData — parity با computeStatsDataFallback روی همان ورودی ──
  {
    const buckets = [
      { start: '2026-03-01T00:00:00.000Z', end: '2026-03-02T00:00:00.000Z', label: 'روز۱' },
      { start: '2026-03-02T00:00:00.000Z', end: '2026-03-03T00:00:00.000Z', label: 'روز۲' },
      { start: '2026-03-03T00:00:00.000Z', end: '2026-03-04T00:00:00.000Z', label: 'روز۳ (خالی)' }
    ];
    const invoices = [
      { id: 1, date: '2026-03-01T08:00:00.000Z', items: [{ lineTotal: 1000, itemProfit: 100 }, { lineTotal: 500, itemProfit: 50 }] },
      { id: 2, date: '2026-03-02T08:00:00.000Z', items: [{ lineTotal: 2000, itemProfit: 300 }] },
      { id: 3, date: '2026-04-15T08:00:00.000Z', items: [{ lineTotal: 9999, itemProfit: 9999 }] } // خارج از هر سه بازه
    ];
    const msg = runWorkerMessage(workerSrc, { id: 1, kind: 'statsData', invoices, buckets });
    assert(msg && msg.ok === true, `پیام statsData باید ok:true برگرداند — ${JSON.stringify(msg)}`);
    const res = msg.result;
    assert(res.length === 3, 'باید به تعداد بازه‌ها نتیجه برگرداند');
    assert(res[0].label === 'روز۱' && approx(res[0].turnover, 1500) && approx(res[0].profit, 150) && res[0].count === 1,
      `روز۱ باید turnover=1500,profit=150,count=1 باشد — ${JSON.stringify(res[0])}`);
    assert(res[1].label === 'روز۲' && approx(res[1].turnover, 2000) && res[1].count === 1, `روز۲ نادرست — ${JSON.stringify(res[1])}`);
    assert(res[2].count === 0 && approx(res[2].turnover, 0), 'بازه‌ی بدون فاکتور باید صفر باشد، نه خطا');
    assert(res[2].turnover < 9999, 'فاکتور خارج از بازه نباید در هیچ bucket ای حساب شود');
  }

  // ── ۲) statsData — آرایه‌ی خالی invoices نباید بترکد ──
  {
    const msg = runWorkerMessage(workerSrc, { id: 2, kind: 'statsData', invoices: [], buckets: [{ start: '2026-01-01T00:00:00.000Z', end: '2026-01-02T00:00:00.000Z', label: 'خالی' }] });
    assert(msg.ok === true && msg.result.length === 1 && msg.result[0].count === 0, 'invoices خالی باید نتیجه‌ی صفر بدهد، نه خطا');
  }

  // ── ۳) statsLeaders — همان سناریوی stats-leaders-test.mjs (کارتن با qty بیشتر می‌بازد به گردش‌مالی/سود) ──
  {
    const invoices = [
      { id: 101, customerId: 1, customerName: 'علی', date: '2026-03-01T08:00:00.000Z', items: [{ cartonName: 'کارتن الف', cartonLength: 30, cartonWidth: 20, cartonHeight: 15, layer: '3', cartonQty: 100, lineTotal: 1000, itemProfit: 100 }] },
      { id: 102, customerId: 1, customerName: 'علی', date: '2026-03-02T08:00:00.000Z', items: [{ cartonName: 'کارتن الف', cartonLength: 30, cartonWidth: 20, cartonHeight: 15, layer: '3', cartonQty: 80, lineTotal: 200, itemProfit: 20 }] },
      { id: 103, customerId: 2, customerName: 'رضا', date: '2026-03-01T08:00:00.000Z', items: [{ cartonName: 'کارتن ب', cartonLength: 40, cartonWidth: 20, cartonHeight: 15, layer: '5', cartonQty: 50, lineTotal: 5000, itemProfit: 900 }] },
      { id: 104, customerId: 2, customerName: 'رضا', date: '2026-04-15T08:00:00.000Z', items: [{ cartonName: 'کارتن ج', cartonLength: 10, cartonWidth: 10, cartonHeight: 10, layer: '3', cartonQty: 99999, lineTotal: 99999, itemProfit: 99999 }] }
    ];
    const range = { start: '2026-03-01T00:00:00.000Z', end: '2026-03-03T00:00:00.000Z' };
    const msg = runWorkerMessage(workerSrc, { id: 3, kind: 'statsLeaders', invoices, range });
    assert(msg.ok === true, `statsLeaders باید ok:true باشد — ${JSON.stringify(msg)}`);
    const res = msg.result;
    assert(res.topCarton && res.topCarton.cartonName === 'کارتن الف' && res.topCarton.qty === 180,
      `topCarton باید کارتن الف با qty=180 باشد — ${JSON.stringify(res.topCarton)}`);
    assert(res.topBuyer && res.topBuyer.name === 'رضا' && approx(res.topBuyer.turnover, 5000), `topBuyer نادرست — ${JSON.stringify(res.topBuyer)}`);
    assert(res.topProfitCustomer && res.topProfitCustomer.name === 'رضا' && approx(res.topProfitCustomer.profit, 900), `topProfitCustomer نادرست — ${JSON.stringify(res.topProfitCustomer)}`);
    assert(res.topCarton.cartonName !== 'کارتن ج', 'فاکتور خارج از بازه نباید topCarton را عوض کند');
  }

  // ── ۴) statsLeaders — بازه‌ی کاملاً خالی → هر سه باید null باشند، نه خطا ──
  {
    const invoices = [{ id: 1, customerId: 1, customerName: 'کسی', date: '2026-03-01T08:00:00.000Z', items: [{ cartonName: 'ک', cartonQty: 1, lineTotal: 1, itemProfit: 1 }] }];
    const msg = runWorkerMessage(workerSrc, { id: 4, kind: 'statsLeaders', invoices, range: { start: '2020-01-01T00:00:00.000Z', end: '2020-01-02T00:00:00.000Z' } });
    assert(msg.ok === true, 'بازه‌ی خالی نباید ok:false بدهد');
    assert(msg.result.topCarton === null && msg.result.topBuyer === null && msg.result.topProfitCustomer === null,
      `بازه‌ی بدون فاکتور باید هر سه را null برگرداند — ${JSON.stringify(msg.result)}`);
  }

  // ── ۵) پیام با kind ناشناخته → ok:false، نه throw بی‌صدا در کل صفحه ──
  {
    const msg = runWorkerMessage(workerSrc, { id: 5, kind: 'somethingElse', invoices: [] });
    assert(msg.ok === false && typeof msg.error === 'string', `kind ناشناخته باید ok:false با پیام خطا برگرداند — ${JSON.stringify(msg)}`);
  }

  // ✅ فاز ۸ نقشه‌راه ۲: مسیر fallback هم‌زمان (computeStatsDataFallback/
  // computeStatsLeadersFallback) از html8.html حذف شد. بدون Repo و بدون
  // Worker سراسری (دقیقاً محیط این تست در Node)، این دو تابع دیگر نباید
  // بی‌صدا نتیجه بدهند — باید reject کنند تا UI پیام خطا نشان دهد.
  // ── ۶) fetchStatsData/fetchStatsLeaders — بدون Worker سراسری (محیط این تست) باید خطا throw کنند ──
  {
    const invoices = [{ id: 1, date: new Date().toISOString(), items: [{ lineTotal: 700, itemProfit: 70 }] }];
    const { fetchStatsData } = getFetchFns(invoices, {});
    let threw = false;
    try { await fetchStatsData('month'); } catch { threw = true; }
    assert(threw === true, 'بدون Repo و بدون Worker باید fetchStatsData reject کند (دیگر fallback حافظه‌ای وجود ندارد)');
  }
  {
    const invoices = [{ id: 1, customerId: 5, customerName: 'کاربر حافظه', date: new Date().toISOString(), items: [{ cartonName: 'کارتن حافظه', cartonQty: 3, lineTotal: 300, itemProfit: 30 }] }];
    const { fetchStatsLeaders } = getFetchFns(invoices, {});
    let threw = false;
    try { await fetchStatsLeaders('month'); } catch { threw = true; }
    assert(threw === true, 'بدون Repo و بدون Worker باید fetchStatsLeaders reject کند');
  }

  // ── ۷) fetchStatsData — با یک Worker جعلی تزریق‌شده (global.Worker/Blob/URL)، باید از مسیر Worker جواب بگیرد نه از fallback هم‌زمان ──
  {
    const invoices = [{ id: 1, date: new Date().toISOString(), items: [{ lineTotal: 42, itemProfit: 4 }] }];
    const savedWorker = global.Worker, savedBlob = global.Blob, savedURL = global.URL;
    global.Worker = makeFakeWorkerGlobal(workerSrc);
    global.Blob = class { constructor() {} };
    global.URL = { createObjectURL: () => 'blob:fake' };
    try {
      const { fetchStatsData } = getFetchFns(invoices, {});
      const res = await fetchStatsData('month');
      const last = res[res.length - 1];
      assert(approx(last.turnover, 42) && approx(last.profit, 4) && last.count === 1, `مسیر Worker جعلی باید همان نتیجه‌ی درست را بدهد — ${JSON.stringify(last)}`);
    } finally {
      global.Worker = savedWorker; global.Blob = savedBlob; global.URL = savedURL;
    }
  }

  console.log(`\n${pass} پاس، ${fail} خطا.`);
  if (fail > 0) process.exit(1);
}

run();
