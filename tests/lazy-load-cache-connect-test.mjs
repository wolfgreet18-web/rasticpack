// تست اتصال Lazy Loading اسکرول/مودال به InvoiceCache — ریزمرحله‌ی ۲.۷.۳ (نسخه‌ی ۴.۱۵)
// مثل tests/resolve-invoice-by-id-test.mjs، توابع واقعی را مستقیم با regex از
// html8.html استخراج می‌کند (نه یک بازنویسی جدا) تا کد واقعاً شیپ‌شده را بسنجد.
// اجرا: node tests/lazy-load-cache-connect-test.mjs

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { InvoiceCache } from '../rasticpack-capacitor/src/cache/InvoiceCache.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const html = fs.readFileSync(path.join(__dirname, '..', 'html8.html'), 'utf-8');

let pass = 0, fail = 0;
function assert(cond, msg) { if (cond) pass++; else { fail++; console.error('❌ FAIL:', msg); } }

function extractFn(name, src) {
  const re = new RegExp(`async function ${name}\\([^)]*\\)\\{[\\s\\S]*?\\n\\}\\n`);
  const m = src.match(re);
  if (!m) { console.error(`❌ FAIL: ${name} در html8.html پیدا نشد.`); process.exit(1); }
  return m[0];
}

// ✅ فاز ۷ (نسخه‌ی جدید): fetchInvoicesPage دیگر خودمختار نیست — به چند تابع/متغیر
// کمکی هم‌سطح (کش انکرهای cursor) وابسته است. برای این‌که تست واقعاً کد شیپ‌شده
// را بسنجد (نه یک بازنویسی جدا)، کل بلوک از تعریف CURSOR_ANCHOR_CACHE_LIMIT تا
// انتهای fetchInvoicesPage با هم استخراج می‌شود — نگاه کن به
// tests/invoice-cursor-routing-test.mjs برای تست اختصاصی منطق مسیریابی cursor/offset.
function extractInvoicesPageBlock(src) {
  const startMarker = 'const CURSOR_ANCHOR_CACHE_LIMIT=';
  const endMarker = '\nlet _invoicesVirtualList=null;';
  const start = src.indexOf(startMarker);
  const end = src.indexOf(endMarker, start);
  if (start === -1 || end === -1) { console.error('❌ FAIL: بلوک fetchInvoicesPage پیدا نشد.'); process.exit(1); }
  return src.slice(start, end);
}

async function run() {
  // ── fetchInvoicesPage (تب فاکتورها / اسکرول createVirtualList) ──
  {
    const src = extractInvoicesPageBlock(html);
    // monthSortValToIsoRange در تابع استفاده می‌شود فقط اگر ctx.month ست باشد؛ اینجا null است.
    const factory = new Function('window', `function monthSortValToIsoRange(){return null;}
${src}
return fetchInvoicesPage;`);
    const cache = new InvoiceCache(10);
    const items = [{ id: 101, customerName: 'الف' }, { id: 102, customerName: 'ب' }];
    const fakeRepo = { getPage: async () => ({ items }) };
    const fetchInvoicesPage = factory({ __InvoiceRepo: fakeRepo, __invoiceCache: cache });
    const res = await fetchInvoicesPage(0, 20, { fullList: [] });
    assert(res.length === 2, 'باید آیتم‌های برگشتی از Repo را برگرداند');
    assert(res.every(i => i.type === 'final'), 'آیتم‌های برگشتی باید نرمالایز شوند (type:final)');
    assert(cache.has(101) && cache.has(102), 'صفحه‌ای که از InvoiceRepo کشیده شد باید در InvoiceCache ست شود');
  }

  // fetchInvoicesPage: بدون InvoiceCache سراسری (window.__invoiceCache نبود) نباید بترکد
  {
    const src = extractInvoicesPageBlock(html);
    const factory = new Function('window', `function monthSortValToIsoRange(){return null;}
${src}
return fetchInvoicesPage;`);
    const fakeRepo = { getPage: async () => ({ items: [{ id: 1 }] }) };
    const fetchInvoicesPage = factory({ __InvoiceRepo: fakeRepo });
    let threw = false;
    try { await fetchInvoicesPage(0, 20, { fullList: [] }); } catch { threw = true; }
    assert(threw === false, 'نبود window.__invoiceCache نباید fetchInvoicesPage را بترکاند');
  }

  // fetchInvoicesPage: fallback به fullList وقتی InvoiceRepo نیست — نباید به cache دست بزند
  {
    const src = extractInvoicesPageBlock(html);
    const factory = new Function('window', `function monthSortValToIsoRange(){return null;}
${src}
return fetchInvoicesPage;`);
    const cache = new InvoiceCache(10);
    const fetchInvoicesPage = factory({ __invoiceCache: cache });
    const fullList = [{ id: 5 }, { id: 6 }];
    const res = await fetchInvoicesPage(0, 20, { fullList });
    assert(res.length === 2 && res[0].id === 5, 'بدون InvoiceRepo باید از fullList (آرایه‌ی حافظه) برگرداند');
    assert(cache.has(5) === false, 'fallback حافظه نباید چیزی به InvoiceCache اضافه کند (چون از قبل در حافظه‌ست)');
  }

  // ── fetchCustomerInvoicesPage (مودال فاکتورهای مشتری) ──
  {
    const src = extractFn('fetchCustomerInvoicesPage', html);
    const factory = new Function('window', `${src}; return fetchCustomerInvoicesPage;`);
    const cache = new InvoiceCache(10);
    const items = [{ id: 201, customerId: 7 }, { id: 202, customerId: 7 }];
    const fakeRepo = { getPage: async () => ({ items }) };
    const fetchCustomerInvoicesPage = factory({ __InvoiceRepo: fakeRepo, __invoiceCache: cache });
    const res = await fetchCustomerInvoicesPage(0, 20, 7, []);
    assert(res.length === 2, 'باید آیتم‌های برگشتی از Repo را برگرداند');
    assert(cache.has(201) && cache.has(202), 'صفحه‌ای که مودال از InvoiceRepo کشید باید در InvoiceCache ست شود');
  }

  // fetchCustomerInvoicesPage: خطای Repo نباید بترکد و باید به fullList برگردد
  {
    const src = extractFn('fetchCustomerInvoicesPage', html);
    const factory = new Function('window', `${src}; return fetchCustomerInvoicesPage;`);
    const cache = new InvoiceCache(10);
    const fakeRepo = { getPage: async () => { throw new Error('DB down'); } };
    const fetchCustomerInvoicesPage = factory({ __InvoiceRepo: fakeRepo, __invoiceCache: cache });
    const fullList = [{ id: 9 }];
    let threw = false, res;
    try { res = await fetchCustomerInvoicesPage(0, 20, 7, fullList); } catch { threw = true; }
    assert(threw === false, 'خطای InvoiceRepo.getPage نباید بترکاند');
    assert(res?.length === 1 && res[0].id === 9, 'روی خطا باید به fullList برگردد');
  }

  console.log(`\n${pass} پاس، ${fail} خطا`);
  if (fail > 0) process.exit(1);
}

run();
