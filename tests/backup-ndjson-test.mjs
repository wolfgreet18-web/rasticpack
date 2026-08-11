// تست فرمت بکاپ/بازیابی ndjson-v1 — فاز ۶ نقشه‌راه (نسخه‌ی ۴.۲۳)
// این تست منبع واقعی توابع را مستقیم از html8.html استخراج می‌کند (همان روش
// resolve-invoice-by-id-test.mjs/stats-worker-test.mjs) — نه یک بازنویسی جدا.
// idbForEachBatch/IndexedDB واقعی اینجا تست نمی‌شود (نیاز به مرورگر دارد،
// مثل بقیه‌ی این پوشه)؛ آنچه اینجا تست می‌شود منطق خالص/تزریق‌پذیر است:
// backupHeaderLine/backupRecordLine/buildBackupParts (با readBatches جعلی)،
// detectBackupFormat، applyNdjsonBackup (با onCustomerBatch/onInvoiceBatch
// جعلی).
// اجرا: node tests/backup-ndjson-test.mjs

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const html = fs.readFileSync(path.join(__dirname, '..', 'html8.html'), 'utf-8');

function extract(re, name) {
  const m = html.match(re);
  if (!m) {
    console.error(`❌ FAIL: ${name} در html8.html پیدا نشد — احتمالاً نام/امضا تغییر کرده.`);
    process.exit(1);
  }
  return m[0];
}

const srcHeaderLine   = extract(/function backupHeaderLine\(\)\{[\s\S]*?\n\}\n/, 'backupHeaderLine');
const srcRecordLine   = extract(/function backupRecordLine\(t,d\)\{[\s\S]*?\n\}\n/, 'backupRecordLine');
const srcBuildParts   = extract(/async function buildBackupParts\(\{[\s\S]*?\n\}\n/, 'buildBackupParts');
const srcDetectFormat = extract(/function detectBackupFormat\(text\)\{[\s\S]*?\n\}\n/, 'detectBackupFormat');
const srcApplyNdjson  = extract(/async function applyNdjsonBackup\(text,\{[\s\S]*?\n\}\n/, 'applyNdjsonBackup');

// BACKUP_BATCH یک ثابت سطح-فایل در html8.html است (پیش‌فرض batchSize توابع
// پایین) — این‌جا با همان مقدار واقعی (استخراج‌شده، نه هاردکد جدا) تزریق می‌شود.
const srcBackupBatchConst = extract(/const BACKUP_BATCH=\d+;/, 'BACKUP_BATCH');

const factory = new Function(`
  ${srcBackupBatchConst}
  ${srcHeaderLine}
  ${srcRecordLine}
  ${srcBuildParts}
  ${srcDetectFormat}
  ${srcApplyNdjson}
  return { backupHeaderLine, backupRecordLine, buildBackupParts, detectBackupFormat, applyNdjsonBackup };
`);
const { backupHeaderLine, backupRecordLine, buildBackupParts, detectBackupFormat, applyNdjsonBackup } = factory();

let pass = 0, fail = 0;
function assert(cond, msg) { if (cond) pass++; else { fail++; console.error('❌ FAIL:', msg); } }

/* شبیه‌ساز readBatches حافظه‌ای — همان قرارداد idbForEachBatch واقعی
   (storeName, batchSize, onBatch) → به‌ازای هر batch از آرایه‌ی داده‌شده
   onBatch را صدا می‌زند، بدون هیچ IndexedDB واقعی. */
function makeFakeReadBatches(dataByStore) {
  return async (storeName, batchSize, onBatch) => {
    const arr = dataByStore[storeName] || [];
    for (let i = 0; i < arr.length; i += batchSize) {
      await onBatch(arr.slice(i, i + batchSize));
    }
  };
}

async function run() {
  // 1) backupHeaderLine/backupRecordLine — قالب پایه
  {
    const line = backupHeaderLine();
    assert(line.endsWith('\n'), 'backupHeaderLine باید با newline تمام شود');
    const h = JSON.parse(line.trim());
    assert(h.app === 'rasticpack' && h.format === 'ndjson-v1', 'هدر باید app=rasticpack و format=ndjson-v1 باشد');
  }
  {
    const line = backupRecordLine('customer', { id: 5, name: 'تست' });
    const rec = JSON.parse(line.trim());
    assert(rec.t === 'customer' && rec.d.id === 5, 'backupRecordLine باید {t,d} تولید کند');
  }

  // 2) buildBackupParts — ترتیب (مشتری قبل از فاکتور)، batching، و شمارش صحیح
  {
    const customers = Array.from({ length: 5 }, (_, i) => ({ id: i + 1, name: 'c' + i }));
    const invoices = Array.from({ length: 7 }, (_, i) => ({ id: i + 1, total: i * 10 }));
    const readBatches = makeFakeReadBatches({ customers, invoices });
    const { parts, custCount, invCount } = await buildBackupParts({ readBatches, batchSize: 2, metaObj: { wastePrice: 100 } });
    assert(custCount === 5, 'custCount باید ۵ باشد');
    assert(invCount === 7, 'invCount باید ۷ باشد');
    // خط اول باید هدر باشد
    const header = JSON.parse(parts[0].trim());
    assert(header.format === 'ndjson-v1', 'اولین بخش باید خط هدر باشد');
    // بقیه‌ی خطوط: ابتدا ۵ خط مشتری، بعد ۷ خط فاکتور، بعد ۱ خط meta
    const rest = parts.slice(1).map(p => JSON.parse(p.trim()));
    assert(rest.length === 5 + 7 + 1, 'تعداد کل خطوط باید customers+invoices+meta باشد');
    assert(rest.slice(0, 5).every(r => r.t === 'customer'), 'همه‌ی ۵ خط اول بعد از هدر باید مشتری باشند (قبل از فاکتور، ترتیب FK)');
    assert(rest.slice(5, 12).every(r => r.t === 'invoice'), '۷ خط بعدی باید فاکتور باشند');
    const metaRec = rest[rest.length - 1];
    assert(metaRec.t === 'meta' && metaRec.d.wastePrice === 100, 'خط آخر باید meta با فیلدهای تزریق‌شده باشد');
    assert(metaRec.d.custCount === 5 && metaRec.d.invCount === 7, 'meta باید custCount/invCount درست را هم داشته باشد');
  }

  // 3) buildBackupParts با دیتاست خالی — نباید بترکد
  {
    const readBatches = makeFakeReadBatches({ customers: [], invoices: [] });
    const { parts, custCount, invCount } = await buildBackupParts({ readBatches, metaObj: {} });
    assert(custCount === 0 && invCount === 0, 'دیتاست خالی باید شمارش صفر بدهد');
    assert(parts.length === 2, 'فقط باید خط هدر + خط meta باشد (بدون هیچ رکوردی)');
  }

  // 4) detectBackupFormat
  {
    const ndjsonText = backupHeaderLine() + backupRecordLine('customer', { id: 1 });
    assert(detectBackupFormat(ndjsonText) === 'ndjson-v1', 'باید فرمت جدید را تشخیص دهد');
  }
  {
    const legacyText = JSON.stringify({ app: 'rasticpack', v: 20, customers: [], invoices: [] });
    assert(detectBackupFormat(legacyText) === 'legacy-json', 'بکاپ تک-JSON قدیمی باید legacy-json تشخیص داده شود');
  }
  {
    assert(detectBackupFormat('این یک متن کاملاً نامعتبر است') === 'legacy-json', 'متن غیر-JSON هم باید legacy-json برگرداند (نه throw) تا مسیر قدیمی خطای مناسب بدهد');
    assert(detectBackupFormat('') === 'unknown', 'رشته‌ی خالی باید unknown برگرداند');
  }

  // 5) applyNdjsonBackup — batching صحیح، ترتیب customer→invoice، رد شدن خط هدر
  {
    const customers = Array.from({ length: 5 }, (_, i) => ({ id: i + 1 }));
    const invoices = Array.from({ length: 5 }, (_, i) => ({ id: i + 1 }));
    const readBatches = makeFakeReadBatches({ customers, invoices });
    const { parts } = await buildBackupParts({ readBatches, batchSize: 3, metaObj: { wastePrice: 55 } });
    const text = parts.join('');

    const custBatches = [], invBatches = [];
    const meta = await applyNdjsonBackup(text, {
      batchSize: 2,
      onCustomerBatch: async b => custBatches.push(b.length),
      onInvoiceBatch: async b => invBatches.push(b.length),
    });
    assert(JSON.stringify(custBatches) === JSON.stringify([2, 2, 1]), `customer batches باید [2,2,1] باشد، بود: ${JSON.stringify(custBatches)}`);
    assert(JSON.stringify(invBatches) === JSON.stringify([2, 2, 1]), `invoice batches باید [2,2,1] باشد، بود: ${JSON.stringify(invBatches)}`);
    assert(meta && meta.wastePrice === 55, 'meta باید از خط آخر برگردانده شود');
    assert(meta.custCount === 5 && meta.invCount === 5, 'meta باید شمارش صحیح داشته باشد');
  }

  // 6) applyNdjsonBackup با batchSize بزرگ‌تر از کل داده — یک batch نهایی
  {
    const readBatches = makeFakeReadBatches({ customers: [{ id: 1 }, { id: 2 }], invoices: [{ id: 1 }] });
    const { parts } = await buildBackupParts({ readBatches, metaObj: {} });
    const text = parts.join('');
    const custBatches = [], invBatches = [];
    await applyNdjsonBackup(text, {
      batchSize: 1000,
      onCustomerBatch: async b => custBatches.push(b.length),
      onInvoiceBatch: async b => invBatches.push(b.length),
    });
    assert(JSON.stringify(custBatches) === JSON.stringify([2]), 'با batchSize بزرگ باید یک batch نهایی برای مشتری‌ها باشد');
    assert(JSON.stringify(invBatches) === JSON.stringify([1]), 'با batchSize بزرگ باید یک batch نهایی برای فاکتورها باشد');
  }

  // 7) applyNdjsonBackup با خطوط خراب/ناقص وسط فایل — نباید بترکد، بقیه باید پردازش شوند
  {
    const header = backupHeaderLine();
    const good1 = backupRecordLine('customer', { id: 1 });
    const broken = '{این یک JSON خراب است\n';
    const good2 = backupRecordLine('customer', { id: 2 });
    const metaLine = backupRecordLine('meta', { ok: true });
    const text = header + good1 + broken + good2 + metaLine;

    const custBatches = [];
    let threw = false, meta;
    try {
      meta = await applyNdjsonBackup(text, {
        batchSize: 10,
        onCustomerBatch: async b => custBatches.push(b),
        onInvoiceBatch: async () => {},
      });
    } catch (e) { threw = true; }
    assert(threw === false, 'خط خراب وسط فایل نباید کل بازیابی را بترکاند');
    assert(custBatches.length === 1 && custBatches[0].length === 2, 'دو رکورد سالم (قبل/بعد از خط خراب) باید هر دو پردازش شوند');
    assert(meta && meta.ok === true, 'خط meta بعد از خط خراب هم باید درست خوانده شود');
  }

  // 8) applyNdjsonBackup روی فایل خالی از رکورد (فقط هدر) — نباید batch جعلی بسازد
  {
    const text = backupHeaderLine();
    let custCalled = false, invCalled = false;
    await applyNdjsonBackup(text, {
      onCustomerBatch: async () => { custCalled = true; },
      onInvoiceBatch: async () => { invCalled = true; },
    });
    assert(custCalled === false && invCalled === false, 'بدون هیچ رکوردی نباید هیچ batch callback صدا زده شود');
  }

  console.log(`\n${pass} پاس، ${fail} خطا`);
  if (fail > 0) process.exit(1);
}

run();
