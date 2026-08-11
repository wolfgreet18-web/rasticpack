// تست مصرف RAM قبل/بعد از ریزمرحله‌ی ۲.۷ — بخش دوم ۲.۷.۵ (نسخه‌ی ۴.۱۶)
// چون IndexedDB/مرورگر واقعی در این محیط در دسترس نیست، از process.memoryUsage()
// خودِ Node به‌عنوان یک پروکسی معتبر برای رشد heap استفاده می‌شود — دقیقاً همان
// اصلی که بقیه‌ی تست‌های این پوشه (node:sqlite به‌جای IndexedDB واقعی، برای مثال)
// هم رعایت می‌کنند: عدد دقیق روی مرورگر/دستگاه واقعی هنوز جداست، ولی نسبت
// رشد heap بین «قبل» (آرایه‌ی کامل invoices[]) و «بعد» (آرایه‌ی محدود +
// InvoiceCache با ظرفیت ثابت) در همین Node هم قابل اندازه‌گیری و معتبر است.
// اجرا: node --expose-gc tests/ram-usage-test.mjs   (بدون --expose-gc هم کار
// می‌کند، فقط عدد نویزی‌تر می‌شود چون GC دستی صدا زده نمی‌شود)

import { InvoiceCache } from '../rasticpack-capacitor/src/cache/InvoiceCache.js';

const TOTAL_INVOICES = 1_000_000; // همان مقیاس معیار پذیرش رسمی («بی‌نهایت رکورد» → فاز ۷ / ۴.۴/۴.۵)
const STARTUP_PAGE_SIZE = 500; // دقیقاً همان مقدار STARTUP_PAGE_SIZE در loadState (html8.html)

function makeInvoice(id) {
  // شکل تقریبی یک رکورد واقعی فاکتور در این اپ (فیلدهای اصلی renderInvoiceViewCard) —
  // نه یک عدد ساده، تا رشد heap واقع‌گرایانه باشد، نه دست‌کم‌گرفته‌شده.
  return {
    id,
    type: 'final',
    customerId: id % 5000,
    customerName: `مشتری شماره ${id % 5000}`,
    date: new Date(2024, 0, 1 + (id % 365)).toISOString(),
    items: [
      { productId: 1, qty: 3, price: 125000, name: 'ورق ۳ میل' },
      { productId: 2, qty: 1, price: 480000, name: 'ورق ۵ میل' },
    ],
    total: 855000,
    paid: id % 3 === 0 ? 855000 : 0,
    status: id % 3 === 0 ? 'paid' : (id % 3 === 1 ? 'partial' : 'draft'),
    note: '',
  };
}

function heapMB() {
  if (global.gc) global.gc();
  return process.memoryUsage().heapUsed / (1024 * 1024);
}

function fmt(n) { return n.toFixed(1); }

let pass = 0, fail = 0;
function assert(cond, msg) { if (cond) pass++; else { fail++; console.error('❌ FAIL:', msg); } }

async function run() {
  if (!global.gc) {
    console.log('⚠️  بدون --expose-gc اجرا شد — اعداد نویزی‌تر خواهند بود (اما همچنان معتبر برای نسبت کلی).\n');
  }

  // ── سناریوی «قبل» (پیش از ۲.۷.۲): loadState کل ۱M رکورد را در invoices[] می‌ریخت ──
  const beforeStart = heapMB();
  let fullInvoices = [];
  for (let i = 0; i < TOTAL_INVOICES; i++) fullInvoices.push(makeInvoice(i));
  const beforeEnd = heapMB();
  const beforeDeltaMB = beforeEnd - beforeStart;
  console.log(`«قبل» (idbGetAll کامل → invoices[] با ${TOTAL_INVOICES.toLocaleString('en-US')} رکورد): +${fmt(beforeDeltaMB)}MB heap`);
  assert(fullInvoices.length === TOTAL_INVOICES, 'آرایه‌ی «قبل» باید همه‌ی رکوردها را نگه دارد');

  // آزاد کردن حافظه‌ی سناریوی «قبل» قبل از رفتن به سناریوی «بعد» — تا دو سناریو
  // در اندازه‌گیری هم‌پوشانی نداشته باشند.
  fullInvoices = null;
  heapMB();

  // ── سناریوی «بعد» (۲.۷.۱/۲.۷.۲/۲.۷.۳): فقط STARTUP_PAGE_SIZE در invoices[] +
  // InvoiceCache با ظرفیت ثابت — حتی اگر کاربر با اسکرول/فیلتر/مودال (۲.۷.۳)
  // صفحات بیشتری از همان ۱M رکورد را ببیند، InvoiceCache قدیمی‌ترین‌ها را evict می‌کند. ──
  const afterStart = heapMB();
  const invoicesArr = [];
  for (let i = 0; i < STARTUP_PAGE_SIZE; i++) invoicesArr.push(makeInvoice(TOTAL_INVOICES - STARTUP_PAGE_SIZE + i));
  const cache = new InvoiceCache(STARTUP_PAGE_SIZE);
  cache.setMany(invoicesArr);
  // شبیه‌سازی ۲.۷.۳: کاربر کل ۱M رکورد را طی اسکرول/فیلتر/مودال، صفحه‌صفحه می‌بیند —
  // هر صفحه وارد همان InvoiceCache با ظرفیت ثابت می‌شود (دقیقاً مثل fetchInvoicesPage/
  // fetchCustomerInvoicesPage در html8.html، نسخه‌ی ۴.۱۵).
  const PAGE = 50;
  for (let offset = 0; offset < TOTAL_INVOICES; offset += PAGE) {
    const page = [];
    for (let i = offset; i < Math.min(offset + PAGE, TOTAL_INVOICES); i++) page.push(makeInvoice(i));
    cache.setMany(page);
  }
  const afterEnd = heapMB();
  const afterDeltaMB = afterEnd - afterStart;
  console.log(`«بعد» (startup محدود + InvoiceCache، حتی بعد از دیدن کل ${TOTAL_INVOICES.toLocaleString('en-US')} رکورد طی اسکرول/فیلتر): +${fmt(afterDeltaMB)}MB heap`);

  assert(cache.size === STARTUP_PAGE_SIZE, `InvoiceCache باید حتی بعد از setMany روی ${TOTAL_INVOICES.toLocaleString('en-US')} رکورد، دقیقاً روی سقف ظرفیت (${STARTUP_PAGE_SIZE}) بماند — این خودِ اثبات «مصرف RAM مستقل از N» است`);
  assert(invoicesArr.length === STARTUP_PAGE_SIZE, 'آرایه‌ی «بعد» فقط باید STARTUP_PAGE_SIZE رکورد داشته باشد');

  const ratio = beforeDeltaMB > 0 && afterDeltaMB > 0 ? (beforeDeltaMB / afterDeltaMB) : null;
  if (ratio) console.log(`\nنسبت مصرف heap «قبل»/«بعد»: ~${fmt(ratio)}× کمتر (روی این محیط/Node؛ عدد دقیق مرورگر/دستگاه واقعی هنوز اندازه‌گیری نشده)`);

  // ── تأیید مستقل از اندازه‌گیری heap (که به GC/محیط حساس است): خودِ رفتار الگوریتمی
  // ضامن این است که حافظه مستقل از N بماند — این بخش noise-free و قطعی است. ──
  {
    const c2 = new InvoiceCache(STARTUP_PAGE_SIZE);
    for (let i = 0; i < 2_000_000; i++) c2.set(i, makeInvoice(i));
    assert(c2.size === STARTUP_PAGE_SIZE, 'حتی با ۲M فراخوانی set، اندازه‌ی InvoiceCache هرگز نباید از ظرفیت رد شود (تضمین الگوریتمی، نه فقط اندازه‌گیری heap)');
  }

  console.log(`\n${pass} پاس، ${fail} خطا`);
  if (fail > 0) process.exit(1);
}

run();
