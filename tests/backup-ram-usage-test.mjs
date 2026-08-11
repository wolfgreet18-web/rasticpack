// تست مصرف RAM بازیابی (Restore) — فاز ۶ نقشه‌راه، نسخه‌ی ۴.۲۴
//
// معیار پذیرش رسمی فاز ۶ («بکاپ/بازیابی ۱۰ میلیون رکورد بدون افزایش غیرمنطقی
// RAM») در نسخه‌ی ۴.۲۳ فقط از نظر منطق batching/فرمت تست شده بود — نه از نظر
// عدد واقعی RAM. این فایل همان روش اثبات‌شده‌ی خودِ این پروژه برای فاز ۲.۷
// (tests/ram-usage-test.mjs: از process.memoryUsage() به‌عنوان پروکسی معتبر
// برای رشد heap استفاده کن، چون IndexedDB/مرورگر واقعی در sandbox نیست) را
// روی «بازیابی» (که واقعی‌ترین ریسک RAM را دارد، نه ساخت بکاپ) تکرار می‌کند.
//
// مقایسه: بازیابی مسیر قدیمی (doRestoreLegacyJson — JSON.parse کامل متن +
// ساخت آرایه‌ی invoices با .map) در برابر مسیر جدید (applyNdjsonBackup —
// batch به batch، هر batch بعد از پردازش دور انداخته می‌شود، هیچ‌وقت آرایه‌ی
// کامل ساخته نمی‌شود).
//
// توابع واقعی (backupHeaderLine/backupRecordLine/buildBackupParts/
// applyNdjsonBackup/BACKUP_BATCH) مستقیم از html8.html استخراج می‌شوند —
// همان روش backup-ndjson-test.mjs.
//
// اجرا: node --expose-gc tests/backup-ram-usage-test.mjs
// (بدون --expose-gc هم اجرا می‌شود، فقط عدد نویزی‌تر است)

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const html = fs.readFileSync(path.join(__dirname, '..', 'html8.html'), 'utf-8');

function extract(re, name) {
  const m = html.match(re);
  if (!m) { console.error(`❌ FAIL: ${name} پیدا نشد`); process.exit(1); }
  return m[0];
}

const srcBackupBatchConst = extract(/const BACKUP_BATCH=\d+;/, 'BACKUP_BATCH');
const srcHeaderLine  = extract(/function backupHeaderLine\(\)\{[\s\S]*?\n\}\n/, 'backupHeaderLine');
const srcRecordLine  = extract(/function backupRecordLine\(t,d\)\{[\s\S]*?\n\}\n/, 'backupRecordLine');
const srcBuildParts  = extract(/async function buildBackupParts\(\{[\s\S]*?\n\}\n/, 'buildBackupParts');
const srcApplyNdjson = extract(/async function applyNdjsonBackup\(text,\{[\s\S]*?\n\}\n/, 'applyNdjsonBackup');

const factory = new Function(`
  ${srcBackupBatchConst}
  ${srcHeaderLine}
  ${srcRecordLine}
  ${srcBuildParts}
  ${srcApplyNdjson}
  return { BACKUP_BATCH, backupHeaderLine, backupRecordLine, buildBackupParts, applyNdjsonBackup };
`);
const { BACKUP_BATCH, buildBackupParts, applyNdjsonBackup } = factory();

// شکل تقریبی رکورد واقعی این اپ — مثل ram-usage-test.mjs، نه یک عدد ساده
const N_INVOICES = 300000;
const N_CUSTOMERS = 20000;

function makeInvoice(id) {
  return {
    id, customerId: id % N_CUSTOMERS, customerName: `مشتری شماره ${id % N_CUSTOMERS}`,
    date: new Date(2024, 0, 1 + (id % 365)).toISOString(),
    items: [
      { productId: 1, qty: 3, price: 125000, name: 'ورق ۳ میل' },
      { productId: 2, qty: 1, price: 480000, name: 'ورق ۵ میل' },
    ],
    total: 855000, paid: id % 3 === 0 ? 855000 : 0,
    status: id % 3 === 0 ? 'paid' : (id % 3 === 1 ? 'partial' : 'draft'), note: '',
  };
}
function makeCustomer(id) {
  return { id, name: `مشتری شماره ${id}`, company: `شرکت ${id}`, address: 'تهران', phone: '0912' + String(id).padStart(7, '0') };
}

function heapMB() { if (global.gc) global.gc(); return process.memoryUsage().heapUsed / (1024 * 1024); }
function fmt(n) { return n.toFixed(1); }

let pass = 0, fail = 0;
function assert(cond, msg) { if (cond) pass++; else { fail++; console.error('❌ FAIL:', msg); } }

async function run() {
  if (!global.gc) console.log('⚠️  بدون --expose-gc اجرا شد — اعداد نویزی‌تر خواهند بود.\n');

  // ── ساخت متن‌های ورودی (شبیه‌سازی فایل بکاپ روی دیسک) — یک‌بار، خارج از
  // اندازه‌گیری، چون در سناریوی واقعی هم متن از قبل روی دیسک/فایل موجود است. ──
  const customers = Array.from({ length: N_CUSTOMERS }, (_, i) => makeCustomer(i + 1));
  const invoices = Array.from({ length: N_INVOICES }, (_, i) => makeInvoice(i + 1));

  const legacyJsonText = JSON.stringify({
    app: 'rasticpack', v: 20, customers, invoices,
    inventory: [], productionQueue: [], vanDrivers: [],
    meta: { nextId: 1, nextCustId: N_CUSTOMERS + 1, nextInvoiceId: N_INVOICES + 1 },
  });

  const readBatches = async (storeName, batchSize, onBatch) => {
    const arr = storeName === 'customers' ? customers : invoices;
    for (let i = 0; i < arr.length; i += batchSize) await onBatch(arr.slice(i, i + batchSize));
  };
  const { parts } = await buildBackupParts({ readBatches, metaObj: {} });
  const ndjsonText = parts.join('');
  parts.length = 0; // آزاد کردن بلافاصله — دیگر لازم نیست

  console.log(`ورودی: ${N_CUSTOMERS.toLocaleString('en-US')} مشتری + ${N_INVOICES.toLocaleString('en-US')} فاکتور`);
  console.log(`حجم متن legacy-json: ${fmt(legacyJsonText.length / 1024 / 1024)}MB, حجم متن ndjson-v1: ${fmt(ndjsonText.length / 1024 / 1024)}MB\n`);

  heapMB(); // GC قبل از شروع اندازه‌گیری اصلی

  // ── سناریوی «قبل» (doRestoreLegacyJson): JSON.parse کل متن + یک آرایه‌ی
  // دوم با .map (دقیقاً همان دو خط واقعی خودِ تابع در html8.html) ──
  const beforeStart = heapMB();
  const parsed = JSON.parse(legacyJsonText);
  const restoredInvoices = parsed.invoices.map(i => ({ type: 'final', ...i }));
  const restoredCustomers = parsed.customers;
  const beforeEnd = heapMB();
  const beforeDeltaMB = beforeEnd - beforeStart;
  console.log(`«قبل» (doRestoreLegacyJson: JSON.parse کامل + آرایه‌ی invoices[]): +${fmt(beforeDeltaMB)}MB heap`);
  assert(restoredInvoices.length === N_INVOICES, 'سناریوی «قبل» باید همه‌ی فاکتورها را در حافظه نگه دارد');
  assert(restoredCustomers.length === N_CUSTOMERS, 'سناریوی «قبل» باید همه‌ی مشتریان را در حافظه نگه دارد');

  // آزاد کردن قبل از رفتن به سناریوی بعدی — دو سناریو نباید هم‌پوشانی داشته باشند
  parsed.invoices = null; parsed.customers = null;
  const parsedRef = null; void parsedRef;
  // (خودِ متغیرهای بالا هم باید از scope خارج شوند تا GC واقعاً آزادشان کند —
  // چون JS بلاک-scope دارد نه تابع، یک تابع جدا برای این بخش امن‌تر بود؛ اینجا
  // فقط ارجاع‌های داخلی‌شان پاک شدند که برای اندازه‌گیری heap کافی است)

  heapMB();

  // ── سناریوی «بعد» (doRestoreNdjson/applyNdjsonBackup): batch به batch —
  // هر batch فقط شمارش می‌شود و دور انداخته می‌شود، دقیقاً مثل onCustomerBatch/
  // onInvoiceBatch واقعی در html8.html که بعد از idbBulkPut/bulkInsert دیگر
  // batch را نگه نمی‌دارند؛ در نهایت هیچ آرایه‌ی invoices/customers کامل
  // ساخته نمی‌شود (برخلاف سناریوی «قبل»). ──
  const afterStart = heapMB();
  let custCount = 0, invCount = 0, maxBatchSeen = 0;
  const meta = await applyNdjsonBackup(ndjsonText, {
    batchSize: BACKUP_BATCH,
    onCustomerBatch: async batch => { custCount += batch.length; maxBatchSeen = Math.max(maxBatchSeen, batch.length); },
    onInvoiceBatch: async batch => { invCount += batch.length; maxBatchSeen = Math.max(maxBatchSeen, batch.length); },
  });
  const afterEnd = heapMB();
  const afterDeltaMB = afterEnd - afterStart;
  console.log(`«بعد» (applyNdjsonBackup: batch=${BACKUP_BATCH}، بدون آرایه‌ی کامل): +${fmt(afterDeltaMB)}MB heap`);

  assert(custCount === N_CUSTOMERS, `applyNdjsonBackup باید همه‌ی ${N_CUSTOMERS} مشتری را batch به batch پردازش کند`);
  assert(invCount === N_INVOICES, `applyNdjsonBackup باید همه‌ی ${N_INVOICES} فاکتور را batch به batch پردازش کند`);
  assert(maxBatchSeen <= BACKUP_BATCH, `هیچ batch نباید بزرگ‌تر از BACKUP_BATCH (${BACKUP_BATCH}) باشد`);
  void meta;

  if (beforeDeltaMB > 0 && afterDeltaMB >= 1) {
    const ratio = beforeDeltaMB / afterDeltaMB;
    console.log(`\nنسبت مصرف heap «قبل»/«بعد»: ~${fmt(ratio)}× کمتر (روی این محیط/Node — عدد دقیق مرورگر/دستگاه واقعی هنوز اندازه‌گیری نشده، مثل بقیه‌ی معیارهای این سند)`);
    assert(ratio > 1, 'مسیر جدید باید heap محسوساً کمتری نسبت به مسیر قدیمی مصرف کند');
  } else if (beforeDeltaMB > 0) {
    // afterDeltaMB زیر ۱MB است — گزارش یک نسبت عددی اینجا گمراه‌کننده است (مخرج
    // نزدیک صفر یعنی نسبت هر عددی می‌تواند باشد، نه یک اندازه‌گیری معنادار).
    // آنچه واقعاً و صادقانه قابل‌گفتن است: مسیر جدید عملاً heap اضافه‌ی
    // قابل‌اندازه‌گیری روی این حجم داده باقی نمی‌گذارد.
    console.log(`\n«بعد» زیر آستانه‌ی اندازه‌گیری (${fmt(afterDeltaMB)}MB) بود — به‌جای یک نسبت گمراه‌کننده، همین کافی است: مسیر جدید عملاً heap اضافه‌ی قابل‌سنجش روی این حجم باقی نمی‌گذارد، برخلاف ${fmt(beforeDeltaMB)}MB مسیر قدیمی.`);
    assert(afterDeltaMB < beforeDeltaMB, 'مسیر جدید باید heap کمتری نسبت به مسیر قدیمی مصرف کند');
  }

  // ── تأیید مستقل از نویز GC: تضمین الگوریتمی که batch هرگز از سقف رد نمی‌شود،
  // حتی روی دیتاستی خیلی بزرگ‌تر (همان الگوی c2 در ram-usage-test.mjs) ──
  {
    let maxSeen = 0, total = 0;
    const bigReadBatches = async (storeName, batchSize, onBatch) => {
      const n = storeName === 'invoices' ? 2_000_000 : 0;
      for (let i = 0; i < n; i += batchSize) {
        const size = Math.min(batchSize, n - i);
        maxSeen = Math.max(maxSeen, size); total += size;
        await onBatch(new Array(size).fill(0));
      }
    };
    await buildBackupParts({ readBatches: bigReadBatches, metaObj: {} }).then(({ invCount: ic }) => {
      assert(ic === 2_000_000, 'buildBackupParts باید حتی روی ۲M رکورد شمارش درست بدهد');
    });
    assert(maxSeen <= BACKUP_BATCH, 'حتی با ۲M رکورد، اندازه‌ی هر batch هرگز از سقف رد نشود (تضمین الگوریتمی، نه فقط اندازه‌گیری heap)');
  }

  console.log(`\n${pass} پاس، ${fail} خطا`);
  if (fail > 0) process.exit(1);
}

run();
