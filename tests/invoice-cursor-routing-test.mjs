// تست اتصال Virtual List به getPageByCursor برای اسکرول پیوسته — باقی‌مانده‌ی
// رسمی (۱) فاز ۷ (نگاه کن به یادداشت این نسخه بالای roadmap-infinite-records-v4_26.md).
// مثل tests/resolve-invoice-by-id-test.mjs / lazy-load-cache-connect-test.mjs، کد واقعی
// (نه یک بازنویسی جدا) مستقیم با regex از html8.html استخراج و اجرا می‌شود.
// اجرا: node tests/invoice-cursor-routing-test.mjs

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { InvoiceCache } from '../rasticpack-capacitor/src/cache/InvoiceCache.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const html = fs.readFileSync(path.join(__dirname, '..', 'html8.html'), 'utf-8');

let pass = 0, fail = 0;
function assert(cond, msg) { if (cond) pass++; else { fail++; console.error('❌ FAIL:', msg); } }

// کل بلوک از تعریف CURSOR_ANCHOR_CACHE_LIMIT تا انتهای fetchInvoicesPage —
// خودمختار است (فقط به monthSortValToIsoRange نیاز دارد که تزریق می‌شود).
function extractBlock(src) {
  const startMarker = 'const CURSOR_ANCHOR_CACHE_LIMIT=';
  const endMarker = '\nlet _invoicesVirtualList=null;';
  const start = src.indexOf(startMarker);
  const end = src.indexOf(endMarker, start);
  if (start === -1 || end === -1) {
    console.error('❌ FAIL: بلوک cursor-routing در html8.html پیدا نشد.');
    process.exit(1);
  }
  return src.slice(start, end);
}

function makeModule(fakeWindow) {
  const src = extractBlock(html);
  const factory = new Function('window', `function monthSortValToIsoRange(){return null;}
${src}
return { fetchInvoicesPage, invalidateInvoiceCursorCache, _invoiceCursorAnchors };`);
  return factory(fakeWindow);
}

async function run() {
  // ── ۱. اولین fetch (offset=0) باید از getPage (offset-based) استفاده کند، نه cursor ──
  {
    const calls = { getPage: 0, getPageByCursor: 0 };
    const fakeRepo = {
      getPage: async ({ offset, limit }) => { calls.getPage++; return { items: mkItems(offset, limit) }; },
      getPageByCursor: async () => { calls.getPageByCursor++; return { items: [] }; }
    };
    const cache = new InvoiceCache(1000);
    const { fetchInvoicesPage } = makeModule({ __InvoiceRepo: fakeRepo, __invoiceCache: cache });
    const res = await fetchInvoicesPage(0, 10, { fullList: [] });
    assert(calls.getPage === 1 && calls.getPageByCursor === 0, 'اولین صفحه (offset=0) باید از getPage استفاده کند');
    assert(res.length === 10, 'باید ۱۰ آیتم برگرداند');
  }

  // ── ۲. ادامه‌ی پیوسته (offset دقیقاً بلافاصله بعد از چیزی که قبلاً دیده شده) باید از getPageByCursor استفاده کند ──
  {
    const calls = { getPage: 0, getPageByCursor: 0 };
    let lastCursorSeen = null;
    const fakeRepo = {
      getPage: async ({ offset, limit }) => { calls.getPage++; return { items: mkItems(offset, limit) }; },
      getPageByCursor: async ({ cursor, limit }) => {
        calls.getPageByCursor++; lastCursorSeen = cursor;
        const startIdx = cursor.id; // در mkItems، id همان globalIndex است
        return { items: mkItems(startIdx, limit), nextCursor: null };
      }
    };
    const cache = new InvoiceCache(1000);
    const { fetchInvoicesPage } = makeModule({ __InvoiceRepo: fakeRepo, __invoiceCache: cache });
    await fetchInvoicesPage(0, 10, { fullList: [] });           // انکرهای ۰..۹ ثبت می‌شوند
    const res2 = await fetchInvoicesPage(10, 10, { fullList: [] }); // ادامه‌ی دقیق offset=10
    assert(calls.getPageByCursor === 1, 'ادامه‌ی پیوسته (offset=10 بعد از دیدن ۰..۹) باید از getPageByCursor استفاده کند');
    assert(calls.getPage === 1, 'getPage نباید دوباره صدا زده شود وقتی cursor کافی است');
    assert(lastCursorSeen && lastCursorSeen.id === 9, 'cursor باید انکر ردیف ۹ (offset-1) باشد');
    assert(res2.length === 10, 'صفحه‌ی دوم هم باید ۱۰ آیتم برگرداند');
  }

  // ── ۳. جهش واقعی (offset ناشناخته، هیچ انکری برایش ثبت نشده) باید به getPage برگردد ──
  {
    const calls = { getPage: 0, getPageByCursor: 0 };
    const fakeRepo = {
      getPage: async ({ offset, limit }) => { calls.getPage++; return { items: mkItems(offset, limit) }; },
      getPageByCursor: async () => { calls.getPageByCursor++; return { items: [] }; }
    };
    const cache = new InvoiceCache(1000);
    const { fetchInvoicesPage } = makeModule({ __InvoiceRepo: fakeRepo, __invoiceCache: cache });
    await fetchInvoicesPage(0, 10, { fullList: [] });             // انکرهای ۰..۹
    await fetchInvoicesPage(500, 10, { fullList: [] });           // جهش بزرگ — بدون انکر
    assert(calls.getPageByCursor === 0, 'جهش به offset ناشناخته نباید از getPageByCursor استفاده کند');
    assert(calls.getPage === 2, 'جهش باید به getPage (offset-based) برگردد');
  }

  // ── ۴. تغییر فیلتر (customerId متفاوت) باید یک سیگنیچر جدا داشته باشد — بدون تداخل انکر ──
  {
    const calls = { getPageByCursor: 0 };
    const fakeRepo = {
      getPage: async ({ offset, limit }) => ({ items: mkItems(offset, limit) }),
      getPageByCursor: async ({ limit }) => { calls.getPageByCursor++; return { items: mkItems(0, limit) }; }
    };
    const cache = new InvoiceCache(1000);
    const { fetchInvoicesPage } = makeModule({ __InvoiceRepo: fakeRepo, __invoiceCache: cache });
    await fetchInvoicesPage(0, 10, { fullList: [], customerId: 'A' });
    // همان offset=10 ولی برای مشتری دیگر — نباید انکر مشتری A را (به‌اشتباه) استفاده کند
    await fetchInvoicesPage(10, 10, { fullList: [], customerId: 'B' });
    assert(calls.getPageByCursor === 0, 'فیلتر متفاوت (سیگنیچر جدا) نباید از انکر فیلتر قبلی استفاده کند');
  }

  // ── ۵. شکست getPageByCursor باید بی‌صدا به getPage برگردد ──
  {
    const calls = { getPage: 0 };
    const fakeRepo = {
      getPage: async ({ offset, limit }) => { calls.getPage++; return { items: mkItems(offset, limit) }; },
      getPageByCursor: async () => { throw new Error('boom'); }
    };
    const cache = new InvoiceCache(1000);
    const { fetchInvoicesPage } = makeModule({ __InvoiceRepo: fakeRepo, __invoiceCache: cache });
    await fetchInvoicesPage(0, 10, { fullList: [] });
    const res = await fetchInvoicesPage(10, 10, { fullList: [] });
    assert(calls.getPage === 2, 'شکست getPageByCursor باید بی‌صدا catch و به getPage سقوط کند');
    assert(res.length === 10, 'با وجود شکست cursor، نتیجه‌ی نهایی هنوز درست است');
  }

  // ── ۶. invalidateInvoiceCursorCache باید کل کش را پاک کند — بعدش ادامه‌ی «پیوسته» دوباره جهش محسوب می‌شود ──
  {
    const calls = { getPage: 0, getPageByCursor: 0 };
    const fakeRepo = {
      getPage: async ({ offset, limit }) => { calls.getPage++; return { items: mkItems(offset, limit) }; },
      getPageByCursor: async ({ limit }) => { calls.getPageByCursor++; return { items: mkItems(0, limit), nextCursor: null }; }
    };
    const cache = new InvoiceCache(1000);
    const mod = makeModule({ __InvoiceRepo: fakeRepo, __invoiceCache: cache });
    await mod.fetchInvoicesPage(0, 10, { fullList: [] });
    mod.invalidateInvoiceCursorCache();
    await mod.fetchInvoicesPage(10, 10, { fullList: [] }); // انکر پاک شده — باید دوباره offset-based باشد
    assert(calls.getPageByCursor === 0, 'بعد از invalidate، ادامه‌ی قبلی دیگر نباید cursor بخورد');
    assert(calls.getPage === 2, 'بعد از invalidate باید به getPage برگردد');
  }

  // ── ۷. نبود window.__InvoiceRepo → بی‌صدا به fullList برمی‌گردد (بدون خطا) ──
  {
    const { fetchInvoicesPage } = makeModule({});
    const fullList = [{ id: 1 }, { id: 2 }, { id: 3 }];
    const res = await fetchInvoicesPage(0, 2, { fullList });
    assert(res.length === 2 && res[0].id === 1, 'نبود InvoiceRepo باید به fullList.slice برگردد');
  }

  console.log(`\n${pass} پاس، ${fail} شکست`);
  if (fail > 0) process.exit(1);
}

function mkItems(offset, limit) {
  const out = [];
  for (let i = 0; i < limit; i++) {
    const idx = offset + i;
    out.push({ id: idx, date: `2024-01-${String(1 + (idx % 28)).padStart(2, '0')}`, customerName: 'مشتری ' + idx });
  }
  return out;
}

run();
