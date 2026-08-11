// تست resolveInvoiceById — ریزمرحله‌ی ۲.۷.۴ (نسخه‌ی ۴.۱۴)
// این تست منبع واقعی تابع را مستقیم از html8.html استخراج می‌کند (نه یک
// بازنویسی جدا) تا واقعاً همان کدی که در اپ اجرا می‌شود را بسنجد — همان
// اصلی که بقیه‌ی تست‌های این پوشه هم (روی InvoiceRepo واقعی) رعایت می‌کنند.
// اجرا: node tests/resolve-invoice-by-id-test.mjs

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { InvoiceCache } from '../rasticpack-capacitor/src/cache/InvoiceCache.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const html = fs.readFileSync(path.join(__dirname, '..', 'html8.html'), 'utf-8');
const match = html.match(/async function resolveInvoiceById\(id\)\{[\s\S]*?\n\}\n/);
if (!match) {
  console.error('❌ FAIL: resolveInvoiceById در html8.html پیدا نشد — احتمالاً نام/امضا تغییر کرده.');
  process.exit(1);
}

let pass = 0, fail = 0;
function assert(cond, msg) { if (cond) pass++; else { fail++; console.error('❌ FAIL:', msg); } }

// هر سناریو یک sandbox جدا با globals تازه می‌سازد و همان سورس واقعی را با
// new Function اجرا می‌کند — چون تابع از globalهای invoices/window استفاده
// می‌کند، این globalها را به‌عنوان پارامتر تزریق می‌کنیم.
function makeResolver(invoicesArr, windowObj) {
  const factory = new Function('invoices', 'window', `${match[0]}; return resolveInvoiceById;`);
  return factory(invoicesArr, windowObj);
}

async function run() {
  // 1) hit در آرایه‌ی حافظه (سریع‌ترین مسیر)
  {
    const invoices = [{ id: 1, v: 'from-array' }];
    const resolve = makeResolver(invoices, { __invoicesFullyLoaded: true });
    const inv = await resolve(1);
    assert(inv?.v === 'from-array', 'باید از آرایه‌ی حافظه برگرداند وقتی آنجا هست');
  }

  // 2) miss در آرایه، hit در InvoiceCache
  {
    const invoices = [];
    const cache = new InvoiceCache(10);
    cache.set(2, { id: 2, v: 'from-cache' });
    const resolve = makeResolver(invoices, { __invoicesFullyLoaded: true, __invoiceCache: cache });
    const inv = await resolve(2);
    assert(inv?.v === 'from-cache', 'باید وقتی در آرایه نیست از InvoiceCache برگرداند');
  }

  // 3) miss در آرایه و کش، ولی بارگذاری پس‌زمینه تمام شده → نباید به InvoiceRepo برود
  {
    const invoices = [];
    let repoCalled = false;
    const fakeRepo = { getById: async () => { repoCalled = true; return { id: 3 }; } };
    const resolve = makeResolver(invoices, { __invoicesFullyLoaded: true, __InvoiceRepo: fakeRepo });
    const inv = await resolve(3);
    assert(inv === null, 'اگر بارگذاری پس‌زمینه تمام شده، miss باید null برگرداند (نه فرض غلط)');
    assert(repoCalled === false, 'وقتی fullyLoaded=true نباید اصلاً InvoiceRepo.getById صدا زده شود');
  }

  // 4) miss در آرایه و کش، بارگذاری پس‌زمینه ناتمام → باید از InvoiceRepo.getById بگیرد (سناریوی اصلی ریسک ۲.۷.۴)
  {
    const invoices = [];
    let repoCalledWith = null;
    const fakeRepo = { getById: async (id) => { repoCalledWith = id; return { id, customerName: 'قدیمی' }; } };
    const cache = new InvoiceCache(10);
    const resolve = makeResolver(invoices, { __invoicesFullyLoaded: false, __InvoiceRepo: fakeRepo, __invoiceCache: cache });
    const inv = await resolve(999);
    assert(repoCalledWith === 999, 'باید InvoiceRepo.getById را با id درست صدا بزند');
    assert(inv?.customerName === 'قدیمی', 'باید رکورد fetch‌شده از Repo را برگرداند');
    assert(inv?.type === 'final', 'رکورد fetch‌شده باید نرمالایز شود (type:final) — هم‌شکل با بقیه‌ی invoices[]');
    assert(invoices.some(i => i.id === 999), 'رکورد fetch‌شده باید به invoices[] هم اضافه شود تا miss بعدی رایگان باشد');
    assert(cache.has(999), 'رکورد fetch‌شده باید در InvoiceCache هم ست شود');
  }

  // 5) InvoiceRepo موجود نیست (مرورگر معمولی بدون SQLite) → نباید بترکد، فقط null برگرداند
  {
    const invoices = [];
    const resolve = makeResolver(invoices, { __invoicesFullyLoaded: false });
    const inv = await resolve(42);
    assert(inv === null, 'بدون InvoiceRepo باید بی‌صدا null برگرداند، نه throw');
  }

  // 6) InvoiceRepo.getById خطا می‌دهد → نباید بترکد (catch بی‌صدا)
  {
    const invoices = [];
    const fakeRepo = { getById: async () => { throw new Error('DB down'); } };
    const resolve = makeResolver(invoices, { __invoicesFullyLoaded: false, __InvoiceRepo: fakeRepo });
    let threw = false;
    let inv;
    try { inv = await resolve(7); } catch { threw = true; }
    assert(threw === false, 'خطای InvoiceRepo.getById نباید کل زنجیره را بترکاند');
    assert(inv === null, 'روی خطا باید null برگرداند');
  }

  console.log(`\n${pass} پاس، ${fail} خطا`);
  if (fail > 0) process.exit(1);
}

run();
