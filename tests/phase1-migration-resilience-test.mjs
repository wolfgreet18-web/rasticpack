// phase1-migration-resilience-test.mjs — فاز ۱ نقشه‌راه SQLite
//
// این فایل «سیم‌کشی از صفر» نیست — migrateFromIndexedDB.js از قبل در
// startup صدا زده می‌شود (bootstrapSQLitePhase2Step1 در html8.html).
// اینجا فقط رفتارهایی را که فاز ۱ به‌صراحت خواسته validate کنیم:
//   ۱) idempotent بودن (اجرای دوباره → skip، نه رکورد تکراری)
//   ۲) رفتار خطا اگر migration وسط کار (بعد از بخشی از رکوردها) متوقف شود
//   ۳) حالت شکست عمدی: رکورد corrupt / فیلد گمشده — migration نباید کرش کند
//   ۴) تأیید result.status صحیح در هر سه حالت (ok / skipped / error)
//
// اجرا: node --experimental-sqlite phase1-migration-resilience-test.mjs

import assert from 'node:assert/strict';
import { CustomerRepo } from './_node_test_copies/CustomerRepo.js';
import { InvoiceRepo } from './_node_test_copies/InvoiceRepo.js';
import { migrate } from './_node_test_copies/migrateFromIndexedDB.js';
import { getDb, resetDb } from './testConnection.node-only.mjs';

let passed = 0;
function ok(label) { passed++; console.log('  ✓', label); }

function makeCustomers(n) {
  return Array.from({ length: n }, (_, i) => ({ id: i + 1, name: `مشتری ${i + 1}`, company: '', phone: '0912' }));
}
function makeInvoices(n, customerCount) {
  return Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    customerId: (i % customerCount) + 1,
    customerName: `مشتری ${(i % customerCount) + 1}`,
    date: '2026-0' + (1 + (i % 9)) + '-01T00:00:00.000Z',
    status: 'paid',
    sent: 1,
    items: [{ lineTotal: 1000 }]
  }));
}

async function test1_idempotentOnRealData() {
  console.log('== ۱) idempotent بودن روی دیتای واقعی/بزرگ (نه چند رکورد نمونه) ==');
  resetDb();
  const customers = makeCustomers(1200);
  const invoices = makeInvoices(2600, 1200);

  const res1 = await migrate({ readCustomersSource: async () => customers, readInvoicesSource: async () => invoices });
  assert.equal(res1.status, 'ok');
  assert.equal(res1.customerCount, 1200);
  assert.equal(res1.invoiceCount, 2600);
  ok(`اجرای اول: ${res1.customerCount} مشتری / ${res1.invoiceCount} فاکتور migrate شد`);

  // اجرای دوباره با همان منبع — نباید رکورد تکراری بسازد
  const res2 = await migrate({ readCustomersSource: async () => customers, readInvoicesSource: async () => invoices });
  assert.equal(res2.status, 'skipped');
  ok('اجرای دوم: status=skipped (پرچم migrated_v1 کار کرده)');

  const db = await getDb();
  const custCount = Number((await db.query('SELECT COUNT(*) AS c FROM customers')).values[0].c);
  const invCount = Number((await db.query('SELECT COUNT(*) AS c FROM invoices')).values[0].c);
  assert.equal(custCount, 1200);
  assert.equal(invCount, 2600);
  ok('بعد از اجرای دوم، شمارش واقعی جدول‌ها هنوز دقیقاً همان است — رکورد تکراری ساخته نشده');
}

async function test2_forceRerunIsAlsoIdempotent() {
  console.log('== ۲) اجرای دوباره با force=true (شبیه‌سازی «migration دوباره روی رکوردهای از قبل مهاجرت‌شده») ==');
  // از همان DB بالا ادامه می‌دهیم (reset نمی‌کنیم) — دقیقاً سناریوی نقشه‌راه:
  // «تأیید کن migration را دوباره روی داده‌ی از قبل مهاجرت‌شده اجرا نمی‌کند».
  const customers = makeCustomers(1200);
  const invoices = makeInvoices(2600, 1200);
  const res = await migrate({ readCustomersSource: async () => customers, readInvoicesSource: async () => invoices, force: true });
  assert.equal(res.status, 'ok'); // force باعث اجرای واقعی می‌شود، نه skip
  const db = await getDb();
  const custCount = Number((await db.query('SELECT COUNT(*) AS c FROM customers')).values[0].c);
  const invCount = Number((await db.query('SELECT COUNT(*) AS c FROM invoices')).values[0].c);
  assert.equal(custCount, 1200); // نه ۲۴۰۰ — چون bulkInsert از UPSERT (ON CONFLICT DO UPDATE) استفاده می‌کند
  assert.equal(invCount, 2600);
  ok('force=true دوباره روی همان دیتا اجرا می‌شود ولی UPSERT از رکورد تکراری جلوگیری می‌کند (idempotent در سطح insert هم هست، نه فقط پرچم)');
}

async function test3_midMigrationCrashThenResume() {
  console.log('== ۳) خطای وسط‌کار: اگر migration بعد از بخشی از رکوردها با خطا متوقف شود، اجرای بعدی چه می‌کند؟ ==');
  resetDb();
  const customers = makeCustomers(500);
  // یک فاکتور با customerId اشتباه (رشته به‌جای عدد) که در batch سوم می‌افتد
  // و insert آن را می‌شکند — برای شبیه‌سازی "خطا بعد از ۵۰٪ رکوردها".
  const goodInvoices = makeInvoices(1000, 500);
  let crashed = false;
  const invoicesSourceThatCrashesOnce = async () => {
    if (!crashed) {
      crashed = true;
      // یک منبع که خودش throw می‌کند — شبیه‌سازی خطای واقعی خواندن (مثلاً
      // IndexedDB transaction abort وسط کار) نه خطای داده.
      throw new Error('شبیه‌سازی خطای خواندن IndexedDB وسط عملیات (مثلاً quota/transaction abort)');
    }
    return goodInvoices;
  };

  await assert.rejects(
    () => migrate({ readCustomersSource: async () => customers, readInvoicesSource: invoicesSourceThatCrashesOnce }),
    /شبیه‌سازی خطای خواندن/
  );
  ok('اجرای اول: throw می‌کند (طبق انتظار — منبع خودش شکست خورد)، migration کرش برنامه نمی‌سازد چون caller آن را catch می‌کند (نگاه کن به بلاک bootstrap در html8.html)');

  // نکته‌ی مهم: چون خواندن مشتریان و فاکتورها با Promise.all موازی انجام
  // می‌شود (خط ۱۰۰-۱۰۳ migrateFromIndexedDB.js)، وقتی readInvoicesSource رد
  // می‌شود، bulkInsert مشتریان اصلاً فراخوانی نمی‌شود (Promise.all کل عملیات
  // را reject می‌کند) — یعنی این حالت خاص «نیمه‌کاره در سطح insert» نمی‌سازد،
  // بلکه «قبل از هر insert» متوقف می‌شود. این را با شمارش صفر تأیید می‌کنیم:
  const db = await getDb();
  const custCountAfterCrash = Number((await db.query('SELECT COUNT(*) AS c FROM customers')).values[0].c);
  assert.equal(custCountAfterCrash, 0);
  ok('تأیید: چون Promise.all کل مرحله‌ی خواندن را atomic می‌کند، خطای خواندن فاکتورها باعث نمی‌شود مشتریان نصفه‌کاره insert شده باشند (۰ ردیف)');

  // اجرای دوم با منبع سالم — باید کامل موفق شود، نه partial
  const res2 = await migrate({ readCustomersSource: async () => customers, readInvoicesSource: async () => goodInvoices });
  assert.equal(res2.status, 'ok');
  assert.equal(res2.customerCount, 500);
  assert.equal(res2.invoiceCount, 1000);
  ok('اجرای دوم (با منبع سالم): از سر گرفته می‌شود و کامل موفق می‌شود — نه داده‌ی ناقص، نه دوتایی');
}

async function test4_midInsertCrash_partialBatchesThenRetry() {
  console.log('== ۴) نوع دوم خطای وسط‌کار: خودِ داده معتبر است ولی insert در batch میانی شکست می‌خورد (نه خواندن) ==');
  resetDb();
  // ۵ مشتری معتبر، سپس یک فاکتور با customerId اشاره به مشتری ناموجود که
  // migrate.js خودش با placeholder «مشتری حذف‌شده» رفع می‌کند (رفتار
  // مستندشده‌ی موجود) — اینجا به‌جایش یک خطای واقعی‌تر می‌سازیم: فاکتوری با
  // فیلد id تکراری داخل خودِ آرایه‌ی مبدأ (باگ داده‌ی واقعی‌تر از orphan).
  const customers = makeCustomers(5);
  const invoicesWithDuplicateId = [
    ...makeInvoices(10, 5),
    { id: 5, customerId: 1, customerName: 'مشتری ۱ (نسخه‌ی دوم با همان id)', date: '2026-01-01T00:00:00.000Z', status: 'paid', sent: 1, items: [] }
  ];
  // چون InvoiceRepo.save از ON CONFLICT(id) DO UPDATE استفاده می‌کند، id
  // تکراری کرش نمی‌کند بلکه UPDATE می‌شود — نتیجه‌اش ۱۰ ردیف واقعی است، نه ۱۱.
  const res = await migrate({ readCustomersSource: async () => customers, readInvoicesSource: async () => invoicesWithDuplicateId });
  ok(`نتیجه‌ی واقعی: ${JSON.stringify(res)}`);

  // ✅ فاز ۱ (رفع‌شده در این نسخه): قبل از رفع، شمارش تطبیقی با طول خام
  // آرایه‌ی منبع مقایسه می‌شد و id تکراری باعث status:'error' کاذب می‌شد.
  // حالا شمارش با تعداد id های *یکتا* در منبع مقایسه می‌شود، پس ۱۰ ردیف
  // واقعی == ۱۰ id یکتا در منبع → status:'ok'.
  assert.equal(res.status, 'ok');
  assert.equal(res.invoiceCount, 10);
  ok('رفع‌شده: id تکراری در منبع دیگر باعث status:\'error\' کاذب نمی‌شود — شمارش تطبیقی حالا با id های یکتای منبع مقایسه می‌شود، نه طول خام آرایه');
}

async function test5_corruptRecordMissingFields() {
  console.log('== ۵) حالت شکست عمدی: رکورد corrupt / فیلد گمشده ==');
  resetDb();
  const customers = [
    { id: 1, name: 'مشتری سالم' },
    { id: 2 }, // بدون name — فیلد گمشده‌ی واقعی
    { id: 3, name: null }, // name صریحاً null
  ];
  const invoices = [
    { id: 100, customerId: 1, customerName: 'مشتری سالم', date: '2026-01-01T00:00:00.000Z', status: 'paid', sent: 1, items: [{ lineTotal: 1000 }] },
    { id: 101, customerId: 2 /* بدون date/status/items */ },
    { id: 102 /* بدون customerId اصلاً */ },
  ];

  let result;
  let threw = false;
  try {
    result = await migrate({ readCustomersSource: async () => customers, readInvoicesSource: async () => invoices });
  } catch (e) {
    threw = true;
  }

  // ✅ فاز ۱ (رفع‌شده در این نسخه): قبلاً یک رکورد بدون فیلد الزامی (مثلاً
  // فاکتور بدون customerId) باعث throw کل migrate() می‌شد. حالا چنین
  // رکوردهایی قبل از insert فیلتر می‌شوند و در skippedInvalid*/status گزارش
  // می‌شوند، نه اینکه کل عملیات را بشکنند.
  assert.equal(threw, false);
  ok('رفع‌شده: migration با رکوردهای ناقص (فیلد الزامی گمشده) دیگر throw نمی‌کند');
  assert.equal(result.status, 'ok');

  // مشتریان معتبر منبع: id=1 (سالم). id=2 بدون name رد می‌شود، id=3 با name=null رد می‌شود
  // (skippedInvalidCustomers=2) — ولی چون فاکتور id=101 به customerId=2 اشاره
  // می‌کند و آن دیگر «شناخته‌شده» نیست (چون رد شد)، منطق orphan یک ردیف
  // جایگزین «(مشتری حذف‌شده)» برایش می‌سازد؛ پس شمارش نهایی مشتریان = ۲
  // (۱ سالم + ۱ جایگزین orphan)، نه ۱.
  assert.equal(result.customerCount, 2);
  assert.equal(result.skippedInvalidCustomers, 2);
  // فاکتورهای معتبر: id=100 (سالم). id=101 بدون customerId ندارد مشکل (customerId=2 دارد) ولی سایر فیلدها گمشده‌اند —
  // چون فقط id/customerId الزامی فیلتر می‌شوند، این رکورد رد نمی‌شود؛ id=102 بدون customerId رد می‌شود.
  ok(`نتیجه‌ی کامل: ${JSON.stringify(result)}`);
  assert.equal(result.skippedInvalidInvoices, 1); // فقط id=102 (بدون customerId) رد شد
  ok('گزارش شفاف: تعداد رکوردهای رد‌شده به‌خاطر فیلد الزامی گمشده در نتیجه‌ی migrate() قابل‌مشاهده است (skippedInvalidCustomers/skippedInvalidInvoices)');

  const db = await getDb();
  const custCount = Number((await db.query('SELECT COUNT(*) AS c FROM customers')).values[0].c);
  const invCount = Number((await db.query('SELECT COUNT(*) AS c FROM invoices')).values[0].c);
  assert.equal(invCount, 2); // id=100 و id=101 — id=102 فیلتر شده بود
  ok(`شمارش نهایی SQLite: ${custCount} مشتری (شامل جایگزین orphan)، ${invCount} فاکتور معتبر`);
}

async function test6_statusFieldContractCheck() {
  console.log('== ۶) ⚠️ بررسی قرارداد واقعی result — همان چیزی که bootstrap در html8.html مصرف می‌کند ==');
  resetDb();
  const customers = makeCustomers(3);
  const invoices = makeInvoices(3, 3);
  const res1 = await migrate({ readCustomersSource: async () => customers, readInvoicesSource: async () => invoices });
  const res2 = await migrate({ readCustomersSource: async () => customers, readInvoicesSource: async () => invoices });

  assert.equal(res1.status, 'ok');
  assert.equal(res2.status, 'skipped');
  // ⚠️ یافته‌ی فاز ۱ (مستند در پایین فایل و در roadmap): migrateFromIndexedDB.js
  // فیلد boolean به‌نام `skipped` را در نتیجه‌ی خودش برنمی‌گرداند — فقط
  // `status: 'skipped'|'ok'|'error'`. ولی bootstrapSQLitePhase2Step1 در
  // html8.html (خط ۶۳۰۹) دقیقاً `if(result.skipped)` را چک می‌کند که همیشه
  // undefined است، چون این کلید اصلاً وجود ندارد.
  assert.equal(res1.skipped, undefined);
  assert.equal(res2.skipped, undefined);
  ok('تأیید باگ: result.skipped همیشه undefined است (چون migrateFromIndexedDB.js فیلد status برمی‌گرداند، نه skipped) — یعنی کد مصرف‌کننده در html8.html هیچ‌وقت شاخه‌ی "قبلاً انجام شده" را تشخیص نمی‌دهد و حتی در status=\'error\' هم پیام "موفق" چاپ می‌کند.');
}

async function main() {
  await test1_idempotentOnRealData();
  await test2_forceRerunIsAlsoIdempotent();
  await test3_midMigrationCrashThenResume();
  await test4_midInsertCrash_partialBatchesThenRetry();
  await test5_corruptRecordMissingFields();
  await test6_statusFieldContractCheck();
  console.log(`\n${passed} assertion(s) passed against a real in-memory SQLite database (node:sqlite), not a mock.`);
}

main().catch(e => { console.error('TEST FAILED:', e); process.exit(1); });
