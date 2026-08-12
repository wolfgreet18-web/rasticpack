// تست فاز ۴ اقدام ۱، ادامه (نسخه‌ی ۴.۲۰) — رکورددارهای تب آمار
// (پرفروش‌ترین کارتن / مشتری با بیشترین خرید / مشتری با بیشترین سود):
//   ۱) InvoiceRepo.getStatsLeaders روی یک دیتابیس واقعی node:sqlite —
//      گروه‌بندی روی کلید ترکیبی کارتن (نام+ابعاد+لایه) و روی customerId،
//      با json_each روی items[] هر فاکتور.
//   ۲) fetchStatsLeaders در html8.html — پل بین این متد و Web Worker/خطای
//      fail-fast (از فاز ۸ نقشه‌راه ۲، دیگر fallback حافظه‌ای وجود ندارد)،
//      شامل ساخت برچسب فارسی کارتن (dims + layerLabel) که عمداً در UI مانده، نه در Repo.
// اجرا: node --experimental-sqlite tests/stats-leaders-test.mjs

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
  const endMarker = 'async function fetchStatsLeaders(period){';
  const startIdx = src.indexOf(startMarker);
  if (startIdx === -1) { console.error('❌ FAIL: calcInvoiceProfit در html8.html پیدا نشد.'); process.exit(1); }
  const fnStartIdx = src.indexOf(endMarker, startIdx);
  if (fnStartIdx === -1) { console.error('❌ FAIL: fetchStatsLeaders در html8.html پیدا نشد.'); process.exit(1); }
  const closeIdx = src.indexOf('\n}\n', fnStartIdx);
  if (closeIdx === -1) { console.error('❌ FAIL: انتهای fetchStatsLeaders پیدا نشد.'); process.exit(1); }
  return src.slice(startIdx, closeIdx + 3);
}

function getFetchStatsLeaders(invoicesArr, windowObj) {
  const block = extractBlock(html);
  // toFaDigits/layerLabel هر دو خارج از این بلوک تعریف شده‌اند (فایل کامل
  // html8.html) — روی محاسبات SQL/aggregation این تست اثری ندارند، فقط
  // برای این‌که خودِ fetchStatsLeaders بدون ReferenceError اجرا شود تزریق
  // می‌شوند. layerLabel دقیقاً همان تعریف واقعی خط ۷۵۷ فایل است، نه یک mock
  // ساده‌شده — چون برچسب نهایی (assertion‌های این تست) به آن وابسته است.
  const layerLabel = l => l === '5' ? 'پنج‌لایه' : 'سه‌لایه';
  // [فاز ۱۱ نقشه‌راه ۲] computeStatsLeadersViaWorker دیگر آرایه‌ی سراسری invoices
  // را نمی‌خواند — fetchAllInvoicesForBackgroundLoad (بیرون از این بلوک) را صدا
  // می‌زند؛ همان invoicesArr قبلی از طریق یک stub تزریق می‌شود.
  const factory = new Function('invoices', 'window', 'toFaDigits', 'layerLabel', 'fetchAllInvoicesForBackgroundLoad',
    `${block}; return fetchStatsLeaders;`);
  return factory(invoicesArr, windowObj, (n) => String(n), layerLabel, async () => invoicesArr);
}

async function run() {
  const db = await getDb();

  await db.run(`INSERT INTO customers (id, name) VALUES (?, ?), (?, ?)`, [1, 'علی', 2, 'رضا']);
  const mkInvoice = (id, customerId, customerName, date, items) => ({
    id, customerId, customerName, date, status: 'paid', sent: 0, sentToProduction: 0, totalSheets: 0, items
  });
  // علی: کارتن الف ×۱۰۰ (روز۱) + ×۸۰ (روز۲) = ۱۸۰ جمعاً، گردش/سود کم
  await InvoiceRepo.save(mkInvoice(101, 1, 'علی', '2026-03-01T08:00:00.000Z',
    [{ cartonName: 'کارتن الف', cartonLength: 30, cartonWidth: 20, cartonHeight: 15, layer: '3', cartonQty: 100, lineTotal: 1000, itemProfit: 100 }]));
  await InvoiceRepo.save(mkInvoice(102, 1, 'علی', '2026-03-02T08:00:00.000Z',
    [{ cartonName: 'کارتن الف', cartonLength: 30, cartonWidth: 20, cartonHeight: 15, layer: '3', cartonQty: 80, lineTotal: 200, itemProfit: 20 }]));
  // رضا: کارتن ب ×۵۰ (کمتر از مجموع الف)، ولی گردش/سود خیلی بیشتر
  await InvoiceRepo.save(mkInvoice(103, 2, 'رضا', '2026-03-01T08:00:00.000Z',
    [{ cartonName: 'کارتن ب', cartonLength: 40, cartonWidth: 20, cartonHeight: 15, layer: '5', cartonQty: 50, lineTotal: 5000, itemProfit: 900 }]));
  // خارج از بازه — نباید حساب شود
  await InvoiceRepo.save(mkInvoice(104, 2, 'رضا', '2026-04-15T08:00:00.000Z',
    [{ cartonName: 'کارتن ج', cartonLength: 10, cartonWidth: 10, cartonHeight: 10, layer: '3', cartonQty: 99999, lineTotal: 99999, itemProfit: 99999 }]));

  // ── ۱) InvoiceRepo.getStatsLeaders روی بازه‌ی ۱–۲ اسفند ──
  {
    const res = await InvoiceRepo.getStatsLeaders('2026-03-01T00:00:00.000Z', '2026-03-03T00:00:00.000Z');
    assert(res.topCarton && res.topCarton.cartonName === 'کارتن الف' && res.topCarton.qty === 180,
      `topCarton باید «کارتن الف» با qty=180 باشد (مجموع دو فاکتور علی) — ${JSON.stringify(res.topCarton)}`);
    assert(res.topBuyer && res.topBuyer.name === 'رضا' && approx(res.topBuyer.turnover, 5000),
      `topBuyer باید «رضا» با turnover=5000 باشد (بیشتر از مجموع ۱۲۰۰ علی) — ${JSON.stringify(res.topBuyer)}`);
    assert(res.topProfitCustomer && res.topProfitCustomer.name === 'رضا' && approx(res.topProfitCustomer.profit, 900),
      `topProfitCustomer باید «رضا» با profit=900 باشد — ${JSON.stringify(res.topProfitCustomer)}`);
  }

  // ── ۲) فاکتور خارج از بازه (کارتن ج، اردیبهشت) نباید هیچ‌کدام از رکورددارها را عوض کند ──
  {
    const res = await InvoiceRepo.getStatsLeaders('2026-03-01T00:00:00.000Z', '2026-03-03T00:00:00.000Z');
    assert(res.topCarton.cartonName !== 'کارتن ج', 'کارتن ج (خارج از بازه) نباید topCarton شود');
    assert(res.topBuyer.turnover < 99999, 'گردش‌مالی فاکتور خارج از بازه نباید در جمع بازه‌ی اسفند حساب شود');
  }

  // ── ۳) بازه‌ی کاملاً خالی → هر سه باید null باشند، نه خطا ──
  {
    const res = await InvoiceRepo.getStatsLeaders('2020-01-01T00:00:00.000Z', '2020-01-02T00:00:00.000Z');
    assert(res.topCarton === null && res.topBuyer === null && res.topProfitCustomer === null,
      `بازه‌ی بدون فاکتور باید هر سه فیلد را null برگرداند — ${JSON.stringify(res)}`);
  }

  // ── ۴) fetchStatsLeaders — مسیر SQL موفق، برچسب فارسی کارتن باید در UI (نه Repo) ساخته شود ──
  {
    const fakeRepo = {
      getStatsLeaders: async (start, end) => ({
        topCarton: { cartonName: 'جعبه تست', cartonLength: 10, cartonWidth: 20, cartonHeight: 30, layer: '5', qty: 42 },
        topBuyer: { customerId: 9, name: 'مشتری تست', turnover: 700 },
        topProfitCustomer: { customerId: 9, name: 'مشتری تست', profit: 70 }
      })
    };
    const fetchStatsLeaders = getFetchStatsLeaders([], { __InvoiceRepo: fakeRepo });
    const res = await fetchStatsLeaders('week');
    assert(res.topCarton.label === 'جعبه تست 10×20×30 · پنج‌لایه', `برچسب باید dims+layerLabel را ترکیب کند — «${res.topCarton?.label}»`);
    assert(res.topCarton.qty === 42, 'qty باید بدون تغییر از Repo عبور کند');
    assert(res.topBuyer.name === 'مشتری تست' && res.topBuyer.turnover === 700, 'topBuyer باید از پاسخ Repo بیاید');
    assert(res.topProfitCustomer.profit === 70, 'topProfitCustomer باید از پاسخ Repo بیاید');
  }

  // ── ۵) fetchStatsLeaders — یکی از سه فیلد null (بازه‌ی کم‌داده) → نباید بترکد ──
  {
    const fakeRepo = { getStatsLeaders: async () => ({ topCarton: null, topBuyer: { customerId: 1, name: 'تنها مشتری', turnover: 10 }, topProfitCustomer: null }) };
    const fetchStatsLeaders = getFetchStatsLeaders([], { __InvoiceRepo: fakeRepo });
    const res = await fetchStatsLeaders('week');
    assert(res.topCarton === null, 'topCarton:null از Repo باید همان‌طور null بماند');
    assert(res.topProfitCustomer === null, 'topProfitCustomer:null از Repo باید همان‌طور null بماند');
    assert(res.topBuyer.name === 'تنها مشتری', 'topBuyer موجود باید درست عبور کند حتی وقتی بقیه null هستند');
  }

  // ✅ فاز ۸ نقشه‌راه ۲: computeStatsLeadersFallback (حلقه‌ی JS روی آرایه‌ی
  // حافظه) از html8.html کامل حذف شد. تست‌های ۶ تا ۸ زیر قبلاً همان fallback
  // را verify می‌کردند؛ حالا verify می‌کنند که fetchStatsLeaders وقتی هم Repo
  // و هم Worker شکست می‌خورند (در Node، Worker همیشه در دسترس نیست)، دیگر
  // بی‌صدا سقوط نمی‌کند بلکه خطا throw می‌کند.

  // ── ۶) fetchStatsLeaders — نبود window.__InvoiceRepo (و نبود Worker) → باید خطا throw کند ──
  {
    const invoices = [{ id: 1, customerId: 5, customerName: 'کاربر حافظه', date: new Date().toISOString(),
      items: [{ cartonName: 'کارتن حافظه', cartonQty: 3, lineTotal: 300, itemProfit: 30 }] }];
    const fetchStatsLeaders = getFetchStatsLeaders(invoices, {});
    let threw = false, errMsg = '';
    try { await fetchStatsLeaders('month'); } catch (e) { threw = true; errMsg = e?.message || ''; }
    assert(threw === true, 'نبود Repo و Worker باید fetchStatsLeaders را reject کند، نه سقوط بی‌صدا به آرایه‌ی حافظه');
    assert(/آمار در دسترس نیست/.test(errMsg), `پیام خطا باید برای UI قابل‌فهم باشد — دریافت شد: "${errMsg}"`);
  }

  // ── ۷) fetchStatsLeaders — خطای getStatsLeaders (و نبود Worker) → باید نهایتاً خطا throw کند ──
  {
    const invoices = [{ id: 1, customerId: 5, customerName: 'کاربر حافظه', date: new Date().toISOString(),
      items: [{ cartonName: 'کارتن حافظه', cartonQty: 3, lineTotal: 300, itemProfit: 30 }] }];
    const fakeRepo = { getStatsLeaders: async () => { throw new Error('DB down'); } };
    const fetchStatsLeaders = getFetchStatsLeaders(invoices, { __InvoiceRepo: fakeRepo });
    let threw = false;
    try { await fetchStatsLeaders('month'); } catch { threw = true; }
    assert(threw === true, 'خطای Repo + نبود Worker باید در نهایت reject کند (دیگر fallback حافظه‌ای وجود ندارد)');
  }

  // ── ۸) fetchStatsLeaders — نبود متد getStatsLeaders روی repo (و نبود Worker) → باید خطا throw کند ──
  {
    const invoices = [{ id: 1, customerId: 5, customerName: 'کاربر حافظه', date: new Date().toISOString(),
      items: [{ cartonName: 'کارتن حافظه', cartonQty: 3, lineTotal: 300, itemProfit: 30 }] }];
    const fakeRepo = {}; // بدون getStatsLeaders
    const fetchStatsLeaders = getFetchStatsLeaders(invoices, { __InvoiceRepo: fakeRepo });
    let threw = false;
    try { await fetchStatsLeaders('month'); } catch { threw = true; }
    assert(threw === true, 'نبود متد getStatsLeaders + نبود Worker باید reject کند');
  }

  console.log(`\n${pass} پاس، ${fail} خطا.`);
  if (fail > 0) process.exit(1);
}

run();
